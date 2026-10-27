package com.dimadesu.lifestreamer.ui.datastore

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import com.dimadesu.lifestreamer.R
import com.google.gson.Gson
import com.google.gson.GsonBuilder

enum class DataStoreFormat {
    KEY_VALUE,
    JSON
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFE8F5E9),
    background = Color(0xFF121214),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF18181E),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF24242C),
    onSurfaceVariant = Color(0xFFB0B0B8),
    outline = Color(0x33FFFFFF)
)

/**
 * Top-level Jetpack Compose screen for inspecting and viewing DataStore entries.
 *
 * Supports live reactive updates, search filtering across key names and values,
 * instant switching between Key-Value and JSON views, selectable code viewer with
 * dual-axis scrollbars, and clipboard copy / share actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataStoreScreen(
    preferences: Preferences?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var formatMode by rememberSaveable { mutableStateOf(DataStoreFormat.KEY_VALUE) }

    val gson = remember { GsonBuilder().setPrettyPrinting().create() }

    val allEntries = remember(preferences) {
        preferences?.asMap()?.entries?.sortedBy { it.key.name } ?: emptyList()
    }

    val trimmedQuery = searchQuery.trim().lowercase()
    val filteredEntries = remember(allEntries, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                entry.key.name.lowercase().contains(trimmedQuery) ||
                    entry.value.toString().lowercase().contains(trimmedQuery)
            }
        }
    }

    val renderedText = remember(filteredEntries, formatMode, allEntries.size, trimmedQuery) {
        if (filteredEntries.isEmpty()) {
            ""
        } else when (formatMode) {
            DataStoreFormat.KEY_VALUE -> formatEntriesAsKeyValue(
                entries = filteredEntries,
                totalCount = allEntries.size,
                isFiltered = trimmedQuery.isNotEmpty()
            )
            DataStoreFormat.JSON -> formatEntriesAsJson(
                entries = filteredEntries,
                gson = gson
            )
        }
    }

    val summaryText = when {
        allEntries.isEmpty() -> stringResource(R.string.datastore_summary_empty)
        trimmedQuery.isEmpty() -> stringResource(R.string.datastore_summary_keys, allEntries.size)
        else -> stringResource(R.string.datastore_summary_filtered, filteredEntries.size, allEntries.size)
    }

    MaterialTheme(colorScheme = DarkColorScheme) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color(0xFF121214),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.title_activity_datastore),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back_24),
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onCopy(renderedText) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_content_copy_24),
                                contentDescription = stringResource(R.string.datastore_copy),
                                tint = if (renderedText.isNotEmpty()) Color.White else Color(0x55FFFFFF)
                            )
                        }
                        IconButton(onClick = { onShare(renderedText) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_share_24),
                                contentDescription = stringResource(R.string.datastore_share),
                                tint = if (renderedText.isNotEmpty()) Color.White else Color(0x55FFFFFF)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF18181E)
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search filter and format controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A20))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.datastore_filter_hint),
                                color = Color(0x77FFFFFF),
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search_24),
                                contentDescription = null,
                                tint = Color(0x88FFFFFF),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_baseline_close_24),
                                        contentDescription = "Clear search",
                                        tint = Color(0xAAFFFFFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color(0x2BFFFFFF),
                            focusedContainerColor = Color(0xFF24242C),
                            unfocusedContainerColor = Color(0xFF222228),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF4CAF50)
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x1FFFFFFF)
                        ) {
                            Text(
                                text = summaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xCCFFFFFF),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        // Format selection toggle
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF25252E))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            FormatToggleButton(
                                text = stringResource(R.string.datastore_format_key_value),
                                selected = formatMode == DataStoreFormat.KEY_VALUE,
                                onClick = { formatMode = DataStoreFormat.KEY_VALUE }
                            )
                            FormatToggleButton(
                                text = stringResource(R.string.datastore_format_json),
                                selected = formatMode == DataStoreFormat.JSON,
                                onClick = { formatMode = DataStoreFormat.JSON }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

                // Main viewer content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when {
                        allEntries.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.datastore_empty),
                                    color = Color(0x88FFFFFF),
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        filteredEntries.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.datastore_no_matching_keys, searchQuery),
                                        color = Color(0x88FFFFFF),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { searchQuery = "" },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0x22FFFFFF),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Clear filter", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        else -> {
                            val verticalScroll = rememberScrollState()
                            val horizontalScroll = rememberScrollState()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF0F0F12),
                                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScrollbar(verticalScroll)
                                        .horizontalScrollbar(horizontalScroll)
                                ) {
                                    SelectionContainer {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(verticalScroll)
                                                .horizontalScroll(horizontalScroll)
                                                .padding(16.dp)
                                        ) {
                                            Text(
                                                text = renderedText,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                color = Color(0xFFE2E8F0)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF4CAF50) else Color.Transparent,
        label = "format_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0x88FFFFFF),
        label = "format_text"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

private fun formatEntriesAsKeyValue(
    entries: List<Map.Entry<Preferences.Key<*>, Any>>,
    totalCount: Int,
    isFiltered: Boolean
): String {
    val header = if (isFiltered) {
        "// DataStore Entries: ${entries.size} (filtered from $totalCount)\n\n"
    } else {
        "// DataStore Entries: ${entries.size}\n\n"
    }

    val body = entries.joinToString("\n") { entry ->
        val keyName = entry.key.name
        val typeName = entry.value::class.java.simpleName
        val formattedValue = when (val value = entry.value) {
            is String -> "\"$value\""
            is Set<*> -> value.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            else -> value.toString()
        }
        "$keyName = $formattedValue  ($typeName)"
    }

    return header + body
}

private fun formatEntriesAsJson(
    entries: List<Map.Entry<Preferences.Key<*>, Any>>,
    gson: Gson
): String {
    val map = linkedMapOf<String, Any>()
    for (entry in entries) {
        map[entry.key.name] = entry.value
    }
    return try {
        gson.toJson(map)
    } catch (e: Exception) {
        map.toString()
    }
}

/**
 * Sleek vertical scrollbar rendered on the right edge of a container.
 */
private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    color: Color = Color(0x66FFFFFF),
    width: Dp = 3.dp
): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val maxVal = scrollState.maxValue
        if (maxVal > 0) {
            val totalHeight = size.height
            val visibleRatio = (totalHeight / (totalHeight + maxVal)).coerceIn(0.15f, 1f)
            val thumbHeight = totalHeight * visibleRatio
            val scrollRatio = scrollState.value.toFloat() / maxVal.toFloat()
            val thumbOffset = (totalHeight - thumbHeight) * scrollRatio
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - width.toPx(), thumbOffset),
                size = Size(width.toPx(), thumbHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
)

/**
 * Sleek horizontal scrollbar rendered on the bottom edge of a container.
 */
private fun Modifier.horizontalScrollbar(
    scrollState: ScrollState,
    color: Color = Color(0x66FFFFFF),
    height: Dp = 3.dp
): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val maxVal = scrollState.maxValue
        if (maxVal > 0) {
            val totalWidth = size.width
            val visibleRatio = (totalWidth / (totalWidth + maxVal)).coerceIn(0.15f, 1f)
            val thumbWidth = totalWidth * visibleRatio
            val scrollRatio = scrollState.value.toFloat() / maxVal.toFloat()
            val thumbOffset = (totalWidth - thumbWidth) * scrollRatio
            drawRoundRect(
                color = color,
                topLeft = Offset(thumbOffset, size.height - height.toPx()),
                size = Size(thumbWidth, height.toPx()),
                cornerRadius = CornerRadius(height.toPx() / 2, height.toPx() / 2)
            )
        }
    }
)

@Preview(name = "DataStore Empty State", showBackground = true, backgroundColor = 0xFF121214)
@Composable
private fun DataStoreScreenEmptyPreview() {
    DataStoreScreen(
        preferences = null,
        onCopy = {},
        onShare = {},
        onBack = {}
    )
}
