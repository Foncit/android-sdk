import java.io.File

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

/**
 * Linphone integrado: se descomprime el .aar de repo-linphone/ (viene en el mismo git) y se empaqueta dentro del .aar de Foncit.
 * Así el SDK trae todo y quien lo use no necesita repositorios ni dependencias de Linphone.
 */
val linphoneVersion = "5.5.39"
val linphoneAar = rootProject.file("repo-linphone/org/linphone/linphone-sdk-android/$linphoneVersion/linphone-sdk-android-$linphoneVersion.aar")
val linphoneDir = layout.buildDirectory.dir("linphone-integrado")
val extraerLinphone = tasks.register<Sync>("extraerLinphone") {
    inputs.file(linphoneAar)
    from(zipTree(linphoneAar)) {
        // El manifiesto de Linphone ya está copiado en el AndroidManifest.xml de este módulo
        exclude("AndroidManifest.xml", "R.txt", "META-INF/**")
    }
    into(linphoneDir)
}

// Todo lo que arma el SDK (JNI, assets, recursos, compilación) espera a que Linphone esté descomprimido
tasks.named("preBuild") { dependsOn(extraerLinphone) }

android {
    namespace = "com.foncit.telefono"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    sourceSets["main"].apply {
        // Librerías nativas (.so de las 4 arquitecturas), assets (gramáticas, sonidos, rootca) y recursos de Linphone
        jniLibs.srcDir(extraerLinphone.map { File(it.destinationDir, "jni") })
        assets.srcDir(extraerLinphone.map { File(it.destinationDir, "assets") })
        res.srcDir(extraerLinphone.map { File(it.destinationDir, "res") })
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    /** Motor de llamadas: Linphone va DENTRO de este SDK (clases + librerías nativas + recursos), como los xcframework en iOS.
     *  La app que importa el SDK no agrega nada de Linphone. */
    api(files(linphoneDir.map { it.file("classes.jar") }).builtBy(extraerLinphone))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

/** Para publicarlo: com.foncit:foncit-telefono:2.0.0 (un solo artefacto, con Linphone dentro) */
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.foncit"
                artifactId = "foncit-telefono"
                version = "2.0.0"
            }
        }
        repositories {
            maven {
                name = "Foncit"
                url = uri(layout.buildDirectory.dir("maven-foncit"))
            }
        }
    }
}
