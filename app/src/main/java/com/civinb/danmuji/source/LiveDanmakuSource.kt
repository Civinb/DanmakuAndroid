package com.civinb.danmuji.source

import com.civinb.danmuji.data.bili.BiliApiException
import com.civinb.danmuji.data.bili.BiliClient
import com.civinb.danmuji.data.bili.BiliHttp
import com.civinb.danmuji.data.bili.live.DanmuConf
import com.civinb.danmuji.data.bili.live.DanmuHost
import com.civinb.danmuji.data.bili.live.LiveCommandParser
import com.civinb.danmuji.data.bili.live.LivePacketCodec
import com.civinb.danmuji.data.bili.live.LivePacketCodec.Packet
import com.civinb.danmuji.data.bili.live.LiveRoomApi
import com.civinb.danmuji.data.bili.live.LiveRoomInfo
import com.civinb.danmuji.model.DanmakuIds
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.util.DebugLog
import com.civinb.danmuji.util.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.brotli.dec.BrotliInputStream
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * B 站直播弹幕来源。
 *
 * 流程（与 blivedm 一致）：
 *   房间信息 → 最近历史弹幕 → buvid3 → getDanmuInfo(WBI) → WebSocket 连接 → 认证包 → 每 30 秒心跳
 * 断线：指数退避重连（1s → 30s），每失败若干次重新获取 token；认证失败立即重新获取 token；
 * 网络切换（WiFi ↔ 流量）：立即断开并用新网络重连。
 */
class LiveDanmakuSource(
    private val roomInput: Long,
    private val bili: BiliClient,
    private val network: NetworkMonitor,
) : DanmakuSource {

    private val api: LiveRoomApi get() = bili.liveApi

    private enum class EndReason { CLOSED, TIMEOUT, AUTH_FAILED, NETWORK_CHANGED }

    private data class SessionResult(val reason: EndReason, val detail: String, val wasConnected: Boolean)

    private sealed interface WsEvent {
        data object Open : WsEvent
        class Binary(val data: ByteArray) : WsEvent
        data class Closed(val reason: String) : WsEvent
        data object NetworkChanged : WsEvent
    }

    private val brotli = LivePacketCodec.BrotliDecoder { input ->
        BrotliInputStream(input.inputStream()).use { it.readBytes() }
    }

    private var lastSystemText = ""
    private var lastSystemTime = 0L

    override fun events(): Flow<SourceEvent> = channelFlow {
        val room = loadRoom() ?: return@channelFlow
        val label = room.title.ifEmpty { "直播间 ${room.roomId}" }
        DebugLog.log(TAG, "房间 ${room.roomId}（短号 ${room.shortId}）主播 ${room.ownerUid} 状态 ${room.liveStatus}")
        if (room.liveStatus != 1) systemItem("主播当前未开播，开播后会自动显示弹幕")

        // 连接前先显示最近的历史弹幕
        try {
            val history = api.getRecentDanmaku(room.roomId)
            if (history.isNotEmpty()) {
                history.forEach { send(SourceEvent.Item(it)) }
                systemItem("—— 以上是最近的历史弹幕 ——")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.log(TAG, "获取历史弹幕失败：${e.message}")
        }

        val buvid = bili.auth.ensureBuvid()
        DebugLog.log(TAG, if (bili.login.isLoggedIn()) "使用已登录身份连接（UID ${bili.login.mid()}）" else "未登录连接（他人昵称会被打码）")
        var conf: DanmuConf? = null
        var attempt = 0
        var everConnected = false
        var backoffMs = INITIAL_BACKOFF_MS

        while (true) {
            if (!network.isAvailable) {
                send(SourceEvent.Status("$label · 等待网络…"))
                network.awaitAvailable()
            }
            if (conf == null) {
                send(SourceEvent.Status("$label · 获取弹幕服务器…"))
                conf = try {
                    api.getDanmuConf(room.roomId).also {
                        DebugLog.log(TAG, "getDanmuInfo 成功，服务器 ${it.hosts.size} 个")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.log(TAG, "getDanmuInfo 失败（${e.message}），降级为默认服务器、不带 token")
                    systemItem("获取弹幕服务器失败（${e.message}），尝试降级连接")
                    LiveRoomApi.FALLBACK
                }
                attempt = 0
            }
            val c = conf!!
            val host = c.hosts[attempt % c.hosts.size]
            send(SourceEvent.Status("$label · 连接中…"))
            DebugLog.log(TAG, "连接 ${host.host}:${host.wssPort}（第 ${attempt + 1} 次）")

            val result = runSession(host, c, room, buvid, label, everConnected)
            attempt++
            if (result.wasConnected) {
                everConnected = true
                backoffMs = INITIAL_BACKOFF_MS
            }
            DebugLog.log(TAG, "连接结束：${result.reason} ${result.detail}")

            when (result.reason) {
                EndReason.NETWORK_CHANGED -> {
                    systemItem("网络已切换，正在重连")
                    backoffMs = INITIAL_BACKOFF_MS
                    continue // 立即重连
                }
                EndReason.AUTH_FAILED -> conf = null
                else -> Unit
            }
            // 与 blivedm 一致：失败次数达到服务器数（至少 3）时重新获取 token；降级状态下每次都重试正式配置
            if (c.degraded || attempt % max(3, c.hosts.size) == 0) conf = null

            val waitMs = backoffMs
            backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            if (result.wasConnected) systemItem("连接断开，${waitMs / 1000} 秒后重连")
            send(SourceEvent.Status("$label · 连接断开，${waitMs / 1000} 秒后重连"))
            // 等待退避时间；期间网络发生变化就提前重连
            val handle = network.state.value.handle
            withTimeoutOrNull(waitMs) {
                network.state.first { it.available && it.handle != handle }
            }
        }
    }

    /** 获取房间信息；网络错误时重试，接口明确报错（如房间不存在）时停止。 */
    private suspend fun ProducerScope<SourceEvent>.loadRoom(): LiveRoomInfo? {
        var backoff = INITIAL_BACKOFF_MS
        while (true) {
            if (!network.isAvailable) {
                send(SourceEvent.Status("等待网络…"))
                network.awaitAvailable()
            }
            send(SourceEvent.Status("正在获取直播间 $roomInput 的信息…"))
            try {
                return api.getRoomInfo(roomInput)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BiliApiException) {
                DebugLog.log(TAG, "房间信息获取失败：${e.message}")
                send(SourceEvent.Status("直播间 $roomInput 获取失败"))
                systemItem("直播间 $roomInput 获取失败：${e.message}")
                return null
            } catch (e: Exception) {
                DebugLog.log(TAG, "房间信息网络错误：${e.message}")
                send(SourceEvent.Status("网络错误，${backoff / 1000} 秒后重试"))
                delay(backoff)
                backoff = min(backoff * 2, MAX_BACKOFF_MS)
            }
        }
    }

    /** 一次 WebSocket 会话，直到断开才返回。 */
    private suspend fun ProducerScope<SourceEvent>.runSession(
        host: DanmuHost,
        conf: DanmuConf,
        room: LiveRoomInfo,
        buvid: String,
        label: String,
        isReconnect: Boolean,
    ): SessionResult {
        val events = Channel<WsEvent>(Channel.UNLIMITED)
        val request = Request.Builder()
            .url("wss://${host.host}:${host.wssPort}/sub")
            .header("User-Agent", BiliHttp.USER_AGENT) // token 与 UA 绑定（blivedm 注释）
            .build()
        val ws = bili.http.wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                events.trySend(WsEvent.Open)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                events.trySend(WsEvent.Binary(bytes.toByteArray()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                events.trySend(WsEvent.Closed("服务器关闭连接（$code $reason）"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                events.trySend(WsEvent.Closed("连接已关闭（$code）"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.trySend(WsEvent.Closed("连接失败：${t.message ?: t.javaClass.simpleName}"))
            }
        })

        val startHandle = network.state.value.handle
        val netJob = launch {
            network.state.first { it.handle != startHandle }
            events.trySend(WsEvent.NetworkChanged)
        }
        var heartbeatJob: Job? = null
        var authed = false
        var parseErrors = 0
        try {
            while (true) {
                val timeout = if (authed) RECEIVE_TIMEOUT_MS else CONNECT_TIMEOUT_MS
                val ev = withTimeoutOrNull(timeout) { events.receive() }
                    ?: return SessionResult(EndReason.TIMEOUT, "${timeout / 1000} 秒没有收到数据", authed)
                when (ev) {
                    WsEvent.Open -> {
                        ws.send(authPacket(room, conf, buvid).toByteString())
                    }
                    is WsEvent.Binary -> {
                        val packets = try {
                            LivePacketCodec.decode(ev.data, brotli)
                        } catch (e: Exception) {
                            DebugLog.log(TAG, "数据包解析失败：${e.message}")
                            emptyList()
                        }
                        for (p in packets) {
                            when (p) {
                                is Packet.AuthReply -> {
                                    val code = try {
                                        JSONObject(p.json).optInt("code", -1)
                                    } catch (e: Exception) {
                                        -1
                                    }
                                    if (code != 0) {
                                        return SessionResult(EndReason.AUTH_FAILED, "认证失败 code=$code", false)
                                    }
                                    authed = true
                                    ws.send(heartbeatPacket())
                                    heartbeatJob = launch {
                                        while (true) {
                                            delay(HEARTBEAT_INTERVAL_MS)
                                            ws.send(heartbeatPacket())
                                        }
                                    }
                                    DebugLog.log(TAG, "认证成功${if (conf.degraded) "（降级模式）" else ""}")
                                    send(SourceEvent.Status("$label · 已连接"))
                                    systemItem(if (isReconnect) "已重新连接" else "已连接弹幕服务器")
                                }
                                is Packet.Command -> {
                                    val items = try {
                                        LiveCommandParser.parse(p.json)
                                    } catch (e: Exception) {
                                        if (parseErrors++ < 5) {
                                            DebugLog.log(TAG, "消息解析失败：${e.message}；${p.json.take(200)}")
                                        }
                                        emptyList()
                                    }
                                    for (item in items) {
                                        if (item.kind == DanmakuKind.SYSTEM) systemItem(item.text) else send(SourceEvent.Item(item))
                                    }
                                }
                                is Packet.HeartbeatReply, is Packet.Unknown -> Unit
                            }
                        }
                    }
                    is WsEvent.Closed -> return SessionResult(EndReason.CLOSED, ev.reason, authed)
                    WsEvent.NetworkChanged -> return SessionResult(EndReason.NETWORK_CHANGED, "", authed)
                }
            }
        } finally {
            netJob.cancel()
            heartbeatJob?.cancel()
            ws.cancel()
            events.close()
        }
    }

    private fun authPacket(room: LiveRoomInfo, conf: DanmuConf, buvid: String): ByteArray {
        val body = JSONObject()
            .put("uid", bili.login.mid()) // 已登录时为自己的 UID（blivedm 同样做法），未登录为 0
            .put("roomid", room.roomId)
            .put("protover", 3) // brotli
            .put("platform", "web")
            .put("type", 2)
            .put("buvid", buvid)
        conf.token?.let { body.put("key", it) }
        return LivePacketCodec.encodeJson(LivePacketCodec.Op.AUTH, body.toString())
    }

    private fun heartbeatPacket(): ByteString =
        LivePacketCodec.encodeJson(LivePacketCodec.Op.HEARTBEAT, "{}").toByteString()

    /** 系统提示，同样内容 60 秒内只显示一次（例如 LIVE 消息会重复下发）。 */
    private suspend fun ProducerScope<SourceEvent>.systemItem(text: String) {
        val now = System.currentTimeMillis()
        if (text == lastSystemText && now - lastSystemTime < 60_000) return
        lastSystemText = text
        lastSystemTime = now
        send(SourceEvent.Item(DanmakuItem(id = DanmakuIds.next(), kind = DanmakuKind.SYSTEM, text = text)))
    }

    private companion object {
        const val TAG = "Live"
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        const val RECEIVE_TIMEOUT_MS = 35_000L // 与 blivedm 一致：心跳间隔 + 5 秒
        const val CONNECT_TIMEOUT_MS = 15_000L
    }
}
