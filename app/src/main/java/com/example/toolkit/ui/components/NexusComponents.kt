package com.example.toolkit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.toolkit.ui.theme.AccentGradient
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.GlassBorder
import com.example.toolkit.ui.theme.MatrixBlack
import com.example.toolkit.ui.theme.MonoBody
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.SurfaceRaised
import com.example.toolkit.ui.theme.TerminalGray

/**
 * Shared design-system components — "premium SaaS" v2: glass cards (translucent
 * surface + hairline border) and a violet→cyan gradient accent on primary
 * actions, module badges and headline accents. Every screen builds its UI
 * from these primitives, so retheming happens here (and in ui/theme) rather
 * than in each of the 15 module screens individually.
 */

@Composable
fun NexusPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelGreen.copy(alpha = 0.82f), RoundedCornerShape(18.dp))
            .border(1.dp, GlassBorder.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GhostWhite,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentGradient)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun NexusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = BorderGreen,
            focusedTextColor = GhostWhite,
            unfocusedTextColor = GhostWhite,
            cursorColor = NeonGreen,
            focusedContainerColor = MatrixBlack,
            unfocusedContainerColor = MatrixBlack,
            focusedLabelColor = NeonGreen,
            unfocusedLabelColor = MuteGreen
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

/**
 * A dedicated search field with a leading search glyph and a clear button —
 * used for the dashboard's live module filter. State is hoisted by the
 * caller so it survives recomposition and lock/unlock cycles.
 */
@Composable
fun NexusSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = MuteGreen) },
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MuteGreen)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MuteGreen)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = BorderGreen,
            focusedTextColor = GhostWhite,
            unfocusedTextColor = GhostWhite,
            cursorColor = NeonGreen,
            focusedContainerColor = SurfaceRaised,
            unfocusedContainerColor = SurfaceRaised
        ),
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * Primary action button with the signature violet→cyan gradient fill.
 * Built as a plain clickable Box (rather than M3's [androidx.compose.material3.Button])
 * because Button's `colors` API only accepts a solid `containerColor`, not a [Brush].
 */
@Composable
fun NexusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)
    val isActive = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .then(
                if (isActive) Modifier.background(AccentGradient) else Modifier.background(BorderGreen)
            )
            .clickable(enabled = isActive, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
            Text(
                text,
                color = if (isActive) Color.Black else MuteGreen,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color = NeonGreen) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(100.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = GhostWhite) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            color = MuteGreen,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value.ifBlank { "—" },
            color = valueColor,
            style = MonoBody,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
fun ModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PanelGreen.copy(alpha = 0.82f)),
        border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = GhostWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(subtitle, color = TerminalGray, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MuteGreen
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = GhostWhite,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, color = TerminalGray, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentGradient)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun WarningBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlertAmber.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, AlertAmber.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.WarningAmber,
            contentDescription = null,
            tint = AlertAmber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = AlertAmber,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Gradient wordmark used for the app brand text on the dashboard and lock screen. */
@Composable
fun GradientBrandText(
    text: String,
    modifier: Modifier = Modifier,
    brush: Brush = AccentGradient
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.displayLarge.copy(brush = brush)
    )
}
