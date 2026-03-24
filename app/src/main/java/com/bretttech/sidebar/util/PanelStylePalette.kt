package com.bretttech.sidebar.util

import android.graphics.Color
import androidx.core.graphics.ColorUtils

data class PanelStyleColors(
    val panelBackground: Int,
    val panelStroke: Int,
    val handleBackground: Int,
    val handleStroke: Int,
    val labelColor: Int,
    val mutedColor: Int
)

object PanelStylePalette {

    data class ToneOption(
        val name: String,
        val color: Int
    )

    val customToneOptions: List<ToneOption> = listOf(
        ToneOption("Slate", Color.parseColor("#4C5D73")),
        ToneOption("Ocean", Color.parseColor("#31516F")),
        ToneOption("Forest", Color.parseColor("#3E5A4D")),
        ToneOption("Olive", Color.parseColor("#6A7051")),
        ToneOption("Lime", Color.parseColor("#7A8A3A")),
        ToneOption("Moss", Color.parseColor("#556B44")),
        ToneOption("Rosewood", Color.parseColor("#6B4B56")),
        ToneOption("Red", Color.parseColor("#7A4747")),
        ToneOption("Berry", Color.parseColor("#77465E")),
        ToneOption("Plum", Color.parseColor("#5C4F72")),
        ToneOption("Purple", Color.parseColor("#66508A")),
        ToneOption("Iris", Color.parseColor("#5564A0")),
        ToneOption("Clay", Color.parseColor("#7B5B49")),
        ToneOption("Amber", Color.parseColor("#8A6940")),
        ToneOption("Teal", Color.parseColor("#3F6B6E")),
        ToneOption("Steel", Color.parseColor("#56616B"))
    )

    fun forCustomBase(baseColor: Int): PanelStyleColors {
        val panelBackground = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(baseColor, Color.BLACK, 0.34f),
            0xF2
        )
        val handleBackground = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(baseColor, Color.WHITE, 0.18f),
            0xEE
        )
        val labelColor = if (ColorUtils.calculateLuminance(panelBackground) > 0.52) {
            Color.parseColor("#FF1D232B")
        } else {
            Color.WHITE
        }
        val mutedBase = if (labelColor == Color.WHITE) {
            ColorUtils.blendARGB(Color.WHITE, handleBackground, 0.38f)
        } else {
            ColorUtils.blendARGB(Color.parseColor("#FF1D232B"), panelBackground, 0.42f)
        }

        return PanelStyleColors(
            panelBackground = panelBackground,
            panelStroke = if (labelColor == Color.WHITE) 0x30FFFFFF else 0x26000000,
            handleBackground = handleBackground,
            handleStroke = if (labelColor == Color.WHITE) 0x55FFFFFF else 0x55000000,
            labelColor = labelColor,
            mutedColor = mutedBase
        )
    }
}