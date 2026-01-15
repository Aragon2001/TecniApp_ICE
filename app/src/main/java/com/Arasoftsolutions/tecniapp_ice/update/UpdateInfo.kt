package com.Arasoftsolutions.tecniapp_ice.update

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val notes: String
) {
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}
