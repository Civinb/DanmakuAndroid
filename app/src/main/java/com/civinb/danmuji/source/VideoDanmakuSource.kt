package com.civinb.danmuji.source

import com.civinb.danmuji.data.bili.BiliClient
import com.civinb.danmuji.data.bili.link.LinkTarget
import com.civinb.danmuji.data.bili.video.VideoApi
import com.civinb.danmuji.data.bili.video.VideoInfo
import com.civinb.danmuji.data.bili.video.VideoPage
import com.civinb.danmuji.model.DanmakuIds
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.util.DebugLog
import com.civinb.danmuji.video.AutoStatus
import com.civinb.danmuji.video.PageDetector
import com.civinb.danmuji.video.SyncMode
import com.civinb.danmuji.video.SyncState
import com.civinb.danmuji.video.TimelinePlayer
import com.civinb.danmuji.video.VideoSync
import com.civinb.danmuji.video.VideoTimeline
import com.civinb.danmuji.video.VideoUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 视频弹幕来源：取视频信息 → 按 6 分钟一段下载当前分P的全部弹幕 → 按播放进度（自动 / 手动）逐条放出。
 * 自动模式下若检测到 B 站 App 切换了分P（见 [PageDetector]），自动换成对应分P的弹幕。
 */
class VideoDanmakuSource(
    private val target: LinkTarget.Video,
    private val bili: BiliClient,
    private val sync: VideoSync,
) : DanmakuSource {

    private var lastSystemText = ""

    override fun events(): Flow<SourceEvent> = channelFlow {
        send(SourceEvent.Status("正在获取视频信息…"))
        DebugLog.log(TAG, if (bili.login.isLoggedIn()) "已登录" else "未登录（部分视频未登录时只返回部分弹幕）")
        val info: VideoInfo = try {
            bili.videoApi.getVideoInfo(target.bvid, target.aid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.log(TAG, "视频信息获取失败：${e.message}")
            send(SourceEvent.Status("视频信息获取失败"))
            system("视频信息获取失败：${e.message}")
            return@channelFlow
        }
        val pageNo = (target.page ?: 1).coerceIn(1, info.pages.size)
        var page: VideoPage = info.pages.firstOrNull { it.page == pageNo } ?: info.pages[pageNo - 1]
        if (info.pages.size > 1) {
            DebugLog.log(TAG, "分P：" + info.pages.joinToString { "P${it.page}「${it.part}」${it.durationSec}s" })
        }
        var hint: String? = if (info.pages.size > 1) {
            "该视频有 ${info.pages.size} 个分P。自动同步模式下，在 B 站 App 里切换分P会自动跟随（依据会话标题或时长判断）。"
        } else {
            null
        }
        while (true) {
            val (next, message) = playPage(info, page, hint)
            page = next
            hint = message
        }
    }

    /** 播放一个分P，直到检测到 B 站切换了分P，返回新的分P及要显示的提示 */
    private suspend fun ProducerScope<SourceEvent>.playPage(
        info: VideoInfo,
        page: VideoPage,
        hint: String?,
    ): Pair<VideoPage, String> {
        val durationMs = page.durationSec * 1000
        val videoName = info.title.ifEmpty { page.part.ifEmpty { info.bvid.ifEmpty { "av${info.aid}" } } }
        val label = if (info.pages.size > 1) "P${page.page} ${page.part}" else videoName
        DebugLog.log(TAG, "视频 ${info.bvid} aid=${info.aid} P${page.page} cid=${page.cid} 时长 ${page.durationSec}s")

        sync.startVideo(durationMs)
        val timeline = VideoTimeline()
        val player = TimelinePlayer(timeline)
        val detector = PageDetector(info.pages)
        val total = max(1, ((durationMs + VideoApi.SEGMENT_MS - 1) / VideoApi.SEGMENT_MS).toInt())
        var loaded = 0

        // 后台下载分段：先下当前位置所在的段
        val loader = launch {
            val start = (sync.state().positionMs / VideoApi.SEGMENT_MS).toInt().coerceIn(0, total - 1)
            val order = (listOf(start, start + 1) + (0 until total)).filter { it in 0 until total }.distinct()
            var failed = 0
            for (idx in order) {
                var list: List<DanmakuItem>? = null
                for (attempt in 1..3) {
                    try {
                        list = bili.videoApi.getSegment(page.cid, idx + 1)
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DebugLog.log(TAG, "第 ${idx + 1} 段第 $attempt 次失败：${e.message}")
                        delay(1_000L * attempt)
                    }
                }
                if (list == null) {
                    failed++
                } else {
                    timeline.addSegment(list)
                    player.onSegmentLoaded(idx * VideoApi.SEGMENT_MS, (idx + 1) * VideoApi.SEGMENT_MS)
                    DebugLog.log(TAG, "第 ${idx + 1}/$total 段：${list.size} 条")
                }
                loaded++
                delay(SEGMENT_INTERVAL_MS)
            }
            system(
                if (failed == 0) {
                    "弹幕加载完成，共 ${timeline.size} 条"
                } else {
                    "弹幕加载完成，共 ${timeline.size} 条（$failed 段加载失败）"
                },
            )
        }

        var pendingHint = hint
        var lastStatus = ""
        var lastSession = ""
        var ticks = 0
        try {
            while (true) {
                val st = sync.state()

                // 记录媒体会话报告的标题/时长（变化时），便于核对分P判断依据
                if (st.mode == SyncMode.AUTO && st.autoStatus == AutoStatus.OK) {
                    val session = "标题「${st.sessionTitle.orEmpty()}」时长 ${st.sessionDurationMs / 1000}s"
                    if (session != lastSession) {
                        lastSession = session
                        DebugLog.log(TAG, "媒体会话：$session")
                    }
                    // 每秒检查一次是否换了分P
                    if (info.pages.size > 1 && ticks % 4 == 0) {
                        val detected = detector.observe(st.sessionTitle, st.sessionDurationMs)
                        if (detected != null && detected.cid != page.cid) {
                            DebugLog.log(TAG, "检测到 B 站切换到 P${detected.page}")
                            loader.cancel()
                            return detected to "检测到 B 站切换到 P${detected.page}「${detected.part}」，已切换弹幕"
                        }
                    }
                }
                ticks++

                when (val tick = player.tick(st.positionMs)) {
                    is TimelinePlayer.Tick.Reset -> {
                        send(SourceEvent.Clear)
                        tick.items.forEach { send(SourceEvent.Item(it)) }
                        pendingHint?.let {
                            pendingHint = null
                            lastSystemText = ""
                            system(it)
                        }
                    }
                    is TimelinePlayer.Tick.Emit -> tick.items.forEach { send(SourceEvent.Item(it)) }
                    TimelinePlayer.Tick.Idle -> Unit
                }
                val mismatch = st.mode == SyncMode.AUTO && st.autoStatus == AutoStatus.OK &&
                    titleMismatch(st.sessionTitle, info)
                send(
                    SourceEvent.VideoState(
                        VideoUiState(
                            positionMs = st.positionMs,
                            durationMs = durationMs,
                            playing = st.playing,
                            mode = st.mode,
                            autoStatus = st.autoStatus,
                            offsetMs = st.offsetMs,
                            titleMismatch = mismatch,
                            sessionTitle = st.sessionTitle,
                            loadedSegments = loaded,
                            totalSegments = total,
                            danmakuCount = timeline.size,
                        ),
                    ),
                )
                val status = "$label · ${statusText(st)}"
                if (status != lastStatus) {
                    lastStatus = status
                    send(SourceEvent.Status(status))
                }
                delay(TICK_MS)
            }
        } finally {
            loader.cancel()
        }
    }

    private fun statusText(st: SyncState): String = when (st.mode) {
        SyncMode.MANUAL -> "手动同步"
        SyncMode.AUTO -> when (st.autoStatus) {
            AutoStatus.OK -> "自动同步"
            AutoStatus.NO_PERMISSION -> "自动同步需要通知使用权"
            AutoStatus.NO_SESSION, null -> "未检测到 B 站播放"
        }
    }

    /**
     * B 站媒体会话里的标题和本视频（任一分P）是否对不上，用来提醒“你在 B 站看的可能不是这个视频”。
     * 会话标题的含义没有实测数据，所以视频标题或任一分P标题“互相包含”即视为一致。
     */
    private fun titleMismatch(sessionTitle: String?, info: VideoInfo): Boolean {
        val t = sessionTitle?.trim().orEmpty()
        if (t.isEmpty()) return false
        val candidates = (listOf(info.title) + info.pages.map { it.part }).map { it.trim() }.filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return false
        return candidates.none { c -> t.contains(c) || c.contains(t) }
    }

    private suspend fun ProducerScope<SourceEvent>.system(text: String) {
        if (text == lastSystemText) return
        lastSystemText = text
        send(SourceEvent.Item(DanmakuItem(id = DanmakuIds.next(), kind = DanmakuKind.SYSTEM, text = text)))
    }

    private companion object {
        const val TAG = "Video"
        const val TICK_MS = 250L
        const val SEGMENT_INTERVAL_MS = 300L
    }
}
