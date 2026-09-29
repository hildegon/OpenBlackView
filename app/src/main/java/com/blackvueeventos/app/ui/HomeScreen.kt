package com.blackvueeventos.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackvueeventos.app.AppViewModel
import com.blackvueeventos.app.JobProgress
import com.blackvueeventos.app.storage.EventStorage

@Composable
fun HomeScreen(viewModel: AppViewModel, onSettings: () -> Unit, onLive: () -> Unit) {
    val context = LocalContext.current
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val readyToDelete by viewModel.readyToDelete.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(EventStorage(context).access()) }
    var explainAllFiles by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = EventStorage(context).access()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val writePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        access = EventStorage(context).access()
    }
    val allFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        access = EventStorage(context).access()
    }
    val tree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            EventStorage(context).persistTree(uri)
            access = EventStorage(context).access()
        }
    }
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (confirmDelete) {
        val count = readyToDelete.size
        val files = if (count == 1) "1 archivo" else "$count archivos"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar de la cámara") },
            text = {
                Text(
                    "Se borrarán $files que se acaban de descargar y comprobar. " +
                        "El resto de la tarjeta no se toca. No se puede deshacer. " +
                        "En muchos modelos (por ejemplo la DR590XP) el borrado por Wi‑Fi no existe: " +
                        "si falla, el registro lo dirá y el archivo seguirá en la tarjeta.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteFromCamera()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }

    if (explainAllFiles) {
        AlertDialog(
            onDismissRequest = { explainAllFiles = false },
            title = { Text("Acceso al almacenamiento") },
            text = {
                Text(
                    "Los vídeos se guardan en Almacenamiento interno/blackvue, " +
                        "eventos y parking en carpetas distintas, cada día en la suya. " +
                        "Android pide acceso a todos los archivos porque esa ruta está fuera de Fotos.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainAllFiles = false
                    allFiles.launch(EventStorage(context).allFilesIntent())
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { explainAllFiles = false }) { Text("Cancelar") }
            },
        )
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
                Column(Modifier.weight(1f)) {
                    Text("BlackVue Eventos", style = MaterialTheme.typography.headlineSmall, color = Paper)
                    Text("Cámaras BlackVue · solo red local", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onLive) {
                    Text("En vivo", color = Amber)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Amber)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Conecta el teléfono al Wi‑Fi de la cámara. Eventos en blackvue/eventos; parking, si está activado, en blackvue/parking. Modelos compatibles en Ajustes.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(access.label, color = if (access.ready) Paper else Danger, style = MaterialTheme.typography.bodySmall)
            if (!access.ready) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hace falta poder escribir en Almacenamiento interno/blackvue.",
                    color = Paper,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (access.needsWritePermission) {
                        OutlinedButton(
                            onClick = { writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Permitir almacenamiento")
                        }
                    }
                    if (access.needsAllFiles) {
                        OutlinedButton(
                            onClick = { explainAllFiles = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Acceso a todos los archivos")
                        }
                    }
                    if (access.needsTree) {
                        OutlinedButton(
                            onClick = { tree.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Elegir carpeta blackvue")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ActionButton(
                label = "Sincronizar cámara",
                enabled = !running,
                filled = true,
                onClick = { viewModel.sync() },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = !running && readyToDelete.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    if (readyToDelete.isEmpty()) {
                        "Borrar de la cámara"
                    } else {
                        "Borrar de la cámara (${readyToDelete.size})"
                    },
                )
            }
            progress?.let { current ->
                Spacer(Modifier.height(14.dp))
                JobProgressBar(current)
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancel() },
                        enabled = current.phase != "Cancelando…",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (current.phase == "Cancelando…") "Cancelando…" else "Cancelar")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Registro", color = Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            LogPane(lines, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val content: @Composable () -> Unit = { Text(label) }
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Amber,
                contentColor = InkText,
                disabledContainerColor = Amber.copy(alpha = 0.35f),
                disabledContentColor = InkText.copy(alpha = 0.7f),
            ),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Amber,
                disabledContentColor = Amber.copy(alpha = 0.4f),
            ),
        ) { content() }
    }
}

@Composable
private fun JobProgressBar(progress: JobProgress) {
    val known = progress.total > 0 && progress.fraction >= 0f
    val index = when {
        progress.total <= 0 -> 0
        progress.phase == "Completado" || progress.phase == "Descargando…" ->
            progress.done.coerceAtMost(progress.total)
        progress.file.isNotEmpty() -> (progress.done + 1).coerceAtMost(progress.total)
        else -> progress.done.coerceAtMost(progress.total)
    }
    val percent = if (known) "${(progress.fraction * 100).toInt()}%" else ""
    val count = if (progress.total > 0) "$index/${progress.total} archivos" else ""
    Text(progress.phase, color = Paper, style = MaterialTheme.typography.titleMedium)
    if (count.isNotEmpty() || percent.isNotEmpty()) {
        Text(
            listOf(count, percent).filter { it.isNotEmpty() }.joinToString(" · "),
            color = Amber,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (progress.file.isNotEmpty()) {
        Text(
            progress.file,
            color = Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    if (known) {
        LinearProgressIndicator(
            progress = { progress.fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Amber,
            trackColor = Line,
            drawStopIndicator = {},
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Amber,
            )
            Spacer(Modifier.size(10.dp))
            Text("Calculando el total…", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LogPane(lines: List<String>, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size, lines.lastOrNull()) {
        scroll.scrollTo(scroll.maxValue)
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .verticalScroll(scroll),
    ) {
        SelectionContainer {
            Text(
                text = if (lines.isEmpty()) {
                    "Aquí verás qué archivos se copian y cualquier error."
                } else {
                    lines.joinToString("\n")
                },
                color = if (lines.isEmpty()) Muted else Paper,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
