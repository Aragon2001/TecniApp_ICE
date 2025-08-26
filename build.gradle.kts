
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    // Google Services para Firebase
    id("com.google.gms.google-services") version "4.4.2" apply false
    // KSP (Room usa KSP en Kotlin 2.x)
    alias(libs.plugins.ksp) apply false
}
