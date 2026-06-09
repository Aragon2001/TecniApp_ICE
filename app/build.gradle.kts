plugins {
    // Plugins declarados en Version Catalog (libs.versions.toml)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)

    // Necesario para Firebase (google-services.json)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.Arasoftsolutions.tecniapp_ice"
    compileSdk = 34

    buildFeatures {
        // Binding / BuildConfig (ya los estás usando)
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.Arasoftsolutions.tecniapp_ice"

        // Usados por IceApi / Repository
        buildConfigField("String", "ICE_BASE_URL", "\"https://agenciaelectricidad.cn.ice.go.cr/api/\"")
        buildConfigField("String", "ICE_BEARER", "\"\"")
        buildConfigField(
            "String",
            "UPDATE_JSON_URL",
            "\"https://raw.githubusercontent.com/Aragon2001/TecniApp_ICE/master/updates/update.json\""

        )

        minSdk = 26
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Java/Kotlin 17 (alineado con Room / coroutines modernos)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    // Evita conflictos de licencias/metadata típicos al empaquetar
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
        }
    }
}

dependencies {
    /* =========================================================
       AndroidX / UI base
       ========================================================= */
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.datastore.preferences)

    implementation("androidx.viewpager2:viewpager2:1.0.0")

    /* =========================================================
       Lifecycle / Navigation
       ========================================================= */
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    /* =========================================================
       Google Play Services (Mapas / Localización)
       ========================================================= */
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    /* =========================================================
       Firebase (IMPORTANTE)
       ---------------------------------------------------------
       ✅ Usamos BoM como ÚNICA fuente de versiones.
       ❌ NO mezclar con libs.firebase.messaging.ktx / libs.firebase.functions.ktx
          porque eso introduce versiones que pueden no existir y rompe Gradle.
       ========================================================= */
    implementation(platform(libs.firebase.bom))

    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Messaging / FCM (usa -ktx, no el artifact sin KTX)
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Remote Config / Storage / Functions (Callable)
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")

    /* =========================================================
       Room + KSP
       ========================================================= */
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)


    ksp(libs.androidx.room.compiler)

    // Extras que ya usas en UI/layout
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.datastore.preferences.core.android)

    /* =========================================================
       Coroutines / WorkManager
       ---------------------------------------------------------
       ⚠️ Antes tenías play-services en 2 versiones distintas.
       Dejamos SOLO una (1.8.1) para evitar conflictos.
       ========================================================= */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.guava:guava:31.1-android")

    /* =========================================================
       Networking: Retrofit + Moshi (codegen)
       ========================================================= */
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")

    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    /* =========================================================
       Email (JavaMail Android)
       ---------------------------------------------------------
       Nota: aunque migres el envío a Cloud Functions, esto lo dejo
       porque puede estar usado aún por clases existentes.
       Cuando confirmes que ya no hay ninguna referencia a MailSender,
       lo podemos remover con seguridad.
       ========================================================= */
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    /* =========================================================
       Serialization + Converter
       ========================================================= */
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    /* =========================================================
       Imágenes
       ========================================================= */
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:compiler:4.16.0")

    /* =========================================================
       Exportación a Excel
       ========================================================= */
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    /* =========================================================
       Lectura de PDF
       ========================================================= */
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    /* =========================================================
       Gráficas operativas (Mi Resumen)
       ========================================================= */
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    /* =========================================================
       Testing
       ========================================================= */
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

/* =========================================================
   KSP: schemas de Room (útil para migraciones)
   ========================================================= */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
