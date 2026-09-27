package com.civinb.danmuji.probe

import android.service.notification.NotificationListenerService

/**
 * 空的通知监听服务。
 * Android 只允许“已被用户授予通知使用权的应用”调用 MediaSessionManager.getActiveSessions()，
 * 这个类存在的唯一目的就是让用户能在系统设置里给本应用授权。
 * 我们不重写 onNotificationPosted，不读取任何通知内容。
 * 类名/包名沿用开发期的“探针”命名，不要改：系统按组件名记录授权，改名后需重新授予通知使用权。
 */
class MediaProbeListenerService : NotificationListenerService()
