package com.civinb.danmuji.data.bili.video

import com.civinb.danmuji.data.bili.proto.ProtoMessage
import com.civinb.danmuji.model.DanmakuIds
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind

/**
 * 解码视频弹幕分段接口（seg.so）返回的 protobuf：DmSegMobileReply { repeated DanmakuElem elems = 1; }
 * DanmakuElem 字段号（PiliPlus 的 dm/v1.pb.dart 与 BiliNest server.mjs 一致）：
 *   id=1 progress=2(毫秒) mode=3 fontsize=4 color=5 midHash=6 content=7 ctime=8 weight=9 pool=11 idStr=12
 * mode 1~3 滚动、4 底部、5 顶部、6 逆向；7 高级弹幕、8 代码弹幕、9 BAS 弹幕的 content 是脚本/JSON，不适合列表显示，丢弃。
 */
object DmSegDecoder {

    fun decode(bytes: ByteArray): List<DanmakuItem> {
        if (bytes.isEmpty()) return emptyList()
        val reply = ProtoMessage.parse(bytes)
        return reply.messages(1).mapNotNull { e ->
            val mode = e.int(3, 1)
            val content = e.string(7)
            if (mode >= 7 || content.isBlank()) {
                null
            } else {
                DanmakuItem(
                    id = DanmakuIds.next(),
                    kind = DanmakuKind.DANMAKU,
                    text = content,
                    userHash = e.string(6).ifEmpty { null },
                    color = (e.long(5, 0xFFFFFF).toInt()) and 0xFFFFFF,
                    timestampMs = e.long(8) * 1000,
                    progressMs = e.long(2).coerceAtLeast(0),
                )
            }
        }
    }
}
