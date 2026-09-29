package com.blackvueeventos.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun isWifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
