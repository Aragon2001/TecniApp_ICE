package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

object LuminariaDeepLink {
    @Volatile
    var pendingLuminariaId: Int? = null

    fun consume(): Int? {
        val id = pendingLuminariaId
        pendingLuminariaId = null
        return id
    }
}
