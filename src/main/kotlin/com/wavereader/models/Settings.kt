package com.wavereader.models

import kotlinx.serialization.Serializable

@Serializable
data class WaveAnimationControl(
    val enabled: Boolean = true,
    val speed: Double = 1.0,
    val amplitude: Int = 10,
    val frequency: Int = 2,
    val color: String = "#4a90e2"
)

@Serializable
data class Settings(
    val waveAnimationControl: WaveAnimationControl = WaveAnimationControl(),
    val showNotifications: Boolean = true,
    val going: Boolean = false,
    val toggleKey: String = "Alt+Shift+W"
) 