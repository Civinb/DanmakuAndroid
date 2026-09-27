package com.civinb.danmuji.source

import com.civinb.danmuji.data.bili.BiliClient
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
        emit(SourceEvent.Status("正在解析输入…"))
        DebugLog.log("Input", "输入：${input.take(200)}")
        when (val target = bili.linkResolver.resolve(input)) {
            is LinkTarget.Live -> emitAll(LiveDanmakuSource(target.roomId, bili, network).events())
            is LinkTarget.Video -> emitAll(VideoDanmakuSource(target, bili, videoSync).events())
            is LinkTarget.Invalid -> {
                emit(SourceEvent.Status(target.reason))
                emit(system(target.reason))
            }
            is LinkTarget.NeedsRedirect -> {
                emit(SourceEvent.Status("短链跳转次数过多"))
                emit(system("短链跳转次数过多：${target.url}"))
            }
        }
    }

    private fun system(text: String) = SourceEvent.Item(
        DanmakuItem(id = DanmakuIds.next(), kind = DanmakuKind.SYSTEM, text = text),
    )
}
