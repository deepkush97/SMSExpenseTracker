package com.smsexpensetracker.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring

object AppAnimation {
    fun <T> spring(): AnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 400f)
    fun <T> softSpring(): AnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = 300f)
}
