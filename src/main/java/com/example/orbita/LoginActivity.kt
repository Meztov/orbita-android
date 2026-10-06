package com.example.orbita

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orbita.ui.theme.OrbitaTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        setContent {
            OrbitaTheme {

                var cargando by remember {
                    mutableStateOf(false)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(15, 10, 35)),
                    contentAlignment = Alignment.Center
                ) {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Text(
                            text = "ÓRBITA",
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text = "Tu universo emocional",
                            color = Color.White,
                            fontSize = 18.sp
                        )

                        Spacer(
                            modifier = Modifier.height(40.dp)
                        )

                        Button(
                            onClick = {
                                cargando = true
                                iniciarGoogle()
                            },
                            modifier = Modifier
                                .width(280.dp)
                                .height(55.dp),
                            shape = RoundedCornerShape(30.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {

                            Text(
                                text = if (cargando)
                                    "Conectando..."
                                else
                                    "Continuar con Google",
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    private fun iniciarGoogle() {

        val opciones = GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(
                "572804211528-ur96u0eagpp9eq4drph1mnaj78boa2jj.apps.googleusercontent.com"
            )
            .requestEmail()
            .build()

        val clienteGoogle =
            GoogleSignIn.getClient(this, opciones)

        startActivityForResult(
            clienteGoogle.signInIntent,
            100
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == 100) {

            val tarea =
                GoogleSignIn.getSignedInAccountFromIntent(data)

            try {

                val cuenta =
                    tarea.getResult(
                        com.google.android.gms.common.api.ApiException::class.java
                    )

                val credencial =
                    GoogleAuthProvider.getCredential(
                        cuenta.idToken,
                        null
                    )

                auth.signInWithCredential(credencial)
                    .addOnCompleteListener(this) { resultado ->

                        if (resultado.isSuccessful) {

                            startActivity(
                                Intent(
                                    this,
                                    InicioActivity::class.java
                                )
                            )

                            finish()
                        }
                    }

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }
}