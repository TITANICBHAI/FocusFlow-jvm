package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.sp
import com.focusflow.services.GlobalPin
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PinGateStep { ENTER_PIN, FORGOT_CONFIRM, FORGOT_DONE }

/**
 * PinGateDialog
 *
 * Reusable PIN entry dialog that gates any destructive action.
 * Calls [onSuccess] if the PIN verifies, [onDismiss] if cancelled.
 *
 * Also provides a "Forgot PIN?" recovery path: the user types "RESET" to confirm,
 * which wipes the GlobalPin hash and allows them to set a new one.
 */
@Composable
fun PinGateDialog(
    title: String = "PIN Required",
    subtitle: String = "Enter your GlobalPin to continue",
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var step        by remember { mutableStateOf(PinGateStep.ENTER_PIN) }
    var pin         by remember { mutableStateOf("") }
    var showPin     by remember { mutableStateOf(false) }
    var error       by remember { mutableStateOf(false) }
    var attempts    by remember { mutableStateOf(0) }
    var resetPhrase by remember { mutableStateOf("") }
    val scope       = rememberCoroutineScope()
    // Compute once at dialog creation — avoids repeated DB reads on every recomposition
    val noPinSet    = remember { !GlobalPin.isSet() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface2,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (step == PinGateStep.FORGOT_CONFIRM) Warning.copy(alpha = 0.15f)
                            else Purple80.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (step == PinGateStep.FORGOT_CONFIRM) Icons.Default.Warning else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (step == PinGateStep.FORGOT_CONFIRM) Warning else Purple80,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    when (step) {
                        PinGateStep.ENTER_PIN     -> title
                        PinGateStep.FORGOT_CONFIRM -> "Reset PIN"
                        PinGateStep.FORGOT_DONE    -> "PIN Cleared"
                    },
                    color = OnSurface, fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            when (step) {
                PinGateStep.ENTER_PIN -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (noPinSet) "No PIN is set — click Confirm to continue." else subtitle,
                            color = OnSurface2,
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (!noPinSet) {
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
                        }

                        if (error) {
                            Text(
                                if (attempts >= 3) "Incorrect PIN. Double check and try again."
                                else "Incorrect PIN.",
                                color = Error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (attempts >= 2) {
                            TextButton(
                                onClick = { step = PinGateStep.FORGOT_CONFIRM },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "Forgot PIN?",
                                    color = Warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                PinGateStep.FORGOT_CONFIRM -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Warning.copy(alpha = 0.08f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Warning, modifier = Modifier.size(16.dp))
                            Text(
                                "This will permanently clear your GlobalPin. All settings will remain, but the PIN lock will be removed. You can set a new PIN afterwards.",
                                color = OnSurface2,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Text(
                            "Type RESET below to confirm:",
                            color = OnSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value         = resetPhrase,
                            onValueChange = { resetPhrase = it },
                            placeholder   = { Text("Type RESET", color = OnSurface2) },
                            singleLine    = true,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Warning,
                                unfocusedBorderColor = OnSurface2
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                PinGateStep.FORGOT_DONE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Your PIN has been cleared. You can set a new GlobalPin from Settings at any time.",
                            color = OnSurface2,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                PinGateStep.ENTER_PIN -> {
                    Button(
                        onClick = {
                            if (noPinSet || GlobalPin.verify(pin)) {
                                onSuccess()
                            } else {
                                error = true
                                attempts++
                                pin   = ""
                            }
                        },
                        colors  = ButtonDefaults.buttonColors(containerColor = Purple80),
                        enabled = noPinSet || pin.isNotBlank()
                    ) {
                        Text("Confirm")
                    }
                }

                PinGateStep.FORGOT_CONFIRM -> {
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { GlobalPin.resetWithoutPin() }
                                step = PinGateStep.FORGOT_DONE
                            }
                        },
                        colors  = ButtonDefaults.buttonColors(containerColor = Warning),
                        enabled = resetPhrase.trim() == "RESET"
                    ) {
                        Text("Clear PIN", color = Surface)
                    }
                }

                PinGateStep.FORGOT_DONE -> {
                    Button(
                        onClick = onDismiss,
                        colors  = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) {
                        Text("Done")
                    }
                }
            }
        },
        dismissButton = {
            when (step) {
                PinGateStep.ENTER_PIN -> {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = OnSurface2)
                    }
                }
                PinGateStep.FORGOT_CONFIRM -> {
                    TextButton(onClick = { step = PinGateStep.ENTER_PIN; resetPhrase = "" }) {
                        Text("Back", color = OnSurface2)
                    }
                }
                PinGateStep.FORGOT_DONE -> {}
            }
        }
    )
}
