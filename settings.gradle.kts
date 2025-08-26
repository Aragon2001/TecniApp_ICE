// Configuración de repositorios para los plugins (Gradle y Android)
pluginManagement {
    repositories {
        // Repositorio de Google (para Android, Firebase, Play Services, etc.)
        google {
            content {
                // Solo incluir grupos necesarios para evitar resolver artefactos no deseados
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Maven Central (librerías de terceros)
        mavenCentral()
        // Portal de plugins de Gradle (para plugins no oficiales de Google)
        gradlePluginPortal()
    }
}

// Configuración de resolución de dependencias para el proyecto
dependencyResolutionManagement {
    // Evita que los módulos declaren sus propios repositorios
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        // Repositorio de Google
        google()
        // Maven Central
        mavenCentral()
    }
}

// Nombre raíz del proyecto
rootProject.name = "TecniApp_ICE"

// Módulo principal de la app
include(":app")
