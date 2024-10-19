// Plugins necesarios para el proyecto. Incluyen el soporte para Kotlin y los servicios de Google.
plugins {
    alias(libs.plugins.android.application) // Plugin para aplicaciones Android
    alias(libs.plugins.jetbrains.kotlin.android) // Plugin para soporte Kotlin en Android
    id("com.google.gms.google-services") // Plugin para servicios de Google (Firebase, etc.)
}

android {
    namespace = "com.Arasoftsolutions.tecniapp_ice" // Nombre del paquete
    compileSdk = 34 // Versión del SDK de compilación

    defaultConfig {
        applicationId = "com.Arasoftsolutions.tecniapp_ice" // ID del paquete
        minSdk = 31 // Versión mínima de SDK requerida para que la app funcione
        targetSdk = 34 // Versión de SDK objetivo (donde se espera que la app se ejecute correctamente)
        versionCode = 1 // Código de versión de la app (para Play Store)
        versionName = "1.0" // Nombre de la versión (para mostrar al usuario)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // Runner de pruebas
    }

    buildTypes {
        // Configuración del tipo de build "release"
        release {
            isMinifyEnabled = false // Deshabilita la minificación de código (optimización)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), // Archivo ProGuard por defecto
                "proguard-rules.pro" // Archivo personalizado de reglas ProGuard
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8 // Compatibilidad con Java 1.8
        targetCompatibility = JavaVersion.VERSION_1_8 // Compatibilidad de destino con Java 1.8
    }

    kotlinOptions {
        jvmTarget = "1.8" // Target JVM para Kotlin, también es 1.8
    }

    buildFeatures {
        viewBinding = true // Habilita ViewBinding para evitar el uso de findViewById
    }
}

dependencies {
    // Dependencias esenciales de Android
    implementation(libs.androidx.core.ktx) // Extensiones para Kotlin
    implementation(libs.androidx.appcompat) // Compatibilidad con versiones antiguas de Android
    implementation(libs.material) // Biblioteca de componentes Material Design
    implementation(libs.androidx.constraintlayout) // Para layouts con ConstraintLayout
    implementation(libs.androidx.lifecycle.livedata.ktx) // LiveData para Kotlin
    implementation(libs.androidx.lifecycle.viewmodel.ktx) // ViewModel para Kotlin
    implementation(libs.androidx.navigation.fragment.ktx) // Navegación entre fragmentos con Kotlin
    implementation(libs.androidx.navigation.ui.ktx) // Soporte UI para la navegación

    // Biblioteca Picasso para cargar imágenes
    implementation("com.squareup.picasso:picasso:2.71828")

    // Firebase BOM: Gestor de versiones de Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // Autenticación Firebase (con KTX para mejor soporte en Kotlin)
    implementation("com.google.firebase:firebase-auth-ktx")

    // Firebase Firestore (con soporte KTX para consultas más simples)
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Firebase Realtime Database (con soporte KTX)
    implementation("com.google.firebase:firebase-database-ktx")

    // Google Maps API para integrar mapas en la aplicación
    implementation(libs.play.services.maps)
    implementation ("com.google.android.gms:play-services-auth:21.2.0")
implementation ("com.google.firebase:firebase-auth:23.0.0")


    // Google Sign-In: Inicio de sesión con Google
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Manejo de correo electrónico en Android (usando la biblioteca Sun Mail)
    implementation("com.sun.mail:android-mail:1.6.2") // Mail API
    implementation("com.sun.mail:android-activation:1.6.2") // Soporte para activación de contenido

    // Firebase Analytics para el análisis de datos en la aplicación
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation(libs.play.services.location)

    // Dependencias para realizar pruebas
    testImplementation(libs.junit) // JUnit para pruebas unitarias
    androidTestImplementation(libs.androidx.junit) // JUnit para pruebas en Android
    androidTestImplementation(libs.androidx.espresso.core) // Espresso para pruebas de UI
}
