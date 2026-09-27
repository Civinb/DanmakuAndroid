package com.civinb.danmuji.data.bili.live

import com.civinb.danmuji.data.bili.proto.ProtoMessage
import com.civinb.danmuji.model.DanmakuIds
import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.model.DanmakuKind
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.Locale

/**
 * 把直播信息流里的业务消息（JSON，含 cmd 字段）转换为统一的 [DanmakuItem]。
 * 只处理本应用需要的 cmd，其余返回空列表。
 *
 * 字段依据（2026-09 查证）：
 *  - blivedm `blivedm/handlers.py`、`blivedm/models/web.py`、`blivedm/models/pb.py`（2026-08-15）
 *  - PiliPlus `lib/pages/live_room/controller.dart`（2026-09）
 *  - bilibili-API-collect 存档 `docs/live/message_stream.md`（2026-01-28）
 */
object LiveCommandParser {

    fun parse(json: String): List<DanmakuItem> {
        val obj = JSONObject(json)
        // cmd 可能带后缀，如 "DANMU_MSG:4:0:2:2:2:0"
        val cmd = obj.optString("cmd").substringBefore(':')
        return when (cmd) {
            "DANMU_MSG", "DANMU_MSG_MIRROR" -> listOfNotNull(parseDanmaku(obj))
            "SUPER_CHAT_MESSAGE" -> listOfNotNull(parseSuperChat(obj.optJSONObject("data")))
            "SEND_GIFT" -> listOfNotNull(parseGift(obj.optJSONObject("data")))
            "SEND_GIFT_V2" -> parseGiftV2(obj.optJSONObject("data"))
            "GUARD_BUY" -> listOfNotNull(parseGuardBuy(obj.optJSONObject("data")))
            "INTERACT_WORD_V2" -> listOfNotNull(parseInteractV2(obj.optJSONObject("data")))
            "LOG_IN_NOTICE" -> listOfNotNull(
                obj.optJSONObject("data")?.str("notice_msg")?.let { system(it) },
            )
            "LIVE" -> listOf(system("直播开始了"))
            "PREPARING" -> listOf(system("主播已下播（准备中）"))
            else -> emptyList()
        }
    }

    // ---------------- 弹幕 ----------------

    private fun parseDanmaku(obj: JSONObject): DanmakuItem? {
        val info = obj.optJSONArray("info") ?: return null
        val text = info.optString(1)
        if (text.isEmpty()) return null
        val meta: JSONArray? = info.optJSONArray(0)
        val userArr: JSONArray? = info.optJSONArray(2)
        val modeInfo: JSONObject? = meta?.optJSONObject(15)
        val userObj: JSONObject? = modeInfo?.optJSONObject("user")

        // blivedm 用 info[2][1]；PiliPlus 用 info[0][15].user.base.name，两处都试
        var name = userArr?.strAt(1)
        if (name.isNullOrEmpty()) name = userObj?.optJSONObject("base")?.str("name")
        var uid = userArr?.optLong(0) ?: 0L
        if (uid == 0L) uid = userObj?.optLong("uid") ?: 0L

        return DanmakuItem(
            id = DanmakuIds.next(),
            kind = DanmakuKind.DANMAKU,
            text = text,
            userName = name,
            userId = uid,
            color = meta?.optInt(3, 0xFFFFFF) ?: 0xFFFFFF,
            timestampMs = meta?.optLong(4)?.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    // ---------------- 醒目留言 ----------------

    private fun parseSuperChat(data: JSONObject?): DanmakuItem? {
        data ?: return null
        val message = data.str("message") ?: return null
        return DanmakuItem(
            id = DanmakuIds.next(),
            kind = DanmakuKind.SUPER_CHAT,
            text = message,
            userName = data.optJSONObject("user_info")?.str("uname"),
            userId = data.optLong("uid"),
            price = data.optInt("price"),
        )
    }

    // ---------------- 礼物 ----------------

    private fun parseGift(data: JSONObject?): DanmakuItem? {
        data ?: return null
        val giftName = data.str("giftName") ?: return null
        return giftItem(
            uid = data.optLong("uid"),
            uname = data.str("uname"),
            action = data.str("action") ?: "赠送",
            giftName = giftName,
            num = data.optInt("num", 1),
            coinType = data.str("coin_type"),
            totalCoin = data.optLong("total_coin"),
        )
    }

    /** 2026-07 起灰度的新礼物消息：data.pb 为 base64 protobuf（字段号见 blivedm models/pb.py）。 */
    private fun parseGiftV2(data: JSONObject?): List<DanmakuItem> {
        val pb = data?.str("pb") ?: return emptyList()
        val msg = ProtoMessage.parse(Base64.getDecoder().decode(pb))
        val uid = msg.long(1)
        val uname = msg.string(2)
        return msg.messages(10).mapNotNull { gift ->
            val giftName = gift.string(2)
            if (giftName.isEmpty()) {
                null
            } else {
                giftItem(
                    uid = uid,
                    uname = uname,
                    action = gift.string(18).ifEmpty { "赠送" },
                    giftName = giftName,
                    num = gift.int(3, 1),
                    coinType = gift.string(8),
                    totalCoin = gift.long(7),
                )
            }
        }
    }

    private fun parseGuardBuy(data: JSONObject?): DanmakuItem? {
        data ?: return null
        val giftName = data.str("gift_name") ?: return null
        return DanmakuItem(
            id = DanmakuIds.next(),
            kind = DanmakuKind.GIFT,
            text = "开通了 $giftName ×${data.optInt("num", 1)}",
            userName = data.str("username"),
            userId = data.optLong("uid"),
        )
    }

    private fun giftItem(
        uid: Long,
        uname: String?,
        action: String,
        giftName: String,
        num: Int,
        coinType: String?,
        totalCoin: Long,
    ): DanmakuItem {
        // 1000 金瓜子 = 1 元（blivedm 注释）；银瓜子礼物不显示金额
        val yuan = if (coinType == "gold" && totalCoin > 0) {
            String.format(Locale.US, "（¥%.1f）", totalCoin / 1000.0)
        } else {
            ""
        }
        return DanmakuItem(
            id = DanmakuIds.next(),
            kind = DanmakuKind.GIFT,
            text = "$action $giftName ×$num$yuan",
            userName = uname,
            userId = uid,
        )
    }

    // ---------------- 进场 / 互动 ----------------

    /** data.pb 为 base64 protobuf：uid=1, uname=2, msg_type=5（1 进入 2 关注 3 分享 4 特别关注 5 互粉 6 点赞）。 */
    private fun parseInteractV2(data: JSONObject?): DanmakuItem? {
        val pb = data?.str("pb") ?: return null
        val msg = ProtoMessage.parse(Base64.getDecoder().decode(pb))
        val text = when (msg.int(5)) {
            1 -> "进入直播间"
            2 -> "关注了主播"
            3 -> "分享了直播间"
            4 -> "特别关注了主播"
            5 -> "与主播互粉了"
            6 -> "为主播点赞了"
            else -> return null
        }
        return DanmakuItem(
            id = DanmakuIds.next(),
            kind = DanmakuKind.ENTER,
            text = text,
            userName = msg.string(2),
            userId = msg.long(1),
        )
    }

    // ---------------- 工具 ----------------

    private fun system(text: String) = DanmakuItem(id = DanmakuIds.next(), kind = DanmakuKind.SYSTEM, text = text)

    /** Android 自带的 org.json 对 JSON null 的 optString 会返回 "null"，这里统一处理。 */
    private fun JSONObject.str(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONArray.strAt(index: Int): String? =
        if (index >= length() || isNull(index)) null else optString(index)
}
