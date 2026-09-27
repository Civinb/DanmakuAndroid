// 版本号选的是已确认存在、互相兼容的稳定版本。Android Studio 提示升级时可以先忽略，
// 等功能跑通后再统一升级。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
