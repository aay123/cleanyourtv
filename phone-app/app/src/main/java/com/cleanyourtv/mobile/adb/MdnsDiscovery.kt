package com.cleanyourtv.mobile.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.Closeable

data class DiscoveredEndpoint(
    val name: String,
    val host: String,
    val port: Int,
)

data class DiscoveryState(
    val running: Boolean = false,
    val connectDevices: List<DiscoveredEndpoint> = emptyList(),
    val pairDevices: List<DiscoveredEndpoint> = emptyList(),
)

/**
 * Finds Android 11+ wireless debugging endpoints through Android's own mDNS resolver.
 * Discovery needs no extra dependency and keeps the same two service types adb uses.
 */
class MdnsDiscovery(context: Context) : Closeable {

    private val nsd: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _state = MutableStateFlow(DiscoveryState())
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val resolving = mutableSetOf<String>()

    fun start() {
        if (_state.value.running) return
        _state.value = DiscoveryState(running = true)
        discover(SERVICE_CONNECT, pairing = false)
        discover(SERVICE_PAIRING, pairing = true)
    }

    fun stop() {
        for (listener in listeners) {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        listeners.clear()
        resolving.clear()
        _state.value = DiscoveryState()
    }

    override fun close() = stop()

    private fun discover(serviceType: String, pairing: Boolean) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(serviceInfo, pairing)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _state.update { current ->
                    if (pairing) {
                        current.copy(pairDevices = current.pairDevices.filterNot { it.name == serviceInfo.serviceName })
                    } else {
                        current.copy(connectDevices = current.connectDevices.filterNot { it.name == serviceInfo.serviceName })
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stop()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        listeners += listener
        runCatching { nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { stop() }
    }

    @Suppress("DEPRECATION")
    private fun resolve(serviceInfo: NsdServiceInfo, pairing: Boolean) {
        val key = "${serviceInfo.serviceName}/${serviceInfo.serviceType}"
        if (!resolving.add(key)) return
        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolving.remove(key)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolving.remove(key)
                val host = serviceInfo.host?.hostAddress ?: return
                val endpoint = DiscoveredEndpoint(serviceInfo.serviceName, host, serviceInfo.port)
                _state.update { current ->
                    if (pairing) {
                        current.copy(pairDevices = current.pairDevices.upsert(endpoint))
                    } else {
                        current.copy(connectDevices = current.connectDevices.upsert(endpoint))
                    }
                }
            }
        })
    }

    private fun List<DiscoveredEndpoint>.upsert(endpoint: DiscoveredEndpoint): List<DiscoveredEndpoint> =
        filterNot { it.name == endpoint.name } + endpoint

    companion object {
        private const val SERVICE_CONNECT = "_adb-tls-connect._tcp"
        private const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"
    }
}
