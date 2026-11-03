# Foncit Teléfono — SDK Android (Versión 2)

SDK completo y listo para producción que integra un teléfono VoIP empresarial (marcador telefónico, historial de recientes, control de llamadas entrantes y salientes) en cualquier app Android Kotlin (Jetpack Compose o Views).

Usa el motor de llamadas Linphone con **SIP seguro por TLS (puerto 5061)** y **voz cifrada SRTP**.

> **Novedad en V2**: Linphone viene **100% integrado dentro del proyecto**.  
> El cliente final ya **no** tiene que agregar repositorios externos de Linphone (`https://download.linphone.org/maven_repository`) en su `settings.gradle.kts`. Todo el motor ya viene empaquetado y listo para usar.

---

## 📋 Requisitos del Proyecto

* **Android minSdk 24** (Android 7.0+) o superior
* **compileSdk 35** (Android 15) o superior
* **Java 17** / **Kotlin 2.0+**
* Registro en la consola de [Foncit](https://foncit.com):
  * **Nombre de paquete** (`applicationId`) y **huella SHA-256** del certificado de firma (Debug o Release) registrados en *Foncit > Complementos > Plataformas*.

---

## 🚀 1. Agregar la librería a la app cliente

### `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Repositorio de Foncit (SDK con Linphone preintegrado):
        maven { url = uri("https://stg-android.foncit.com/") }
    }
}
```

### `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.foncit:telefono:2.0.0")
}
```

---

## ⚙️ 2. Permisos en `AndroidManifest.xml`

Asegúrate de que tu `AndroidManifest.xml` incluya los permisos necesarios para llamadas de voz y notificaciones:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
```

---

## 💻 3. Configurar en `Application.onCreate`

En la clase `Application` de tu app (o al iniciar):

```kotlin
import android.app.Application
import com.foncit.telefono.FoncitTelefono
import com.foncit.telefono.AjustesFoncit
import com.foncit.telefono.Ambiente

class MiApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configuración inicial del teléfono:
        FoncitTelefono.configurar(
            context = this,
            usuario = "ID_DE_TU_USUARIO", // Ej: AgCuentaUsuario
            ajustes = AjustesFoncit(
                ambiente = Ambiente.PRUEBAS,  // o Ambiente.PRODUCCION
                colorPrincipal = 0xFFE9791E.toInt()
            )
        )
    }
}
```

---

## 📱 4. Mostrar la Pantalla del Teléfono (Jetpack Compose)

En cualquier pantalla de tu aplicación, invoca la pantalla preconstruida con una sola línea:

```kotlin
import androidx.compose.runtime.Composable
import com.foncit.telefono.ui.FoncitTelefonoPantalla

@Composable
fun MiPantallaPrincipal() {
    // Renderiza teclado numérico, recientes y llamada en curso:
    FoncitTelefonoPantalla()
}
```

---

## 🎨 5. Personalización Visual y Temas

```kotlin
val temaPersonalizado = AjustesFoncit(
    ambiente = Ambiente.PRODUCCION,
    colorPrincipal = 0xFF007AFF.toInt(),
    oscuro = null // null = tema del sistema, true = siempre oscuro, false = siempre claro
)

FoncitTelefono.cambiarTema(temaPersonalizado)
```

---

## 📞 6. Métodos Programáticos (Opcional)

```kotlin
// Realizar una llamada directa a un número o extensión:
FoncitTelefono.llamar("+525512345678")

// Cambiar de extensión asignada:
FoncitTelefono.seleccionarExtension("101")

// Cerrar sesión del teléfono al salir de tu app:
FoncitTelefono.cerrarSesion()
```
