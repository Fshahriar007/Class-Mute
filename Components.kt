@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.classmute

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = ClassMuteColors.TextSecondary,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
    )
}

@Composable
fun PillBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    var m = modifier.height(52.dp).clip(CircleShape).border(1.5.dp, ClassMuteColors.InputBorder, CircleShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun PillButton(
    text: String,
    container: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ClassMuteColors.DarkInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = ClassMuteColors.DarkInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
