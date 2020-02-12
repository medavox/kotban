/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.github.medavox.kotban.textaria

import com.sun.javafx.scene.control.behavior.KeyBinding
import com.sun.javafx.scene.control.behavior.OptionalBoolean
import com.sun.javafx.PlatformUtil

import javafx.scene.input.KeyCode.*
import javafx.scene.input.KeyEvent.*

/**
 */
class TextInputControlBindings {
    companion object {
        @JvmField
        val BINDINGS: List<KeyBinding> = listOf(
            // caret movement
            KeyBinding(RIGHT, KEY_PRESSED, "Right"),
            KeyBinding(KP_RIGHT, KEY_PRESSED, "Right"),
            KeyBinding(LEFT, KEY_PRESSED, "Left"),
            KeyBinding(KP_LEFT, KEY_PRESSED, "Left"),
            KeyBinding(UP, KEY_PRESSED, "Home"),
            KeyBinding(KP_UP, KEY_PRESSED, "Home"),
            KeyBinding(HOME, KEY_PRESSED, "Home"),
            KeyBinding(DOWN, KEY_PRESSED, "End"),
            KeyBinding(KP_DOWN, KEY_PRESSED, "End"),
            KeyBinding(END, KEY_PRESSED, "End"),
            KeyBinding(ENTER, KEY_PRESSED, "Fire"),
            // deletion
            KeyBinding(BACK_SPACE, KEY_PRESSED, "DeletePreviousChar"),
            KeyBinding(DELETE, KEY_PRESSED, "DeleteNextChar"),
            // cut/copy/paste
            KeyBinding(CUT, KEY_PRESSED, "Cut"),
            KeyBinding(DELETE, KEY_PRESSED, "Cut").shift(),
            KeyBinding(COPY, KEY_PRESSED, "Copy"),
            KeyBinding(PASTE, KEY_PRESSED, "Paste"),
            KeyBinding(INSERT, KEY_PRESSED, "Paste").shift(),// does this belong on mac?
            // selection
            KeyBinding(RIGHT, KEY_PRESSED, "SelectRight").shift(),
            KeyBinding(KP_RIGHT, KEY_PRESSED, "SelectRight").shift(),
            KeyBinding(LEFT, KEY_PRESSED, "SelectLeft").shift(),
            KeyBinding(KP_LEFT, KEY_PRESSED, "SelectLeft").shift(),
            KeyBinding(UP, KEY_PRESSED, "SelectHome").shift(),
            KeyBinding(KP_UP, KEY_PRESSED, "SelectHome").shift(),
            KeyBinding(DOWN, KEY_PRESSED, "SelectEnd").shift(),
            KeyBinding(KP_DOWN, KEY_PRESSED, "SelectEnd").shift(),

            KeyBinding(BACK_SPACE, KEY_PRESSED, "DeletePreviousChar").shift(),
            KeyBinding(DELETE, KEY_PRESSED, "DeleteNextChar").shift(),

            // Any other key press first goes to normal text input
            // Note this is KEY_TYPED because otherwise the character is not available in the event.
            KeyBinding(null, KEY_TYPED, "InputCharacter")
                .alt(OptionalBoolean.ANY)
                .shift(OptionalBoolean.ANY)
                .ctrl(OptionalBoolean.ANY)
                .meta(OptionalBoolean.ANY),

            // Traversal Bindings
            KeyBinding(TAB, "TraverseNext"),
            KeyBinding(TAB, "TraversePrevious").shift(),
            KeyBinding(TAB, "TraverseNext").ctrl(),
            KeyBinding(TAB, "TraversePrevious").shift().ctrl(),

            // The following keys are forwarded to the parent container
            KeyBinding(ESCAPE, "Cancel"),
            KeyBinding(F10, "ToParent")
        )+

        // platform specific settings
        if (PlatformUtil.isMac()) listOf<KeyBinding>(
                KeyBinding(HOME, KEY_PRESSED, "SelectHomeExtend").shift(),
                KeyBinding(END, KEY_PRESSED, "SelectEndExtend").shift(),

                KeyBinding(HOME, KEY_PRESSED, "Home").shortcut(),
                KeyBinding(END, KEY_PRESSED, "End").shortcut(),
                KeyBinding(LEFT, KEY_PRESSED, "Home").shortcut(),
                KeyBinding(KP_LEFT, KEY_PRESSED, "Home").shortcut(),
                KeyBinding(RIGHT, KEY_PRESSED, "End").shortcut(),
                KeyBinding(KP_RIGHT, KEY_PRESSED, "End").shortcut(),
                KeyBinding(LEFT, KEY_PRESSED, "LeftWord").alt(),
                KeyBinding(KP_LEFT, KEY_PRESSED, "LeftWord").alt(),
                KeyBinding(RIGHT, KEY_PRESSED, "RightWord").alt(),
                KeyBinding(KP_RIGHT, KEY_PRESSED, "RightWord").alt(),
                KeyBinding(DELETE, KEY_PRESSED, "DeleteNextWord").alt(),
                KeyBinding(BACK_SPACE, KEY_PRESSED, "DeletePreviousWord").alt(),
                KeyBinding(BACK_SPACE, KEY_PRESSED, "DeleteFromLineStart").shortcut(),
                KeyBinding(X, KEY_PRESSED, "Cut").shortcut(),
                KeyBinding(C, KEY_PRESSED, "Copy").shortcut(),
                KeyBinding(INSERT, KEY_PRESSED, "Copy").shortcut(),
                KeyBinding(V, KEY_PRESSED, "Paste").shortcut(),
                KeyBinding(HOME, KEY_PRESSED, "SelectHome").shift().shortcut(),
                KeyBinding(END, KEY_PRESSED, "SelectEnd").shift().shortcut(),
                KeyBinding(LEFT, KEY_PRESSED, "SelectHomeExtend").shift().shortcut(),
                KeyBinding(KP_LEFT, KEY_PRESSED, "SelectHomeExtend").shift().shortcut(),
                KeyBinding(RIGHT, KEY_PRESSED, "SelectEndExtend").shift().shortcut(),
                KeyBinding(KP_RIGHT, KEY_PRESSED, "SelectEndExtend").shift().shortcut(),
                KeyBinding(A, KEY_PRESSED, "SelectAll").shortcut(),
                KeyBinding(LEFT, KEY_PRESSED, "SelectLeftWord").shift().alt(),
                KeyBinding(KP_LEFT, KEY_PRESSED, "SelectLeftWord").shift().alt(),
                KeyBinding(RIGHT, KEY_PRESSED, "SelectRightWord").shift().alt(),
                KeyBinding(KP_RIGHT, KEY_PRESSED, "SelectRightWord").shift().alt(),
                KeyBinding(Z, KEY_PRESSED, "Undo").shortcut(),
                KeyBinding(Z, KEY_PRESSED, "Redo").shift().shortcut()
        )
        else {
            listOf<KeyBinding>(
                KeyBinding(HOME, KEY_PRESSED, "SelectHome").shift(),
                KeyBinding(END, KEY_PRESSED, "SelectEnd").shift(),

                KeyBinding(HOME, KEY_PRESSED, "Home").ctrl(),
                KeyBinding(END, KEY_PRESSED, "End").ctrl(),
                KeyBinding(LEFT, KEY_PRESSED, "LeftWord").ctrl(),
                KeyBinding(KP_LEFT, KEY_PRESSED, "LeftWord").ctrl(),
                KeyBinding(RIGHT, KEY_PRESSED, "RightWord").ctrl(),
                KeyBinding(KP_RIGHT, KEY_PRESSED, "RightWord").ctrl(),
                KeyBinding(H, KEY_PRESSED, "DeletePreviousChar").ctrl(),
                KeyBinding(DELETE, KEY_PRESSED, "DeleteNextWord").ctrl(),
                KeyBinding(BACK_SPACE, KEY_PRESSED, "DeletePreviousWord").ctrl(),
                KeyBinding(X, KEY_PRESSED, "Cut").ctrl(),
                KeyBinding(C, KEY_PRESSED, "Copy").ctrl(),
                KeyBinding(INSERT, KEY_PRESSED, "Copy").ctrl(),
                KeyBinding(V, KEY_PRESSED, "Paste").ctrl(),
                KeyBinding(HOME, KEY_PRESSED, "SelectHome").ctrl().shift(),
                KeyBinding(END, KEY_PRESSED, "SelectEnd").ctrl().shift(),
                KeyBinding(LEFT, KEY_PRESSED, "SelectLeftWord").ctrl().shift(),
                KeyBinding(KP_LEFT, KEY_PRESSED, "SelectLeftWord").ctrl().shift(),
                KeyBinding(RIGHT, KEY_PRESSED, "SelectRightWord").ctrl().shift(),
                KeyBinding(KP_RIGHT, KEY_PRESSED, "SelectRightWord").ctrl().shift(),
                KeyBinding(A, KEY_PRESSED, "SelectAll").ctrl(),
                KeyBinding(BACK_SLASH, KEY_PRESSED, "Unselect").ctrl()
            )+ if (PlatformUtil.isLinux()) listOf<KeyBinding>(
                KeyBinding(Z, KEY_PRESSED, "Undo").ctrl(),
                KeyBinding(Z, KEY_PRESSED, "Redo").ctrl().shift()
            ) else listOf<KeyBinding>(  // Windows
                KeyBinding(Z, KEY_PRESSED, "Undo").ctrl(),
                KeyBinding(Y, KEY_PRESSED, "Redo").ctrl()
            )
        }

            // TODO XXX DEBUGGING ONLY
//        BINDINGS.add(KeyBinding(F4, "TraverseDebug").alt().ctrl().shift())
            /*DEBUG*//*if (PlatformImpl.isSupported(ConditionalFeature.VIRTUAL_KEYBOARD)) {
                KeyBinding(DIGIT9, "UseVK").ctrl().shift()
            }*/

    }
}
