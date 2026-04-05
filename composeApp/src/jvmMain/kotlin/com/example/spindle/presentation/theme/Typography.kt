package com.example.spindle.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SpindleTypography = Typography(
    displayLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 36.sp),
    displaySmall   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 22.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    headlineSmall  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    titleLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 13.sp),
    titleSmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 12.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 13.sp),
    bodySmall      = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 11.sp),
    labelLarge     = TextStyle(fontFamily = FontFamily.Monospace,  fontWeight = FontWeight.Medium,   fontSize = 13.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.Monospace,  fontWeight = FontWeight.Normal,   fontSize = 12.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.Monospace,  fontWeight = FontWeight.Normal,   fontSize = 10.sp),
)
