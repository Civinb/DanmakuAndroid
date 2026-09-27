package com.civinb.danmuji.source

import com.civinb.danmuji.model.DanmakuItem
import com.civinb.danmuji.video.VideoUiState
import kotlinx.coroutines.flow.Flow

/** 弹幕来源发出的事件：一条弹幕，或一条状态变化（连接中/已连接/重连中……）。 */
sealed interface SourceEvent {
    data class Item(val item: DanmakuItem) : SourceEvent
    data class Status(val text: String) : SourceEvent

    /** 清空悬浮窗列表（视频拖动进度后重建） */
    data object Clear : SourceEvent

    /** 视频模式的播放状态，悬浮窗控制条据此刷新 */
    data class VideoState(val state: VideoUiState) : SourceEvent
}

/**
 * 所有弹幕来源（直播、视频）都实现这个接口。
 * 悬浮窗和服务只依赖它；B 站接口变了只需改具体实现。
 * 返回的 Flow 被取消时，实现方必须释放连接。
 */
interface DanmakuSource {
    fun events(): Flow<SourceEvent>
}
