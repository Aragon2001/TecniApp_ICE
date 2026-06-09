package com.Arasoftsolutions.tecniapp_ice.network

enum class NetworkHealth {
    OFFLINE,
    CONNECTED_NO_INTERNET,
    SLOW,
    UNSTABLE,
    STABLE;

    val isUsable: Boolean get() = this == SLOW || this == UNSTABLE || this == STABLE
    val isStable: Boolean get() = this == STABLE
    val blocksLogin: Boolean get() = this == OFFLINE || this == CONNECTED_NO_INTERNET
    val blocksFullSync: Boolean get() = this == OFFLINE || this == CONNECTED_NO_INTERNET
}