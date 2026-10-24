package com.osama.parentstv.web

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfo {
    /** The TV's address on the home network (Wi-Fi or Ethernet), or null when offline. */
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
