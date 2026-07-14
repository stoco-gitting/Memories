package com.lucas.album.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lucas.album.R
import com.lucas.album.data.local.PhotoLayerEntity

private const val CAPTION_MAX_LENGTH = 80

@Composable
fun CaptionEditor(layer: PhotoLayerEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(layer.caption.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= CAPTION_MAX_LENGTH) text = it },
                placeholder = { Text(stringResource(R.string.caption_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
