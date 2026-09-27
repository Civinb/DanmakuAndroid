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

    /** bilibili-API-collect 存档里的真实 DANMU_MSG 样本：文字“白花300块[热]”，extra.emots 含 "[热]" */
    @Test
    fun realDanmakuWithInlineEmote() {
        val item = LiveCommandParser.parse("{\"cmd\": \"DANMU_MSG\", \"dm_v2\": \"\", \"info\": [[0, 1, 25, 9920249, 1723979200649, -1312973962, 0, \"0bc8acd0\", 0, 0, 0, \"\", 0, \"{}\", \"{}\", {\"extra\": \"{\\\"send_from_me\\\":false,\\\"mode\\\":0,\\\"color\\\":9920249,\\\"dm_type\\\":0,\\\"font_size\\\":25,\\\"player_mode\\\":1,\\\"show_player_type\\\":0,\\\"content\\\":\\\"白花300块[热]\\\",\\\"user_hash\\\":\\\"197700816\\\",\\\"emoticon_unique\\\":\\\"\\\",\\\"bulge_display\\\":0,\\\"recommend_score\\\":3,\\\"main_state_dm_color\\\":\\\"\\\",\\\"objective_state_dm_color\\\":\\\"\\\",\\\"direction\\\":0,\\\"pk_direction\\\":0,\\\"quartet_direction\\\":0,\\\"anniversary_crowd\\\":0,\\\"yeah_space_type\\\":\\\"\\\",\\\"yeah_space_url\\\":\\\"\\\",\\\"jump_to_url\\\":\\\"\\\",\\\"space_type\\\":\\\"\\\",\\\"space_url\\\":\\\"\\\",\\\"animation\\\":{},\\\"emots\\\":{\\\"[热]\\\":{\\\"count\\\":1,\\\"descript\\\":\\\"[热]\\\",\\\"emoji\\\":\\\"[热]\\\",\\\"emoticon_id\\\":278,\\\"emoticon_unique\\\":\\\"emoji_278\\\",\\\"height\\\":20,\\\"url\\\":\\\"http://i0.hdslb.com/bfs/live/6df760280b17a6cbac8c1874d357298f982ba4cf.png\\\",\\\"width\\\":20}},\\\"is_audited\\\":false,\\\"id_str\\\":\\\"364b06e3c561af3d5921f1253d66c1d575\\\",\\\"icon\\\":{\\\"prefix\\\":{\\\"type\\\":1,\\\"resource\\\":\\\"ChronosWealth_4.png\\\"}},\\\"show_reply\\\":true,\\\"reply_mid\\\":0,\\\"reply_uname\\\":\\\"\\\",\\\"reply_uname_color\\\":\\\"\\\",\\\"reply_is_mystery\\\":false,\\\"hit_combo\\\":0}\", \"mode\": 0, \"show_player_type\": 0, \"user\": {\"base\": {\"face\": \"https://i1.hdslb.com/bfs/face/5a9bb9cac3afbb58347c808ae76aaa41ca967d07.jpg\", \"is_mystery\": false, \"name\": \"tim1997\", \"name_color\": 0, \"name_color_str\": \"\", \"official_info\": {\"desc\": \"\", \"role\": 0, \"title\": \"\", \"type\": -1}, \"origin_info\": {\"face\": \"https://i1.hdslb.com/bfs/face/5a9bb9cac3afbb58347c808ae76aaa41ca967d07.jpg\", \"name\": \"tim1997\"}, \"risk_ctrl_info\": null}, \"guard\": null, \"guard_leader\": {\"is_guard_leader\": false}, \"medal\": {\"color\": 2951253, \"color_border\": 16771156, \"color_end\": 10329087, \"color_start\": 2951253, \"guard_icon\": \"https://i0.hdslb.com/bfs/live/1d16bf0fcc3b1b768d1179d60f1fdbabe6ab4489.png\", \"guard_level\": 1, \"honor_icon\": \"\", \"id\": 1279130, \"is_light\": 1, \"level\": 29, \"name\": \"果咩吖\", \"ruid\": 3546569288714792, \"score\": 50427312, \"typ\": 0, \"user_receive_count\": 0, \"v2_medal_color_border\": \"#D47AFFFF\", \"v2_medal_color_end\": \"#9660E5CC\", \"v2_medal_color_level\": \"#6C00A099\", \"v2_medal_color_start\": \"#9660E5CC\", \"v2_medal_color_text\": \"#FFFFFFFF\"}, \"title\": {\"old_title_css_id\": \"\", \"title_css_id\": \"\"}, \"uhead_frame\": null, \"uid\": 6088969, \"wealth\": null}}, {\"activity_identity\": \"\", \"activity_source\": 0, \"not_show\": 0}, 0], \"白花300块[热]\", [6088969, \"tim1997\", 0, 0, 0, 10000, 1, \"\"], [29, \"果咩吖\", \"果宝Official\", 31180317, 2951253, \"\", 0, 16771156, 2951253, 10329087, 1, 1, 3546569288714792], [39, 0, 10512625, 42523, 2], [\"\", \"\"], 0, 0, null, {\"ct\": \"AFFF4206\", \"ts\": 1723979200}, 0, 0, null, null, 0, 1040, [49], null]}").single()
        assertEquals("白花300块[热]", item.text)
        assertEquals("tim1997", item.userName)
        assertEquals(listOf("[热]"), item.emoteTokens)
        assertEquals(false, item.emoteOnly)
    }

    /** 整条大表情：dm_type=1，info[0][13] 为表情对象（字段取自 blivedm models/web.py 注释示例） */
    @Test
    fun stickerDanmaku() {
        val item = LiveCommandParser.parse("{\"cmd\": \"DANMU_MSG\", \"info\": [[0, 1, 25, 16777215, 1758800000000, -1, 0, \"abc\", 0, 0, 0, \"\", 1, {\"bulge_display\": 0, \"emoticon_unique\": \"official_13\", \"height\": 60, \"in_player_area\": 1, \"is_dynamic\": 1, \"url\": \"https://i0.hdslb.com/bfs/live/a98e35996545509188fe4d24bd1a56518ea5af48.png\", \"width\": 183}, \"{}\", {\"extra\": \"{\\\"dm_type\\\":1,\\\"emots\\\":null}\", \"mode\": 0, \"user\": {\"uid\": 0, \"base\": {\"name\": \"李**\"}}}], \"赞\", [0, \"李**\", 0, 0, 0, 10000, 1, \"\"], [], [0, 0, 0, \"\"], [\"\", \"\"], 0, 0, null, {}, 0, 0, null, null, 0, 0, [0]]}").single()
        assertEquals(true, item.emoteOnly)
    }

    /** info[0][13] 以 JSON 字符串形式出现时也能识别（blivedm 注释：Union[dict, str]） */
    @Test
    fun stickerDanmakuAsString() {
        val item = LiveCommandParser.parse("{\"cmd\": \"DANMU_MSG\", \"info\": [[0, 1, 25, 16777215, 1758800000000, -1, 0, \"abc\", 0, 0, 0, \"\", 0, \"{\\\"bulge_display\\\": 0, \\\"emoticon_unique\\\": \\\"official_13\\\", \\\"height\\\": 60, \\\"in_player_area\\\": 1, \\\"is_dynamic\\\": 1, \\\"url\\\": \\\"https://i0.hdslb.com/bfs/live/a98e35996545509188fe4d24bd1a56518ea5af48.png\\\", \\\"width\\\": 183}\", \"{}\", {\"extra\": \"{\\\"dm_type\\\":1,\\\"emots\\\":null}\", \"mode\": 0, \"user\": {\"uid\": 0, \"base\": {\"name\": \"李**\"}}}], \"赞\", [0, \"李**\", 0, 0, 0, 10000, 1, \"\"], [], [0, 0, 0, \"\"], [\"\", \"\"], 0, 0, null, {}, 0, 0, null, null, 0, 0, [0]]}").single()
        assertEquals(true, item.emoteOnly)
    }

}
