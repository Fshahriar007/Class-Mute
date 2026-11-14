@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.classmute

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun AddScheduleScreen(onBack: () -> Unit, onSave: (List<ClassSchedule>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var day by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) }
    var startMin by remember { mutableStateOf<Int?>(null) }
    var endMin by remember { mutableStateOf<Int?>(null) }
    var subject by remember { mutableStateOf("") }
    var pickerFor by remember { mutableStateOf<Int?>(null) } // 0 = start, 1 = end
    var dayMenu by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val scanned = remember { mutableStateListOf<ClassSchedule>() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scanning = true
            scope.launch {
                val text = runCatching { RoutineScanner.extractText(context, uri) }.getOrDefault("")
                val found = RoutineScanner.parse(text, day)
                scanned.clear()
                scanned.addAll(found)
                scanning = false
                if (found.isEmpty()) toast("Couldn't find class times. Try a clearer photo or add manually.")
            }
        }
    }

    fun trySave() {
        val s = startMin
        val e = endMin
        if (subject.isBlank()) { toast("Please enter a subject"); return }
        if (s == null || e == null) { toast("Pick start and end time"); return }
        if (e <= s) { toast("End time must be after start time"); return }
        onSave(
            listOf(
                ClassSchedule(
                    subject = subject.trim(),
                    dayOfWeek = day,
                    startHour = s / 60, startMinute = s % 60,
                    endHour = e / 60, endMinute = e % 60
                )
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = ClassMuteColors.TextPrimary)
            }
            Text("Schedule Input", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(12.dp))

        UploadCard(scanning = scanning) { filePicker.launch(arrayOf("image/*", "application/pdf")) }

        if (scanned.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            FieldLabel("FOUND ${scanned.size} CLASSES · TAP ONE TO REMOVE IT")
            scanned.toList().forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ClassMuteColors.SurfaceCard)
                        .clickable { scanned.remove(item) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${item.subject} · ${DAY_SHORT[item.dayOfWeek - 1]}", color = ClassMuteColors.TextPrimary)
                    Text(item.formattedTimeRange(), color = ClassMuteColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            PillButton("Add ${scanned.size} classes", ClassMuteColors.Lavender) { onSave(scanned.toList()) }
        }

        Spacer(Modifier.height(24.dp))
        FieldLabel("Day")
        Box {
            PillBox(Modifier.fillMaxWidth(), onClick = { dayMenu = true }) {
                Text(DAY_SHORT[day - 1], color = ClassMuteColors.TextPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = ClassMuteColors.TextSecondary)
            }
            DropdownMenu(
                expanded = dayMenu,
                onDismissRequest = { dayMenu = false },
                modifier = Modifier.background(ClassMuteColors.SurfaceCard)
            ) {
                DAY_FULL.forEachIndexed { index, name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { day = index + 1; dayMenu = false })
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        FieldLabel("Start/End Time")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillBox(Modifier.weight(1f), onClick = { pickerFor = 0 }) {
                Text(
                    startMin?.let { formatClock(it) } ?: "Start",
                    color = if (startMin == null) ClassMuteColors.TextSecondary else ClassMuteColors.TextPrimary
                )
            }
            PillBox(Modifier.weight(1f), onClick = { pickerFor = 1 }) {
                Text(
                    endMin?.let { formatClock(it) } ?: "End",
                    color = if (endMin == null) ClassMuteColors.TextSecondary else ClassMuteColors.TextPrimary
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        FieldLabel("Subject")
        PillBox(Modifier.fillMaxWidth()) {
            BasicTextField(
                value = subject,
                onValueChange = { subject = it },
                singleLine = true,
                textStyle = TextStyle(color = ClassMuteColors.TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(ClassMuteColors.Sage),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (subject.isEmpty()) Text("e.g. CSE-101", color = ClassMuteColors.TextSecondary)
                        inner()
                    }
                }
            )
        }

        Spacer(Modifier.height(28.dp))
        PillButton("Save Schedule", ClassMuteColors.Sage) { trySave() }
        Spacer(Modifier.height(24.dp))
    }

    pickerFor?.let { which ->
        val initial = (if (which == 0) startMin else endMin) ?: (if (which == 0) 9 * 60 else 10 * 60 + 30)
        ClassTimePickerDialog(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            onDismiss = { pickerFor = null },
            onConfirm = { h, m ->
                if (which == 0) startMin = h * 60 + m else endMin = h * 60 + m
                pickerFor = null
            }
        )
    }
}

@Composable
fun UploadCard(scanning: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(ClassMuteColors.Lavender, ClassMuteColors.Sage)), shape)
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFFF4F4F5).copy(alpha = 0.7f),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
            }
            .clickable(enabled = !scanning, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (scanning) {
                CircularProgressIndicator(color = ClassMuteColors.DarkInk, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(10.dp))
                Text("Reading your routine…", color = ClassMuteColors.DarkInk, fontWeight = FontWeight.Medium)
            } else {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = ClassMuteColors.DarkInk, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(8.dp))
                Text("Upload Routine", color = ClassMuteColors.DarkInk, fontWeight = FontWeight.Medium)
                Text("(Image/PDF)", color = ClassMuteColors.DarkInk.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ClassTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ClassMuteColors.SurfaceCard,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK", color = ClassMuteColors.Sage) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ClassMuteColors.TextSecondary) }
        },
        text = { TimePicker(state = state) }
    )
}
