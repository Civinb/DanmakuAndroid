package com.civinb.danmuji.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 监听系统默认网络。WiFi ↔ 流量切换时 handle 会变化，直播连接据此立即重连。
 */
class NetworkMonitor(context: Context) {

    data class NetState(val available: Boolean, val handle: Long)

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<NetState> = _state.asStateFlow()

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val handle = network.networkHandle
                if (_state.value.handle != handle || !_state.value.available) {
                    DebugLog.log(TAG, "默认网络变为 $handle")
                }
                _state.value = NetState(true, handle)
            }

            override fun onLost(network: Network) {
                if (_state.value.handle == network.networkHandle) {
                    DebugLog.log(TAG, "网络断开")
                    _state.value = NetState(false, 0)
                }
            }
        })
    }

    val isAvailable: Boolean get() = _state.value.available

    suspend fun awaitAvailable() {
        state.first { it.available }
    }

    private fun initialState(): NetState {
        val n = cm.activeNetwork
        return NetState(n != null, n?.networkHandle ?: 0)
    }

    private companion object {
        const val TAG = "Net"
    }
}
