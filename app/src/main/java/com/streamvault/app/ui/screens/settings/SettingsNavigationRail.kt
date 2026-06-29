package com.streamvault.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.streamvault.app.R

private data class SettingsNavEntry(
    val label: String,
    val icon: String,
    val accent: Color
)

@Composable
internal fun SettingsNavigationRail(
    selectedCategory: Int,
    focusRequester: FocusRequester,
    onCategorySelected: (Int) -> Unit,
    onSwitchProfile: () -> Unit = {},
    activeProfileName: String? = null
) {
    val entries = listOf(
        SettingsNavEntry(
            label = stringResource(R.string.settings_playback),
            icon = ">",
            accent = Color(0xFF9E8FFF)
        ),
        SettingsNavEntry(
            label = stringResource(R.string.settings_browsing),
            icon = "#",
            accent = Color(0xFF26A69A)
        ),
        SettingsNavEntry(
            label = stringResource(R.string.settings_privacy),
            icon = "L",
            accent = Color(0xFFFFB74D)
        ),
        SettingsNavEntry(
            label = stringResource(R.string.settings_recording_title),
            icon = "R",
            accent = Color(0xFFEF5350)
        ),
        SettingsNavEntry(
            label = stringResource(R.string.settings_backup_restore),
            icon = "B",
            accent = Color(0xFF42A5F5)
        ),
        SettingsNavEntry(
            label = "EPG Sources",
            icon = "E",
            accent = Color(0xFF66BB6A)
        ),
        SettingsNavEntry(
            label = stringResource(R.string.settings_about),
            icon = "i",
            accent = Color(0xFF78909C)
        )
    )

    LazyColumn(
        modifier = Modifier
            .width(236.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.25f)),
        contentPadding = PaddingValues(top = 76.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(entries) { index, entry ->
            SettingsNavItem(
                label = entry.label,
                badgeChar = entry.icon,
                accentColor = entry.accent,
                isSelected = selectedCategory == index,
                modifier = if (selectedCategory == index) Modifier.focusRequester(focusRequester) else Modifier,
                onClick = { onCategorySelected(index) }
            )
        }

        // Divisor visual antes do botão de trocar perfil
        item {
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Botão "Trocar Perfil" — não faz parte das categorias numeradas
        item {
            SettingsNavItem(
                label = if (activeProfileName != null) "Perfil: $activeProfileName" else "Trocar Perfil",
                badgeChar = "P",
                accentColor = Color(0xFF69A8FF),
                isSelected = false,
                onClick = onSwitchProfile
            )
        }
    }
}