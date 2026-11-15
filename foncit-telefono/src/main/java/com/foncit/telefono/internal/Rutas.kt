package com.foncit.telefono.internal

import com.foncit.telefono.Ambiente

/** Rutas de los webservices de Foncit por ambiente (las mismas que COMPLEMENTOS/WEB_PHONE/V1/FUNCIONES_JS/Rutas.js) */
internal object Rutas {
    fun telefonoMovil(ambiente: Ambiente): String = when (ambiente) {
        Ambiente.PRODUCCION -> "https://webservices.foncit.com/V1/TelefonoMovilWS.php"
        Ambiente.PRUEBAS -> "https://stg-webservices.foncit.com/V1/TelefonoMovilWS.php"
    }

    /** Llave de acceso del webservice (TelefonoMovilWS) */
    const val LLAVE = "TMV8Q2K4XW9RZ7P"
}
