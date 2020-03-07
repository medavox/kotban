package com.github.medavox.kotban.textaria

import com.sun.javafx.PlatformUtil
import com.sun.javafx.geom.transform.Affine3D
import com.sun.javafx.scene.control.behavior.KeyBinding
import com.sun.javafx.scene.control.behavior.OptionalBoolean
import com.sun.javafx.scene.text.HitInfo
import com.sun.javafx.scene.text.TextLayout
import com.sun.javafx.tk.Toolkit
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.text.Font
import javafx.scene.text.TextBoundsType
import java.text.BreakIterator

/**Various private static methods copied from package com.sun.javafx.scene.control.skin,
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
    val layout = Toolkit.getToolkit().textLayoutFactory.createLayout()
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
        return computeTextHeight(
            font,
            text,
            wrappingWidth,
            0.0,
            boundsType
        )
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

    @JvmStatic
    fun calculateNodeToSceneTransform(nod: Node?): Affine3D {
        var node = nod
        val transform = Affine3D()
        do {
            transform.preConcatenate(node!!.impl_getLeafTransform())
            node = node.parent
        } while (node != null)
        return transform
    }

    /** Enumeration of all types of text input that can be simulated on
    touch device, such as iPad. Type is passed to native code and
    native text component is shown. It's used as workaround for iOS
    devices since keyboard control is not possible without native
    text component being displayed
     */
    internal enum class TextInputTypes {
        TEXT_FIELD, PASSWORD_FIELD, EDITABLE_COMBO, TEXT_AREA
    }

    @JvmField
    val KEY_BINDINGS: List<KeyBinding> = listOf(
        // caret movement
        KeyBinding(KeyCode.RIGHT, KeyEvent.KEY_PRESSED, "Right"),
        KeyBinding(KeyCode.KP_RIGHT, KeyEvent.KEY_PRESSED, "Right"),
        KeyBinding(KeyCode.LEFT, KeyEvent.KEY_PRESSED, "Left"),
        KeyBinding(KeyCode.KP_LEFT, KeyEvent.KEY_PRESSED, "Left"),
        KeyBinding(KeyCode.UP, KeyEvent.KEY_PRESSED, "Home"),
        KeyBinding(KeyCode.KP_UP, KeyEvent.KEY_PRESSED, "Home"),
        KeyBinding(KeyCode.HOME, KeyEvent.KEY_PRESSED, "Home"),
        KeyBinding(KeyCode.DOWN, KeyEvent.KEY_PRESSED, "End"),
        KeyBinding(KeyCode.KP_DOWN, KeyEvent.KEY_PRESSED, "End"),
        KeyBinding(KeyCode.END, KeyEvent.KEY_PRESSED, "End"),
        KeyBinding(KeyCode.ENTER, KeyEvent.KEY_PRESSED, "Fire"),
        // deletion
        KeyBinding(KeyCode.BACK_SPACE, KeyEvent.KEY_PRESSED, "DeletePreviousChar"),
        KeyBinding(KeyCode.DELETE, KeyEvent.KEY_PRESSED, "DeleteNextChar"),
        // cut/copy/paste
        KeyBinding(KeyCode.CUT, KeyEvent.KEY_PRESSED, "Cut"),
        KeyBinding(KeyCode.DELETE, KeyEvent.KEY_PRESSED, "Cut").shift(),
        KeyBinding(KeyCode.COPY, KeyEvent.KEY_PRESSED, "Copy"),
        KeyBinding(KeyCode.PASTE, KeyEvent.KEY_PRESSED, "Paste"),
        KeyBinding(KeyCode.INSERT, KeyEvent.KEY_PRESSED, "Paste").shift(),// does this belong on mac?
        // selection
        KeyBinding(KeyCode.RIGHT, KeyEvent.KEY_PRESSED, "SelectRight").shift(),
        KeyBinding(KeyCode.KP_RIGHT, KeyEvent.KEY_PRESSED, "SelectRight").shift(),
        KeyBinding(KeyCode.LEFT, KeyEvent.KEY_PRESSED, "SelectLeft").shift(),
        KeyBinding(KeyCode.KP_LEFT, KeyEvent.KEY_PRESSED, "SelectLeft").shift(),
        KeyBinding(KeyCode.UP, KeyEvent.KEY_PRESSED, "SelectHome").shift(),
        KeyBinding(KeyCode.KP_UP, KeyEvent.KEY_PRESSED, "SelectHome").shift(),
        KeyBinding(KeyCode.DOWN, KeyEvent.KEY_PRESSED, "SelectEnd").shift(),
        KeyBinding(KeyCode.KP_DOWN, KeyEvent.KEY_PRESSED, "SelectEnd").shift(),

        KeyBinding(KeyCode.BACK_SPACE, KeyEvent.KEY_PRESSED, "DeletePreviousChar").shift(),
        KeyBinding(KeyCode.DELETE, KeyEvent.KEY_PRESSED, "DeleteNextChar").shift(),

        // Any other key press first goes to normal text input
        // Note this is KEY_TYPED because otherwise the character is not available in the event.
        KeyBinding(null, KeyEvent.KEY_TYPED, "InputCharacter")
            .alt(OptionalBoolean.ANY)
            .shift(OptionalBoolean.ANY)
            .ctrl(OptionalBoolean.ANY)
            .meta(OptionalBoolean.ANY),

        // Traversal Bindings
        KeyBinding(KeyCode.TAB, "TraverseNext"),
        KeyBinding(KeyCode.TAB, "TraversePrevious").shift(),
        KeyBinding(KeyCode.TAB, "TraverseNext").ctrl(),
        KeyBinding(KeyCode.TAB, "TraversePrevious").shift().ctrl(),

        // The following keys are forwarded to the parent container
        KeyBinding(KeyCode.ESCAPE, "Cancel"),
        KeyBinding(KeyCode.F10, "ToParent")
    )+
    // platform specific settings
    if (PlatformUtil.isMac()) listOf<KeyBinding>(
        KeyBinding(KeyCode.HOME, KeyEvent.KEY_PRESSED, "SelectHomeExtend").shift(),
        KeyBinding(KeyCode.END, KeyEvent.KEY_PRESSED, "SelectEndExtend").shift(),

        KeyBinding(KeyCode.HOME, KeyEvent.KEY_PRESSED, "Home").shortcut(),
        KeyBinding(KeyCode.END, KeyEvent.KEY_PRESSED, "End").shortcut(),
        KeyBinding(KeyCode.LEFT, KeyEvent.KEY_PRESSED, "Home").shortcut(),
        KeyBinding(KeyCode.KP_LEFT, KeyEvent.KEY_PRESSED, "Home").shortcut(),
        KeyBinding(KeyCode.RIGHT, KeyEvent.KEY_PRESSED, "End").shortcut(),
        KeyBinding(KeyCode.KP_RIGHT, KeyEvent.KEY_PRESSED, "End").shortcut(),
        KeyBinding(KeyCode.LEFT, KeyEvent.KEY_PRESSED, "LeftWord").alt(),
        KeyBinding(KeyCode.KP_LEFT, KeyEvent.KEY_PRESSED, "LeftWord").alt(),
        KeyBinding(KeyCode.RIGHT, KeyEvent.KEY_PRESSED, "RightWord").alt(),
        KeyBinding(KeyCode.KP_RIGHT, KeyEvent.KEY_PRESSED, "RightWord").alt(),
        KeyBinding(KeyCode.DELETE, KeyEvent.KEY_PRESSED, "DeleteNextWord").alt(),
        KeyBinding(KeyCode.BACK_SPACE, KeyEvent.KEY_PRESSED, "DeletePreviousWord").alt(),
        KeyBinding(KeyCode.BACK_SPACE, KeyEvent.KEY_PRESSED, "DeleteFromLineStart").shortcut(),
        KeyBinding(KeyCode.X, KeyEvent.KEY_PRESSED, "Cut").shortcut(),
        KeyBinding(KeyCode.C, KeyEvent.KEY_PRESSED, "Copy").shortcut(),
        KeyBinding(KeyCode.INSERT, KeyEvent.KEY_PRESSED, "Copy").shortcut(),
        KeyBinding(KeyCode.V, KeyEvent.KEY_PRESSED, "Paste").shortcut(),
        KeyBinding(KeyCode.HOME, KeyEvent.KEY_PRESSED, "SelectHome").shift().shortcut(),
        KeyBinding(KeyCode.END, KeyEvent.KEY_PRESSED, "SelectEnd").shift().shortcut(),
        KeyBinding(KeyCode.LEFT, KeyEvent.KEY_PRESSED, "SelectHomeExtend").shift().shortcut(),
        KeyBinding(KeyCode.KP_LEFT, KeyEvent.KEY_PRESSED, "SelectHomeExtend").shift().shortcut(),
        KeyBinding(KeyCode.RIGHT, KeyEvent.KEY_PRESSED, "SelectEndExtend").shift().shortcut(),
        KeyBinding(KeyCode.KP_RIGHT, KeyEvent.KEY_PRESSED, "SelectEndExtend").shift().shortcut(),
        KeyBinding(KeyCode.A, KeyEvent.KEY_PRESSED, "SelectAll").shortcut(),
        KeyBinding(KeyCode.LEFT, KeyEvent.KEY_PRESSED, "SelectLeftWord").shift().alt(),
        KeyBinding(KeyCode.KP_LEFT, KeyEvent.KEY_PRESSED, "SelectLeftWord").shift().alt(),
        KeyBinding(KeyCode.RIGHT, KeyEvent.KEY_PRESSED, "SelectRightWord").shift().alt(),
        KeyBinding(KeyCode.KP_RIGHT, KeyEvent.KEY_PRESSED, "SelectRightWord").shift().alt(),
        KeyBinding(KeyCode.Z, KeyEvent.KEY_PRESSED, "Undo").shortcut(),
        KeyBinding(KeyCode.Z, KeyEvent.KEY_PRESSED, "Redo").shift().shortcut()
    )
    else {
        listOf<KeyBinding>(
            KeyBinding(KeyCode.HOME, KeyEvent.KEY_PRESSED, "SelectHome").shift(),
            KeyBinding(KeyCode.END, KeyEvent.KEY_PRESSED, "SelectEnd").shift(),

            KeyBinding(KeyCode.HOME, KeyEvent.KEY_PRESSED, "Home").ctrl(),
            KeyBinding(KeyCode.END, KeyEvent.KEY_PRESSED, "End").ctrl(),
            KeyBinding(KeyCode.LEFT, KeyEvent.KEY_PRESSED, "LeftWord").ctrl(),
            KeyBinding(KeyCode.KP_LEFT, KeyEvent.KEY_PRESSED, "LeftWord").ctrl(),
            KeyBinding(KeyCode.RIGHT, KeyEvent.KEY_PRESSED, "RightWord").ctrl(),
            KeyBinding(KeyCode.KP_RIGHT, KeyEvent.KEY_PRESSED, "RightWord").ctrl(),
            KeyBinding(KeyCode.H, KeyEvent.KEY_PRESSED, "DeletePreviousChar").ctrl(),
            KeyBinding(KeyCode.DELETE, KeyEvent.KEY_PRESSED, "DeleteNextWord").ctrl(),
            KeyBinding(KeyCode.BACK_SPACE, KeyEvent.KEY_PRESSED, "DeletePreviousWord").ctrl(),
            KeyBinding(KeyCode.X, KeyEvent.KEY_PRESSED, "Cut").ctrl(),
            KeyBinding(KeyCode.C, KeyEvent.KEY_PRESSED, "Copy").ctrl(),
            KeyBinding(KeyCode.INSERT, KeyEvent.KEY_PRESSED, "Copy").ctrl(),
            KeyBinding(KeyCode.V, KeyEvent.KEY_PRESSED, "Paste").ctrl(),
            KeyBinding(KeyCode.HOME, KeyEvent.KEY_PRESSED, "SelectHome").ctrl().shift(),
            KeyBinding(KeyCode.END, KeyEvent.KEY_PRESSED, "SelectEnd").ctrl().shift(),
            KeyBinding(KeyCode.LEFT, KeyEvent.KEY_PRESSED, "SelectLeftWord").ctrl().shift(),
            KeyBinding(KeyCode.KP_LEFT, KeyEvent.KEY_PRESSED, "SelectLeftWord").ctrl().shift(),
            KeyBinding(KeyCode.RIGHT, KeyEvent.KEY_PRESSED, "SelectRightWord").ctrl().shift(),
            KeyBinding(KeyCode.KP_RIGHT, KeyEvent.KEY_PRESSED, "SelectRightWord").ctrl().shift(),
            KeyBinding(KeyCode.A, KeyEvent.KEY_PRESSED, "SelectAll").ctrl(),
            KeyBinding(KeyCode.BACK_SLASH, KeyEvent.KEY_PRESSED, "Unselect").ctrl()
        )+ if (PlatformUtil.isLinux()) listOf<KeyBinding>(
            KeyBinding(KeyCode.Z, KeyEvent.KEY_PRESSED, "Undo").ctrl(),
            KeyBinding(KeyCode.Z, KeyEvent.KEY_PRESSED, "Redo").ctrl().shift()
        ) else listOf<KeyBinding>(  // Windows
            KeyBinding(KeyCode.Z, KeyEvent.KEY_PRESSED, "Undo").ctrl(),
            KeyBinding(KeyCode.Y, KeyEvent.KEY_PRESSED, "Redo").ctrl()
        )
    }
}