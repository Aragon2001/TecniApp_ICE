// =====================================
// app/build.gradle.kts  (Módulo: app)
// Plan A: usar BuildConfig generado
// =====================================

plugins {
    // Android App
    alias(libs.plugins.android.application)

    // Kotlin Android
    alias(libs.plugins.jetbrains.kotlin.android)

    // KSP (Room usa KSP en lugar de kapt)
    alias(libs.plugins.ksp)

    // Google Services (Firebase)
    id("com.google.gms.google-services")
}

android {
    // Debe coincidir con el paquete base de tu app (donde importas BuildConfig)
    namespace = "com.Arasoftsolutions.tecniapp_ice"

    // API de compilación
    compileSdk = 34

    // 🔓 Habilita la generación de BuildConfig (requerido para buildConfigField)
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    defaultConfig {
        // Suele ser igual al namespace en apps; en libs no aplica
        applicationId = "com.Arasoftsolutions.tecniapp_ice"

        // Constantes accesibles como BuildConfig.ICE_BASE_URL / ICE_BEARER
        buildConfigField("String", "ICE_BASE_URL", "\"https://agenciaelectricidad.cn.ice.go.cr/api/\"")
        buildConfigField("String", "ICE_BEARER", "\"\"") // si luego usas token, colócalo aquí o inyecta en runtime

        minSdk = 24
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Java/Kotlin 17 (alineado con Room moderno y Kotlin 2.x)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    kotlin { jvmToolchain(17) }

    // Evita conflictos de licencias/recursos
    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE*",
                "META-INF/LICENSE*",
                "META-INF/DEPENDENCIES",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
            // Alternativa si prefieres conservar uno:
            // pickFirsts += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md")
        }
    }
}

dependencies {
    // --- AndroidX / UI base ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)                     // Material Components (usa la versión del catálogo)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)

    // --- Lifecycle / Navigation ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- Google Play Services ---
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // --- Firebase (BoM para alinear versiones) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation("com.google.firebase:firebase-config-ktx:21.6.0")

    // --- Room + KSP ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- WorkManager ---
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- Retrofit + Moshi + OkHttp (UNA sola vez, sin duplicados) ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // --- Email (JavaMail para Android) ---
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")


    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")




    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// KSP - generar esquema Room (útil para migraciones / ver dif de índices)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
