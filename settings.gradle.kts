/** Foncit v2: SDK del teléfono móvil para Android con Linphone 100% integrado */
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        /** Linphone 100% integrado dentro del repositorio (sin descargas externas) */
        maven { url = uri("$rootDir/repo-linphone") }
    }
}
rootProject.name = "FoncitTelefono"
include(":foncit-telefono", ":ejemplo")
