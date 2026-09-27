package com.civinb.danmuji.data.bili

import android.content.Context
import com.civinb.danmuji.data.bili.auth.CredentialStore
import com.civinb.danmuji.data.bili.auth.LoginApi
import com.civinb.danmuji.data.bili.link.LinkResolver
import com.civinb.danmuji.data.bili.live.LiveRoomApi
import com.civinb.danmuji.data.bili.video.VideoApi

/** 接口层的依赖容器（应用级单例，在 DanmuApp 里创建）。 */
class BiliClient(context: Context) {
    private val prefs = context.getSharedPreferences("bili", Context.MODE_PRIVATE)
    val http = BiliHttp(prefs)
    val auth = BiliAuth(http, prefs)
    val liveApi = LiveRoomApi(http, auth)
    val videoApi = VideoApi(http, auth)
    val linkResolver = LinkResolver(http)
    val login = LoginApi(http, CredentialStore(context)).also { it.restore() }
}
