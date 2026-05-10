package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.focusflow.services.GlobalPin
import com.focusflow.ui.theme.*

/**
 * PinGateDialog
 *
 * Reusable PIN entry dialog that gates any destructive action.
 * Calls [onSuccess] if the PIN verifies, [onDismiss] if cancelled.
 */
@Composable
fun PinGateDialog(
    title: String = "PIN Required",
    subtitle: String = "Enter your GlobalPin to continue",
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin         by remember { mutableStateOf("") }
    var showPin     by remember { mutableStateOf(false) }
    var error       by remember { mutableStateOf(false) }
    var attempts    by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Purple80.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Purple80, modifier = Modifier.size(18.dp))
                }
                Text(title, color = OnSurface, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(subtitle, color = OnSurface2, style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value          = pin,
                    onValueChange  = { pin = it; error = false },
                    placeholder    = { Text("Enter PIN", color = OnSurface2) },
                    singleLine     = true,
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon   = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = OnSurface2,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    isError = error,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = if (error) Error else Purple80,
                        unfocusedBorderColor = if (error) Error else OnSurface2,
                        errorBorderColor     = Error
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error) {
                    Text(
                        if (attempts >= 3) "Incorrect PIN. Double check and try again."
                        else "Incorrect PIN.",
                        color = Error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (GlobalPin.verify(pin)) {
                        onSuccess()
                    } else {
                        error = true
                        attempts++
                        pin   = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                enabled = pin.isNotBlank()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurface2)
            }
        }
    )
}
