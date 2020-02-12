package com.github.medavox.kotban

import com.sun.javafx.scene.text.HitInfo
import com.sun.javafx.scene.text.TextLayout
import com.sun.javafx.tk.Toolkit
import javafx.scene.text.Font
import javafx.scene.text.TextBoundsType
import java.text.BreakIterator

/**copied portions of package com.sun.javafx.scene.control.skin.Utils,
 * to support the import of TextAreaSkin */

@Suppress("DEPRECATION")
object Utils {
    /* Using TextLayout directly for simple text measurement.
     * Instead of restoring the TextLayout attributes to default values
     * (each renders the TextLayout unable to efficiently cache layout data).
     * It always sets all the attributes pertinent to calculation being performed.
     * Note that lineSpacing and boundsType are important when computing the height
     * but irrelevant when computing the width.
     *
     * Note: This code assumes that TextBoundsType#VISUAL is never used by controls.
     * */
    val layout =
        Toolkit.getToolkit().textLayoutFactory.createLayout()
    @JvmStatic
    fun getAscent(font: Font, boundsType: TextBoundsType): Double {
        layout.setContent("", font.impl_getNativeFont())
        layout.setWrapWidth(0f)
        layout.setLineSpacing(0f)
        if (boundsType == TextBoundsType.LOGICAL_VERTICAL_CENTER) {
            layout.setBoundsType(TextLayout.BOUNDS_CENTER)
        } else {
            layout.setBoundsType(0)
        }
        return (-layout.bounds.minY).toDouble()
    }
    @JvmStatic
    fun getLineHeight(font: Font, boundsType: TextBoundsType): Double {
        layout.setContent("", font.impl_getNativeFont())
        layout.setWrapWidth(0f)
        layout.setLineSpacing(0f)
        if (boundsType == TextBoundsType.LOGICAL_VERTICAL_CENTER) {
            layout.setBoundsType(TextLayout.BOUNDS_CENTER)
        } else {
            layout.setBoundsType(0)
        }
        // RT-37092: Use the line bounds specifically, to include font leading.
        return layout.lines[0].bounds.height.toDouble()
    }
    @JvmStatic
    fun computeTextWidth(
        font: Font,
        text: String?,
        wrappingWidth: Double
    ): Double {
        layout.setContent(text ?: "", font.impl_getNativeFont())
        layout.setWrapWidth(wrappingWidth.toFloat())
        return layout.bounds.width.toDouble()
    }
    @JvmStatic
    fun computeTextHeight(
        font: Font,
        text: String?,
        wrappingWidth: Double,
        boundsType: TextBoundsType
    ): Double {
        return computeTextHeight(font, text, wrappingWidth, 0.0, boundsType)
    }
    @JvmStatic
    fun computeTextHeight(
        font: Font,
        text: String?,
        wrappingWidth: Double,
        lineSpacing: Double,
        boundsType: TextBoundsType
    ): Double {
        layout.setContent(text ?: "", font.impl_getNativeFont())
        layout.setWrapWidth(wrappingWidth.toFloat())
        layout.setLineSpacing(lineSpacing.toFloat())
        if (boundsType == TextBoundsType.LOGICAL_VERTICAL_CENTER) {
            layout.setBoundsType(TextLayout.BOUNDS_CENTER)
        } else {
            layout.setBoundsType(0)
        }
        return layout.bounds.height.toDouble()
    }

    // Workaround for RT-26961. HitInfo.getInsertionIndex() doesn't skip
// complex character clusters / ligatures.
    private var charIterator: BreakIterator? = null
    @JvmStatic
    fun getHitInsertionIndex(hit: HitInfo, text: String?): Int {
        var charIndex = hit.charIndex
        if (text != null && !hit.isLeading) {
            if (charIterator == null) {
                charIterator = BreakIterator.getCharacterInstance()
            }
            charIterator!!.setText(text)
            val next = charIterator!!.following(charIndex)
            charIndex = if (next == BreakIterator.DONE) {
                hit.insertionIndex
            } else {
                next
            }
        }
        return charIndex
    }
}