package com.civinb.danmuji.source

import com.civinb.danmuji.data.bili.BiliClient
import com.civinb.danmuji.data.bili.auth.LoginApi
import com.civinb.danmuji.data.bili.link.LinkTarget
import com.civinb.danmuji.model.DanmakuIds
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import com.civinb.danmuji.util.DebugLog
import com.civinb.danmuji.util.NetworkMonitor
import com.civinb.danmuji.video.VideoSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import com.civinb.danmuji.util.tr

/**
 * 根据用户输入（房间号 / 链接 / 分享文字）解析目标，再交给对应的弹幕来源。
 */
class InputDanmakuSource(
    private val input: String,
    private val bili: BiliClient,
    private val network: NetworkMonitor,
    private val videoSync: VideoSync,
) : DanmakuSource {

    override fun events(): Flow<SourceEvent> = flow {
        emit(SourceEvent.Status(tr("正在解析输入…", "Parsing input…")))
        DebugLog.log("Input", tr("输入：${input.take(200)}", "Input: ${input.take(200)}"))
        val target = bili.linkResolver.resolve(input)
        // 开始看弹幕前核对登录是否过期；过期时在弹幕列表里提示
        val loginNotice = if (target is LinkTarget.Live || target is LinkTarget.Video) {
            if (bili.login.verifySession() == LoginApi.SessionCheck.EXPIRED) LOGIN_EXPIRED_NOTICE else null
        } else {
            null
        }
        when (target) {
            is LinkTarget.Live -> {
                loginNotice?.let { emit(system(it)) }
                emitAll(LiveDanmakuSource(target.roomId, bili, network).events())
            }
            // 视频来源开始播放时会清空列表，所以提示交给它在清空之后显示
            is LinkTarget.Video -> emitAll(VideoDanmakuSource(target, bili, videoSync, loginNotice).events())
            is LinkTarget.Invalid -> {
                emit(SourceEvent.Status(target.reason))
                emit(system(target.reason))
            }
            is LinkTarget.NeedsRedirect -> {
                emit(SourceEvent.Status(tr("短链跳转次数过多", "Too many short-link redirects")))
                emit(system(tr("短链跳转次数过多：${target.url}", "Too many short-link redirects: ${target.url}")))
            }
        }
    }

    private companion object {
        val LOGIN_EXPIRED_NOTICE: String get() =
            tr("B 站登录已过期，当前按未登录获取弹幕（视频弹幕可能不完整，直播昵称会打码）。请到 设置 → B 站账号 重新扫码登录。", "Your Bilibili login has expired; danmaku are loaded as a guest (video danmaku may be incomplete, live usernames are masked). Go to Settings → Bilibili account to scan the QR code again.")
    }

    private fun system(text: String) = SourceEvent.Item(
        DanmakuItem(id = DanmakuIds.next(), kind = DanmakuKind.SYSTEM, text = text),
    )
}
