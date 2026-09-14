package com.mediara.app.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediara.app.R
import com.mediara.app.data.preferences.*
import com.mediara.app.data.repository.MediationRepository
import com.mediara.app.ui.theme.*
import kotlinx.coroutines.launch

// Map accent id -> preview swatch colour
private val accentSwatches = mapOf(
    AccentOption.INDIGO to MediaraAccents.Indigo.lightPrimary,
    AccentOption.TEAL to MediaraAccents.Teal.lightPrimary,
    AccentOption.NAVY to MediaraAccents.Navy.lightPrimary,
    AccentOption.FOREST to MediaraAccents.Forest.lightPrimary,
    AccentOption.ROSE to MediaraAccents.Rose.lightPrimary,
    AccentOption.PURPLE to MediaraAccents.Purple.lightPrimary,
    AccentOption.AMBER to MediaraAccents.Amber.lightPrimary,
    AccentOption.SLATE to MediaraAccents.Slate.lightPrimary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    repository: MediationRepository,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val settings by preferences.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Accent colour picker ---------------------------------------------------
            SettingsSectionHeader(stringResource(R.string.settings_accent))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AccentOption.entries.forEach { accent ->
                    val previewColor = accentSwatches[accent] ?: MediaraIndigoAccent
                    val selected = settings.accent == accent
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { preferences.update { s -> s.copy(accent = accent) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Font style ---------------------------------------------------------------
            SettingsSectionHeader(stringResource(R.string.settings_font))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    FontStyleOption.DEFAULT to stringResource(R.string.settings_font_default),
                    FontStyleOption.SERIF to stringResource(R.string.settings_font_serif),
                    FontStyleOption.MONOSPACE to stringResource(R.string.settings_font_mono),
                ).forEach { (font, label) ->
                    val selected = settings.fontStyle == font
                    FilterChip(
                        selected = selected,
                        onClick = { preferences.update { s -> s.copy(fontStyle = font) } },
                        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Dark mode ----------------------------------------------------------------
            SettingsSectionHeader(stringResource(R.string.settings_dark_mode))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DarkModeOption.SYSTEM to stringResource(R.string.settings_dark_system),
                    DarkModeOption.LIGHT to stringResource(R.string.settings_dark_light),
                    DarkModeOption.DARK to stringResource(R.string.settings_dark_dark),
                ).forEach { (mode, label) ->
                    val selected = settings.darkMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { preferences.update { s -> s.copy(darkMode = mode) } },
                        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Text size -----------------------------------------------------------------
            SettingsSectionHeader(stringResource(R.string.settings_text_size))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TextSizeOption.SMALL to stringResource(R.string.settings_text_small),
                    TextSizeOption.DEFAULT to stringResource(R.string.settings_text_default),
                    TextSizeOption.LARGE to stringResource(R.string.settings_text_large),
                ).forEach { (size, label) ->
                    val selected = settings.textSize == size
                    FilterChip(
                        selected = selected,
                        onClick = { preferences.update { s -> s.copy(textSize = size) } },
                        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Language picker ----------------------------------------------------------
            SettingsSectionHeader(stringResource(R.string.settings_language))
            Spacer(Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            val currentLang = LanguageOption.fromCode(settings.languageCode)
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { expanded = true }
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${currentLang.nativeName}  (${currentLang.englishName})",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LanguageOption.entries.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text("${lang.nativeName}  (${lang.englishName})") },
                        onClick = {
                            preferences.update { s -> s.copy(languageCode = lang.code) }
                            expanded = false
                            (context as? Activity)?.recreate()
                        },
                        trailingIcon = {
                            if (settings.languageCode == lang.code) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            // Divider & preview --------------------------------------------------------
            HorizontalDivider(Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_preview), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(previewHeroTitle(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    Text(previewHeroDesc(), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = {}, shape = RoundedCornerShape(10.dp)) {
                        Text(stringResource(R.string.action_continue), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // About --------------------------------------------------------------------
            SettingsSectionHeader(stringResource(R.string.settings_about))
            Spacer(Modifier.height(8.dp))
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_privacy_desc), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp))
                }
            }
            Spacer(Modifier.height(24.dp))

            // Logout -------------------------------------------------------------------
            OutlinedButton(
                onClick = {
                    scope.launch {
                        repository.logout()
                        onLogout()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_logout_button")
            ) {
                Icon(Icons.Default.ExitToApp, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_logout), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    )
}

// Preview section string keys (fall back to English if not translated)
@Composable
private fun previewHeroTitle() = stringResource(R.string.preview_hero_title)
@Composable
private fun previewHeroDesc() = stringResource(R.string.preview_hero_desc)