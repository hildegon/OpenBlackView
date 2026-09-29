package com.blackvueeventos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackvueeventos.app.AppViewModel

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val leave = {
        viewModel.flushSettings()
        onBack()
    }

    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            IconButton(onClick = leave) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Amber)
            }
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, color = Paper)
            Text(
                "La IP se guarda en el teléfono. 10.99.77.1 es la dirección habitual del Wi‑Fi de la cámara.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            if (!loaded) {
                Text("Cargando ajustes…", color = Muted)
            } else {
                OutlinedTextField(
                    value = settings.cameraHost,
                    onValueChange = { viewModel.updateSettings(settings.copy(cameraHost = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("IP de la cámara") },
                    supportingText = {
                        Text("Editable. Al volver a abrir la app se usa la última que guardaste.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Paper,
                        unfocusedTextColor = Paper,
                        focusedLabelColor = Amber,
                        unfocusedLabelColor = Muted,
                        focusedBorderColor = Amber,
                        unfocusedBorderColor = Line,
                        cursorColor = Amber,
                        focusedSupportingTextColor = Muted,
                        unfocusedSupportingTextColor = Muted,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = settings.downloadParking,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(downloadParking = it)) },
                    )
                    Spacer(Modifier.size(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Descargar parking", color = Paper)
                        Text(
                            "Tipo P, en blackvue/parking. Los eventos siguen en blackvue/eventos.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Descargas a la vez", color = Paper)
                Text(
                    "De 1 a 4. Si la cámara falla, se sigue de una en una.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (count in 1..4) {
                        val selected = settings.downloadConcurrency == count
                        Button(
                            onClick = {
                                viewModel.updateSettings(settings.copy(downloadConcurrency = count))
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Amber else Line,
                                contentColor = if (selected) InkText else Paper,
                            ),
                        ) {
                            Text(count.toString())
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = leave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = InkText),
            ) {
                Text("Guardar")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "BlackVue Eventos 1.0 · sin nube.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
