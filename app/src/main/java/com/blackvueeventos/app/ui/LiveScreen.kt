package com.blackvueeventos.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackvueeventos.app.AppViewModel
import com.blackvueeventos.app.blackvue.CameraUnreachable
import com.blackvueeventos.app.blackvue.REAR_UNAVAILABLE
import com.blackvueeventos.app.blackvue.streamLive
import com.blackvueeventos.app.net.CAMERA_UNREACHABLE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun LiveScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val copying by viewModel.running.collectAsStateWithLifecycle()
    var rear by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var onScreen by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            onScreen = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(settings.cameraHost, rear, attempt, onScreen, copying) {
        frame = null
        error = null
        if (!onScreen || copying) {
            connecting = false
            return@LaunchedEffect
        }
        connecting = true
        try {
            withContext(Dispatchers.IO) {
                streamLive(settings.cameraHost, rear) { jpeg ->
                    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return@streamLive
                    withContext(Dispatchers.Main) {
                        frame = bitmap
                        connecting = false
                        error = null
                    }
                }
            }
            if (isActive) {
                error = if (frame == null) {
                    if (rear) REAR_UNAVAILABLE else "La cámara no envió imagen."
                } else {
                    "La cámara cerró el vídeo en vivo."
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CameraUnreachable) {
            error = CAMERA_UNREACHABLE
        } catch (errorThrown: Exception) {
            error = if (rear) REAR_UNAVAILABLE else (errorThrown.message ?: "No se pudo abrir el vídeo en vivo.")
        } finally {
            connecting = false
        }
    }

    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Amber)
                }
                Column(Modifier.weight(1f)) {
                    Text("En vivo", style = MaterialTheme.typography.headlineSmall, color = Paper)
                    Text(settings.cameraHost, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "Se corta al salir de esta pantalla, para no ocupar el Wi‑Fi de la cámara.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (choice in listOf(false to "Frente", true to "Trasera")) {
                    val selected = rear == choice.first
                    Button(
                        onClick = { rear = choice.first },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Amber else Line,
                            contentColor = if (selected) InkText else Paper,
                        ),
                    ) {
                        Text(choice.second)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (copying) {
                Text(
                    "Hay una copia en curso. El directo espera a que termine.",
                    color = Paper,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (error != null) {
                Text(error!!, color = Danger, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Panel, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val current = frame
                if (current != null) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = if (rear) "Vídeo en vivo, trasera" else "Vídeo en vivo, frente",
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else if (connecting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Amber)
                        Spacer(Modifier.height(8.dp))
                        Text("Conectando…", color = Muted)
                    }
                }
            }
            if (error != null && !copying) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { attempt++ },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Reintentar")
                }
            }
        }
    }
}
