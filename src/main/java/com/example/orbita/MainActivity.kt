package com.example.orbita

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070A18))
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.orbita_portada
                    ),
                    contentDescription = "Órbita, tu universo emocional",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            LaunchedEffect(Unit) {
                delay(2000)

                val destino =
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        InicioActivity::class.java
                    } else {
                        LoginActivity::class.java
                    }

                startActivity(
                    Intent(this@MainActivity, destino)
                )

                finish()
            }
        }
    }
}