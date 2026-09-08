package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BentoColorScheme = darkColorScheme(
  primary = BentoHero,
  onPrimary = BentoHeroText,
  secondary = BentoPurpleMid,
  onSecondary = BentoTextMain,
  tertiary = BentoGold,
  background = BentoBg,
  surface = BentoCard,
  surfaceVariant = BentoCardActive,
  outline = BentoBorder,
  onBackground = BentoTextMain,
  onSurface = BentoTextMain
)

@Composable
fun CyberQuestTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = BentoColorScheme,
    typography = Typography,
    content = content
  )
}


