package com.foncit.ejemplo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.foncit.telefono.AjustesFoncit
import com.foncit.telefono.Ambiente
import com.foncit.telefono.FoncitTelefono
import com.foncit.telefono.ui.FoncitTelefonoPantalla

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /** En la app real: el identificador del usuario que inició sesión en tu app (el mismo que manda tu panel en UsuariosExternos) */
        FoncitTelefono.configurar(this, USUARIO_DE_PRUEBA, AjustesFoncit(ambiente = Ambiente.PRUEBAS))

        /** La línea que pone todo el teléfono */
        setContent { FoncitTelefonoPantalla() }
    }

    companion object {
        /** Id de un usuario externo que ya llegó del panel y tiene extensiones asignadas */
        const val USUARIO_DE_PRUEBA = ""
    }
}
