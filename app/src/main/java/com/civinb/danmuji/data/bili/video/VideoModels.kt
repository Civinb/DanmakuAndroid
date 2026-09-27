package com.civinb.danmuji.data.bili.video

/** 一个分P。durationSec 为秒。 */
data class VideoPage(val cid: Long, val page: Int, val part: String, val durationSec: Long)

/** 视频信息。title 可能为空（只从分P列表接口拿到时）。 */
data class VideoInfo(val bvid: String, val aid: Long, val title: String, val pages: List<VideoPage>)
