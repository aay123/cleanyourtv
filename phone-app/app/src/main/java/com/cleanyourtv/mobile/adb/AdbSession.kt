package com.cleanyourtv.mobile.adb

import com.cleanyourtv.mobile.data.DeviceInfo
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * One authenticated ADB connection to a TV. Kadb speaks the wire protocol directly, so no adb binary
 * and no adb server are involved. The host key lives in the app's files dir (see CleanYourTvApp) and
 * the TV shows its "Allow USB debugging?" prompt on the first connection.
 */
class AdbSession(
    val host: String,
    val port: Int,
) : Shell, Closeable {

    @Volatile
    private var kadb: Kadb? = null

    val isConnected: Boolean get() = kadb != null

    suspend fun connect(): DeviceInfo = withContext(Dispatchers.IO) {
        val client = Kadb.create(
            host = host,
            port = port,
            connectTimeout = CONNECT_TIMEOUT_MS,
            socketTimeout = IO_TIMEOUT_MS,
        )
        kadb = client
        try {
            val ready = client.shell("echo ready")
            if (!ready.output.contains("ready")) {
                throw IOException("the TV answered the ADB handshake but not a shell command")
            }
            readDeviceInfo()
        } catch (error: Throwable) {
            runCatching { client.close() }
            kadb = null
            throw error
        }
    }

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val client = kadb ?: throw IOException("not connected")
        val response = client.shell(command)
        ShellResult(response.output, response.errorOutput, response.exitCode)
    }

    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val client = kadb ?: throw IOException("not connected")
        client.install(apk, "-r")
    }

    suspend fun readDeviceInfo(): DeviceInfo {
        val props = HashMap<String, String>()
        val output = exec("getprop").output
        Regex("^\\[([^]]+)]: \\[(.*)]$", RegexOption.MULTILINE).findAll(output).forEach {
            props[it.groupValues[1]] = it.groupValues[2]
        }
        fun prop(vararg keys: String): String = keys.firstNotNullOfOrNull { props[it]?.trim() } ?: ""
        return DeviceInfo(
            model = prop("ro.product.model", "ro.product.vendor.model"),
            brand = prop("ro.product.brand"),
            manufacturer = prop("ro.product.manufacturer"),
            device = prop("ro.product.device", "ro.product.vendor.device"),
            product = prop("ro.product.name"),
            name = prop("ro.product.device"),
            androidVersion = prop("ro.build.version.release"),
            sdk = prop("ro.build.version.sdk").toIntOrNull() ?: 0,
        )
    }

    override fun close() {
        runCatching { kadb?.close() }
        kadb = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val IO_TIMEOUT_MS = 180_000

        /**
         * Android 11+ wireless debugging: pair on the ephemeral pairing port shown under
         * "Pair device with pairing code", then connect to the separate debug port.
         */
        suspend fun pair(host: String, pairingPort: Int, code: String) = withContext(Dispatchers.IO) {
            Kadb.pair(host, pairingPort, code.trim())
        }
    }
}
