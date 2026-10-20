package com.themis.mobile.sync

import android.content.Context
import android.net.wifi.WifiManager
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*

data class DesktopHost(val host: String, val port: Int)

object Discovery {
    private const val PORT = 54321
    private const val TIMEOUT_MS = 3000L

    /** Broadcast THEMIS_DISCOVERY_PING and wait up to 3 s for the desktop's JSON reply. */
    suspend fun find(context: Context): DesktopHost? = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("themis-discovery").apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val ping = "THEMIS_DISCOVERY_PING".toByteArray()
                // Global broadcast plus each interface's subnet broadcast (some routers drop 255.255.255.255).
                (listOf("255.255.255.255") + subnetBroadcasts()).distinct().forEach { target ->
                    try { socket.send(DatagramPacket(ping, ping.size, InetAddress.getByName(target), PORT)) }
                    catch (_: Exception) { }
                }
                val buf = ByteArray(2048)
                val deadline = System.currentTimeMillis() + TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    socket.soTimeout = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
                    val packet = DatagramPacket(buf, buf.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { return@withContext null }
                    parse(String(packet.data, 0, packet.length), packet.address.hostAddress)?.let { return@withContext it }
                }
                null
            }
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    private fun parse(json: String, host: String?): DesktopHost? = try {
        val o = JsonParser.parseString(json).asJsonObject
        if (host != null && o["app"]?.asString == "Themis" && o["role"]?.asString == "desktop")
            DesktopHost(host, o["port"].asInt) else null
    } catch (_: Exception) { null }

    private fun subnetBroadcasts(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast?.hostAddress }
    } catch (_: Exception) { emptyList() }
}
