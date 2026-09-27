package com.civinb.danmuji.data.bili

import android.content.Context
import com.civinb.danmuji.data.bili.link.LinkResolver
import com.civinb.danmuji.data.bili.live.LiveRoomApi

/** 接口层的依赖容器（应用级单例，在 DanmuApp 里创建）。 */
class BiliClient(context: Context) {
    private val prefs = context.getSharedPreferences("bili", Context.MODE_PRIVATE)
    val http = BiliHttp(prefs)
    val auth = BiliAuth(http)
    val liveApi = LiveRoomApi(http, auth)
    val linkResolver = LinkResolver(http)
}
