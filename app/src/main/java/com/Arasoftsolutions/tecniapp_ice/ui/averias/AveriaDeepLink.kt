package com.Arasoftsolutions.tecniapp_ice.ui.averias

object AveriaDeepLink {
    @Volatile
    var pendingCaseId: String? = null

    fun consume(): String? {
        val id = pendingCaseId
        pendingCaseId = null
        return id
    }
}
