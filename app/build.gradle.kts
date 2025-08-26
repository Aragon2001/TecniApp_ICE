plugins {
    // Plugin Android para aplicaciones
    alias(libs.plugins.android.application)

    // Plugin Kotlin para Android
    alias(libs.plugins.jetbrains.kotlin.android)

    // Plugin de KSP (reemplazo moderno de kapt)
    alias(libs.plugins.ksp)

    // Plugin de Google Services para Firebase
    id("com.google.gms.google-services")
}

android {
    // Namespace del proyecto (coincide con el paquete original en tu código)
    namespace = "com.Arasoftsolutions.tecniapp_ice"

    // Versión de compilación del SDK
    compileSdk = 34

    defaultConfig {
        // ID de aplicación (igual al namespace para evitar conflictos)
        applicationId = "com.Arasoftsolutions.tecniapp_ice"

        // Mínima versión de Android soportada
        minSdk = 24

        // Versión objetivo
        targetSdk = 34

        // Versión de la app
        versionCode = 1
        versionName = "1.0"

        // Runner para pruebas
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Activar ViewBinding y DataBinding si ya lo usas en layouts
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    // Configuración para usar Java 17 (requerido por Kotlin 2.x y Room 2.7+)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    kotlin {
        jvmToolchain(17)
    }

    // Evita empaquetar licencias duplicadas que a veces rompen el build
      packaging {
        resources {
            // Opción recomendada: excluir duplicados de JavaMail
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE*",
                "META-INF/LICENSE*",
                "META-INF/DEPENDENCIES",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
            // Alternativa (si prefieres conservar uno):
            // pickFirsts += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md")
        }
    }
}

dependencies {
    // --- AndroidX básico ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Ciclo de vida (ViewModel y LiveData)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Navegación Jetpack
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- Google Play Services ---
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // --- Firebase (usamos BoM para unificar versiones) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.firestore.ktx)

    // --- ROOM con KSP ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.core.okio.jvm)
    implementation(libs.androidx.runtime)
    // Aquí usamos KSP (NO kapt) para generar el código de Room
    ksp(libs.androidx.room.compiler)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- envio de emails----
    implementation("com.sun.mail:android-mail:1.6.7")
implementation("com.sun.mail:android-activation:1.6.7")
implementation("com.google.firebase:firebase-config-ktx:21.6.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

}

// Configuración extra para KSP + Room
ksp {
    // Generar esquema de base de datos (útil para migraciones)
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
