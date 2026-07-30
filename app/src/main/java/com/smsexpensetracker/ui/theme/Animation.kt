package com.smsexpensetracker.ui.theme

import androidx.compose.animation.core.spring

object AppAnimation {
    val spring = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)
    val softSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)
}
