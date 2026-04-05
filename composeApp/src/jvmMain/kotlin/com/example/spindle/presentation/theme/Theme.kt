package com.example.spindle.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

@Composable
fun SpindleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpindleDarkColorScheme,
        typography  = SpindleTypography
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(NavyMid, NavyDeep, NavyDeepest),
                        radius = 1400f
                    )
                )
        ) {
            content()
        }
    }
}
