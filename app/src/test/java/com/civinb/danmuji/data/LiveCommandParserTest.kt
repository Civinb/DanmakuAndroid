package com.civinb.danmuji.data

import com.civinb.danmuji.data.bili.live.LiveCommandParser
import com.civinb.danmuji.model.DanmakuKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 醒目留言、礼物、INTERACT_WORD_V2 为 bilibili-API-collect 存档中的真实样本；DANMU_MSG 按 blivedm 的字段下标构造。 */
class LiveCommandParserTest {

    @Test
    fun danmaku() {
        val item = LiveCommandParser.parse("{\"cmd\": \"DANMU_MSG:4:0:2:2:2:0\", \"info\": [[0, 1, 25, 16777215, 1758800000000, -1, 0, \"abc\", 0, 0, 0, \"\", 0, \"{}\", \"{}\", {\"extra\": \"{\\\"content\\\":\\\"你好\\\"}\", \"mode\": 0, \"user\": {\"uid\": 0, \"base\": {\"name\": \"张**\"}}}], \"你好\", [0, \"张**\", 0, 0, 0, 10000, 1, \"\"], [], [0, 0, 9868950, \">50000\"], [\"\", \"\"], 0, 0, null, {\"ts\": 1758800000, \"ct\": \"X\"}, 0, 0, null, null, 0, 0, [0]]}").single()
        assertEquals(DanmakuKind.DANMAKU, item.kind)
        assertEquals("你好", item.text)
        assertEquals("张**", item.userName)
        assertEquals(0L, item.userId)
    }

    @Test
    fun danmakuNameFallbackToModeInfo() {
        val item = LiveCommandParser.parse("{\"cmd\": \"DANMU_MSG\", \"info\": [[0, 1, 25, 16777215, 1758800000000, -1, 0, \"abc\", 0, 0, 0, \"\", 0, \"{}\", \"{}\", {\"extra\": \"{}\", \"mode\": 0, \"user\": {\"uid\": 123, \"base\": {\"name\": \"新版用户名\"}}}], \"只在新字段里有名字\", [0, null, 0, 0, 0, 10000, 1, \"\"], [], [0, 0, 0, \"\"], [\"\", \"\"], 0, 0, null, {}, 0, 0, null, null, 0, 0, [0]]}").single()
        assertEquals("新版用户名", item.userName)
        assertEquals(123L, item.userId)
    }

    @Test
    fun superChat() {
        val item = LiveCommandParser.parse("{\"cmd\": \"SUPER_CHAT_MESSAGE\", \"data\": {\"background_bottom_color\": \"#2A60B2\", \"background_color\": \"#EDF5FF\", \"background_color_end\": \"#405D85\", \"background_color_start\": \"#3171D2\", \"background_icon\": \"\", \"background_image\": \"https://i0.hdslb.com/bfs/live/a712efa5c6ebc67bafbe8352d3e74b820a00c13e.png\", \"background_price_color\": \"#7497CD\", \"color_point\": 0.7, \"dmscore\": 120, \"end_time\": 1677069095, \"gift\": {\"gift_id\": 12000, \"gift_name\": \"醒目留言\", \"num\": 1}, \"id\": 6522809, \"is_ranked\": 1, \"is_send_audit\": 0, \"medal_info\": {\"anchor_roomid\": 732, \"anchor_uname\": \"Asaki大人\", \"guard_level\": 3, \"icon_id\": 0, \"is_lighted\": 1, \"medal_color\": \"#1a544b\", \"medal_color_border\": 6809855, \"medal_color_end\": 5414290, \"medal_color_start\": 1725515, \"medal_level\": 21, \"medal_name\": \"ASAKI\", \"special\": \"\", \"target_id\": 194484313}, \"message\": \"猪播完美预测自己第一个死，这就是鹅鸭杀高玩吗\", \"message_font_color\": \"#A3F6FF\", \"message_trans\": \"\", \"price\": 30, \"rate\": 1000, \"start_time\": 1677069035, \"time\": 60, \"token\": \"7BED5681\", \"trans_mark\": 0, \"ts\": 1677069035, \"uid\": 294094150, \"user_info\": {\"face\": \"https://i1.hdslb.com/bfs/face/7a11b48e0a3055e220fa8b4c7d938cd4bcac2577.jpg\", \"face_frame\": \"https://i0.hdslb.com/bfs/live/80f732943cc3367029df65e267960d56736a82ee.png\", \"guard_level\": 3, \"is_main_vip\": 1, \"is_svip\": 0, \"is_vip\": 0, \"level_color\": \"#969696\", \"manager\": 0, \"name_color\": \"#00D1F1\", \"title\": \"0\", \"uname\": \"界原虚\", \"user_level\": 6}}, \"roomid\": 6154037}").single()
        assertEquals(DanmakuKind.SUPER_CHAT, item.kind)
        assertEquals(30, item.price)
        assertEquals("界原虚", item.userName)
        assertTrue(item.text.startsWith("猪播"))
    }

    @Test
    fun gift() {
        val item = LiveCommandParser.parse("{\"cmd\": \"SEND_GIFT\", \"data\": {\"action\": \"投喂\", \"batch_combo_id\": \"batch:gift:combo_id:510149209:36047134:31036:1673622464.8445\", \"batch_combo_send\": {\"action\": \"投喂\", \"batch_combo_id\": \"batch:gift:combo_id:510149209:36047134:31036:1673622464.8445\", \"batch_combo_num\": 1, \"blind_gift\": null, \"gift_id\": 31036, \"gift_name\": \"小花花\", \"gift_num\": 1, \"send_master\": null, \"uid\": 510149209, \"uname\": \"12138额83121\"}, \"beatId\": \"\", \"biz_source\": \"live\", \"blind_gift\": null, \"broadcast_id\": 0, \"coin_type\": \"gold\", \"combo_resources_id\": 1, \"combo_send\": {\"action\": \"投喂\", \"combo_id\": \"gift:combo_id:510149209:36047134:31036:1673622464.8434\", \"combo_num\": 1, \"gift_id\": 31036, \"gift_name\": \"小花花\", \"gift_num\": 1, \"send_master\": null, \"uid\": 510149209, \"uname\": \"12138额83121\"}, \"combo_stay_time\": 3, \"combo_total_coin\": 100, \"crit_prob\": 0, \"demarcation\": 1, \"discount_price\": 100, \"dmscore\": 8, \"draw\": 0, \"effect\": 0, \"effect_block\": 0, \"face\": \"https://i1.hdslb.com/bfs/face/fb79103e8b33547023e2010030b6889bba2b49bf.jpg\", \"face_effect_id\": 0, \"face_effect_type\": 0, \"float_sc_resource_id\": 0, \"giftId\": 31036, \"giftName\": \"小花花\", \"giftType\": 0, \"gold\": 0, \"guard_level\": 0, \"is_first\": true, \"is_join_receiver\": false, \"is_naming\": false, \"is_special_batch\": 0, \"magnification\": 1, \"medal_info\": {\"anchor_roomid\": 0, \"anchor_uname\": \"\", \"guard_level\": 0, \"icon_id\": 0, \"is_lighted\": 0, \"medal_color\": 0, \"medal_color_border\": 0, \"medal_color_end\": 0, \"medal_color_start\": 0, \"medal_level\": 0, \"medal_name\": \"\", \"special\": \"\", \"target_id\": 0}, \"name_color\": \"\", \"num\": 1, \"original_gift_name\": \"\", \"price\": 100, \"rcost\": 164536872, \"receive_user_info\": {\"uid\": 36047134, \"uname\": \"小霖QL\"}, \"remain\": 0, \"rnd\": \"1673622464121900003\", \"send_master\": null, \"silver\": 0, \"super\": 0, \"super_batch_gift_num\": 1, \"super_gift_num\": 1, \"svga_block\": 0, \"switch\": true, \"tag_image\": \"\", \"tid\": \"1673622464121900003\", \"timestamp\": 1673622464, \"top_list\": null, \"total_coin\": 100, \"uid\": 510149209, \"uname\": \"12138额83121\"}}").single()
        assertEquals(DanmakuKind.GIFT, item.kind)
        assertEquals("投喂 小花花 ×1（¥0.1）", item.text)
    }

    @Test
    fun interactWordV2() {
        val item = LiveCommandParser.parse("{\"cmd\": \"INTERACT_WORD_V2\", \"data\": {\"dmscore\": 3, \"pb\": \"CJTwwNEBEgpTdGFyU2VhMjQ2IgIDASgBMNWgITispaTDBkDUubHe/jJKLAiv8CkQEhoG55Sf5oCBIKS6ngYopLqeBjCkup4GOKS6ngZAAWDVoCFo9JQRYgB4gZ/v1tmc1qcYmgEAsgHPAQiU8MDRARJYCgpTdGFyU2VhMjQ2EkpodHRwczovL2kwLmhkc2xiLmNvbS9iZnMvZmFjZS8xMDliNzg3YzVmMTEzYzRhM2M3NDE1YmI5YmY2YjgyYmMzM2JjNGUyLmpwZxpnCgbnlJ/mgIEQEhikup4GIKS6ngYopLqeBjCkup4GOP/hAUgBUK/wKWD0lBF6CSNEQzZCNkI5OYIBCSNEQzZCNkI5OYoBCSNEQzZCNkI5OZIBCSNGRkZGRkZGRpoBCSM4MTAwMUY5OSICCAkyALoBAA==\"}}").single()
        assertEquals(DanmakuKind.ENTER, item.kind)
        assertEquals("StarSea246", item.userName)
        assertEquals("进入直播间", item.text)
    }

    @Test
    fun loginNotice() {
        val item = LiveCommandParser.parse("{\"cmd\":\"LOG_IN_NOTICE\",\"data\":{\"notice_msg\":\"为保护用户隐私\"}}").single()
        assertEquals(DanmakuKind.SYSTEM, item.kind)
    }

    @Test
    fun unknownCmdIgnored() {
        assertTrue(LiveCommandParser.parse("{\"cmd\":\"ONLINE_RANK_COUNT\",\"data\":{}}").isEmpty())
    }
}
