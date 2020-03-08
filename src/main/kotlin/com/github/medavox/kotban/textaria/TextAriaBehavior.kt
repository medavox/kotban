/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.github.medavox.kotban.textaria

import com.sun.javafx.PlatformUtil
import com.sun.javafx.geom.transform.Affine3D
import com.sun.javafx.scene.control.behavior.*
import com.sun.javafx.scene.text.HitInfo
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.control.ContextMenu
import javafx.scene.input.ContextMenuEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.stage.Screen

import com.sun.javafx.PlatformUtil.isMac
import com.sun.javafx.PlatformUtil.isWindows
import javafx.scene.input.KeyCode.*
import javafx.scene.input.KeyEvent.KEY_PRESSED


/**
 * Text area behavior.
 */
class TextAriaBehavior(textArea:TextAria) : TextInputControlBehavior<TextAria>(textArea, TEXT_AREA_BINDINGS) {
    /**************************************************************************
     *                          Setup KeyBindings                             *
     *************************************************************************/
    private val skin:TextAriaSkin = textArea.ariaSkin
    companion object {
        private val TEXT_AREA_BINDINGS = mutableListOf<KeyBinding>().apply {
            add(KeyBinding(HOME, KEY_PRESSED, "LineStart")) // changed
            add(KeyBinding(END, KEY_PRESSED, "LineEnd")) // changed
            add(KeyBinding(UP, KEY_PRESSED, "PreviousLine")) // changed
            add(KeyBinding(KP_UP, KEY_PRESSED, "PreviousLine")) // changed
            add(KeyBinding(DOWN, KEY_PRESSED, "NextLine")) // changed
            add(KeyBinding(KP_DOWN, KEY_PRESSED, "NextLine")) // changed
            add(KeyBinding(PAGE_UP, KEY_PRESSED, "PreviousPage")) // new
            add(KeyBinding(PAGE_DOWN, KEY_PRESSED, "NextPage")) // new
            add(KeyBinding(ENTER, KEY_PRESSED, "InsertNewLine")) // changed
            add(KeyBinding(TAB, KEY_PRESSED, "TraverseOrInsertTab")) // changed

            add(KeyBinding(HOME, KEY_PRESSED, "SelectLineStart").shift()) // changed
            add(KeyBinding(END, KEY_PRESSED, "SelectLineEnd").shift()) // changed
            add(KeyBinding(UP, KEY_PRESSED, "SelectPreviousLine").shift()) // changed
            add(KeyBinding(KP_UP, KEY_PRESSED, "SelectPreviousLine").shift()) // changed
            add(KeyBinding(DOWN, KEY_PRESSED, "SelectNextLine").shift()) // changed
            add(KeyBinding(KP_DOWN, KEY_PRESSED, "SelectNextLine").shift()) // changed
            add(KeyBinding(PAGE_UP, KEY_PRESSED, "SelectPreviousPage").shift()) // new
            add(KeyBinding(PAGE_DOWN, KEY_PRESSED, "SelectNextPage").shift()) // new
            // Platform specific settings
            if (isMac()) {
                add(KeyBinding(LEFT, KEY_PRESSED, "LineStart").shortcut()) // changed
                add(KeyBinding(KP_LEFT, KEY_PRESSED, "LineStart").shortcut()) // changed
                add(KeyBinding(RIGHT, KEY_PRESSED, "LineEnd").shortcut()) // changed
                add(KeyBinding(KP_RIGHT, KEY_PRESSED, "LineEnd").shortcut()) // changed
                add(KeyBinding(UP, KEY_PRESSED, "Home").shortcut())
                add(KeyBinding(KP_UP, KEY_PRESSED, "Home").shortcut())
                add(KeyBinding(DOWN, KEY_PRESSED, "End").shortcut())
                add(KeyBinding(KP_DOWN, KEY_PRESSED, "End").shortcut())

                add(KeyBinding(LEFT, KEY_PRESSED, "SelectLineStartExtend").shift().shortcut()) // changed
                add(KeyBinding(KP_LEFT, KEY_PRESSED, "SelectLineStartExtend").shift().shortcut()) // changed
                add(KeyBinding(RIGHT, KEY_PRESSED, "SelectLineEndExtend").shift().shortcut()) // changed
                add(KeyBinding(KP_RIGHT, KEY_PRESSED, "SelectLineEndExtend").shift().shortcut()) // changed
                add(KeyBinding(UP, KEY_PRESSED, "SelectHomeExtend").shortcut().shift())
                add(KeyBinding(KP_UP, KEY_PRESSED, "SelectHomeExtend").shortcut().shift())
                add(KeyBinding(DOWN, KEY_PRESSED, "SelectEndExtend").shortcut().shift())
                add(KeyBinding(KP_DOWN, KEY_PRESSED, "SelectEndExtend").shortcut().shift())

                add(KeyBinding(UP, KEY_PRESSED, "ParagraphStart").alt())
                add(KeyBinding(KP_UP, KEY_PRESSED, "ParagraphStart").alt())
                add(KeyBinding(DOWN, KEY_PRESSED, "ParagraphEnd").alt())
                add(KeyBinding(KP_DOWN, KEY_PRESSED, "ParagraphEnd").alt())

                add(KeyBinding(UP, KEY_PRESSED, "SelectParagraphStart").alt().shift())
                add(KeyBinding(KP_UP, KEY_PRESSED, "SelectParagraphStart").alt().shift())
                add(KeyBinding(DOWN, KEY_PRESSED, "SelectParagraphEnd").alt().shift())
                add(KeyBinding(KP_DOWN, KEY_PRESSED, "SelectParagraphEnd").alt().shift())
            } else {
                add(KeyBinding(UP, KEY_PRESSED, "ParagraphStart").ctrl())
                add(KeyBinding(KP_UP, KEY_PRESSED, "ParagraphStart").ctrl())
                add(KeyBinding(DOWN, KEY_PRESSED, "ParagraphEnd").ctrl())
                add(KeyBinding(KP_DOWN, KEY_PRESSED, "ParagraphEnd").ctrl())
                add(KeyBinding(UP, KEY_PRESSED, "SelectParagraphStart").ctrl().shift())
                add(KeyBinding(KP_UP, KEY_PRESSED, "SelectParagraphStart").ctrl().shift())
                add(KeyBinding(DOWN, KEY_PRESSED, "SelectParagraphEnd").ctrl().shift())
                add(KeyBinding(KP_DOWN, KEY_PRESSED, "SelectParagraphEnd").ctrl().shift())
            }
            // Add the other standard key bindings in
            addAll(Utils.KEY_BINDINGS)
            // However, we want to consume other key press / release events too, for
            // things that would have been handled by the InputCharacter normally
            add(KeyBinding(null, KEY_PRESSED, "Consume"))
        }
    }


    private val contextMenu:ContextMenu = ContextMenu()
    private var tlFocus:TwoLevelFocusBehavior? = null

    /**************************************************************************
     * Constructors                                                           *
     *************************************************************************/

    init {
        if (IS_TOUCH_SUPPORTED) {
            contextMenu.styleClass.add("text-input-context-menu")
        }

        // Register for change events
        textArea.focusedProperty().addListener { _, _, _ ->
                // NOTE: The code in this method is *almost* and exact copy of what is in TextFieldBehavior.
                // The only real difference is that TextFieldBehavior selects all the text when the control
                // receives focus (when not gained by mouse click), whereas TextArea doesn't, and also the
                // TextArea doesn't lose selection on focus lost, whereas the TextField does.
                if (textArea.isFocused) {
                    if (PlatformUtil.isIOS()) {
                        // Special handling of focus on iOS is required to allow to
                        // control native keyboard, because native keyboard is popped-up only when native
                        // text component gets focus. When we have JFX keyboard we can remove this code
                        val bounds = textArea.boundsInParent
                        val w = bounds.width
                        val h = bounds.height
                        val trans:Affine3D = Utils.calculateNodeToSceneTransform(textArea)
                        val text = textArea.textProperty().valueSafe

                        // we need to display native text input component on the place where JFX component is drawn
                        // all parameters needed to do that are passed to native impl. here
                        textArea.scene.window.impl_getPeer().requestInput(text, Utils.TextInputTypes.TEXT_AREA.ordinal/*FIXME: this value might have changed*/, w, h,
                                trans.mxx, trans.mxy, trans.mxz, trans.mxt,
                                trans.myx, trans.myy, trans.myz, trans.myt,
                                trans.mzx, trans.mzy, trans.mzz, trans.mzt
                        )
                    }
                    if (!focusGainedByMouseClick) {
                        setCaretAnimating(true)
                    }
                } else {
//                    skin.hideCaret()
                    if (PlatformUtil.isIOS() && textArea.scene != null) {
                        // releasing the focus => we need to hide the native component and also native keyboard
                        textArea.scene.window.impl_getPeer().releaseInput()
                    }
                    focusGainedByMouseClick = false
                    setCaretAnimating(false)
                }
            }

        // Only add this if we're on an embedded platform that supports 5-button navigation
        if (com.sun.javafx.scene.control.skin.Utils.isTwoLevelFocus()) {
            tlFocus = TwoLevelFocusBehavior(textArea) // needs to be last.
        }
    }

    override fun dispose() {
        tlFocus?.dispose()
        super.dispose()
    }

    /**************************************************************************
     * Key handling implementation                                            *
     *************************************************************************/

    override fun callAction(name:String) {
        var ret = name
        var done = false

        if (control.isEditable) {
//            fnCaretAnim(false)
//            setCaretOpacity(1.0)
            isEditing = true
            done = true
            when (name) {
                "InsertNewLine" -> insertNewLine()
                "TraverseOrInsertTab" -> insertTab()
                else -> {
                    done = false
                }
            }
            isEditing = false
        }

        if (!done) {
            done = true
            when (name) {
                "LineStart" -> lineStart(false, false)
                "LineEnd" -> lineEnd(false, false)
                "SelectLineStart" -> lineStart(true, false)
                "SelectLineStartExtend" -> lineStart(true, true)
                "SelectLineEnd" -> lineEnd(true, false)
                "SelectLineEndExtend" -> lineEnd(true, true)
                "PreviousLine" -> skin.previousLine(false)
                "NextLine" -> skin.nextLine(false)
                "SelectPreviousLine" -> skin.previousLine(true)
                "SelectNextLine" -> skin.nextLine(true)
                "ParagraphStart" -> skin.paragraphStart(true, false)
                "ParagraphEnd" -> skin.paragraphEnd(true, isWindows(), false)
                "SelectParagraphStart" -> skin.paragraphStart(true, true)
                "SelectParagraphEnd" -> skin.paragraphEnd(true, isWindows(), true)
                "TraverseOrInsertTab" -> {
                    // RT-40312: Non-editable mode means traverse instead of insert.
                    ret = "TraverseNext"
                    done = false
                }
                else -> {
                    done = false
                }
            }
        }
//            fnCaretAnim(true)

        if (!done) {
            super.callAction(ret)
        }
    }

    private fun insertNewLine() = control.replaceSelection("\n")
    private fun insertTab() = control.replaceSelection("\t")
    override fun deleteChar(previous:Boolean) = skin.deleteChar(previous)

    override fun deleteFromLineStart() {
        val end:Int = control.caretPosition

        if (end > 0) {
            lineStart(false, false)
            val start:Int = control.caretPosition
            if (end > start) {
                replaceText(start, end, "")
            }
        }
    }

    private fun lineStart(select:Boolean, extendSelection:Boolean) = skin.lineStart(select, extendSelection)
    private fun lineEnd(select:Boolean, extendSelection:Boolean) = skin.lineEnd(select, extendSelection)

    override fun replaceText(start:Int, end:Int, txt:String) = control.replaceText(start, end, txt)

    /**
     * If the focus is gained via response to a mouse click, then we don't
     * want to select all the text even if selectOnFocus is true.
     */
    private var focusGainedByMouseClick:Boolean = false // TODO!!
    private var shiftDown:Boolean = false
    private var deferClick:Boolean = false

    override fun mousePressed(e:MouseEvent) {
        val textArea:TextAria = getControl()
        super.mousePressed(e)
        // We never respond to events if disabled
        if (!textArea.isDisabled) {
            // If the text field doesn't have focus, then we'll attempt to set
            // the focus and we'll indicate that we gained focus by a mouse
            // click, TODO which will then NOT honor the selectOnFocus variable
            // of the textInputControl
            if (!textArea.isFocused) {
                focusGainedByMouseClick = true
                textArea.requestFocus()
            }

            // stop the caret animation
            setCaretAnimating(false)
            // only if there is no selection should we see the caret
//            setCaretOpacity(if (textInputControl.dot == textInputControl.mark) then 1.0 else 0.0)

            // if the primary button was pressed
            if (e.button == MouseButton.PRIMARY && !(e.isMiddleButtonDown || e.isSecondaryButtonDown)) {
                val hit:HitInfo = skin.getIndex(e.x, e.y)
                val i = com.sun.javafx.scene.control.skin.Utils.getHitInsertionIndex(hit, textArea.textProperty().valueSafe)
//                 int i = skin.getInsertionPoint(e.getX(), e.getY())
                val anchor:Int = textArea.anchor
                val caretPosition:Int = textArea.caretPosition
                if (e.clickCount < 2 &&
                        (e.isSynthesized ||
                                (anchor != caretPosition &&
                                        ((i > anchor && i < caretPosition) || (i < anchor && i > caretPosition))))) {
                    // if there is a selection, then we will NOT handle the
                    // press now, but will defer until the release. If you
                    // select some text and then press down, we change the
                    // caret and wait to allow you to drag the text (TODO).
                    // When the drag concludes, then we handle the click

                    deferClick = true
                    //TODO start a timer such that after some millis we
                    // switch into text dragging mode, change the cursor
                    // to indicate the text can be dragged, etc.
                } else if (!(e.isControlDown || e.isAltDown || e.isShiftDown || e.isMetaDown || e.isShortcutDown)) {
                    when (e.clickCount) {
                        1 -> skin.positionCaret(hit, false, false)
                        2 -> mouseDoubleClick(hit)
                        3 -> mouseTripleClick(hit)
                    }
                } else if (e.isShiftDown && !(e.isControlDown || e.isAltDown || e.isMetaDown || e.isShortcutDown) && e.clickCount == 1) {
                    // didn't click inside the selection, so select
                    shiftDown = true
                    // if we are on mac os, then we will accumulate the
                    // selection instead of just moving the dot. This happens
                    // by figuring out past which (dot/mark) are extending the
                    // selection, and set the mark to be the other side and
                    // the dot to be the new position.
                    // everywhere else we just move the dot.
                    if (isMac()) {
                        textArea.extendSelection(i)
                    } else {
                        skin.positionCaret(hit, true, false)
                    }
                }
//                 skin.setForwardBias(hit.isLeading())
//                if (textInputControl.editable)
//                    displaySoftwareKeyboard(true)
            }
            if (contextMenu.isShowing) {
                contextMenu.hide()
            }
        }
    }

    override fun mouseDragged(e:MouseEvent) {
        // we never respond to events if disabled, but we do notify any onXXX
        // event listeners on the control
        if (!control.isDisabled && !e.isSynthesized) {
            if (e.button == MouseButton.PRIMARY &&
                    !(e.isMiddleButtonDown || e.isSecondaryButtonDown ||
                            e.isControlDown || e.isAltDown || e.isShiftDown || e.isMetaDown)) {
                skin.positionCaret(skin.getIndex(e.x, e.y), true, false)
            }
        }
        deferClick = false
    }

    override fun mouseReleased(e:MouseEvent) {
        super.mouseReleased(e)
        // we never respond to events if disabled, but we do notify any onXXX
        // event listeners on the control
        if (!control.isDisabled) {
            setCaretAnimating(false)
            if (deferClick) {
                deferClick = false
                skin.positionCaret(skin.getIndex(e.getX(), e.getY()), shiftDown, false)
                shiftDown = false
            }
            setCaretAnimating(true)
        }
    }

    override fun contextMenuRequested(e:ContextMenuEvent) {
        if (contextMenu.isShowing) {
            contextMenu.hide()
        } else if (control.contextMenu == null) {
            var screenX = e.screenX
            var screenY = e.screenY
            var sceneX = e.sceneX

            if (IS_TOUCH_SUPPORTED) {
                val menuPos:Point2D? =
                if (control.selection.length == 0) {
                    skin.positionCaret(skin.getIndex(e.x, e.y), false, false)
                    skin.menuPosition
                } else {
                    val menuPos = skin.menuPosition
                    if (menuPos != null && (menuPos.x <= 0 || menuPos.y <= 0)) {
                        skin.positionCaret(skin.getIndex(e.x, e.y), false, false)
                        skin.menuPosition
                    } else menuPos
                }

                if (menuPos != null) {
                    val p:Point2D = control.localToScene(menuPos)
                    val scene = control.scene
                    val window = scene.window
                    val location = Point2D(window.x + scene.x + p.x,
                            window.y + scene.y + p.y
                    )
                    screenX = location.x
                    sceneX = p.x
                    screenY = location.y
                }
            }

            skin.populateContextMenu(contextMenu)
            val menuWidth = contextMenu.prefWidth(-1.0)
            val menuX = screenX - if(IS_TOUCH_SUPPORTED) menuWidth / 2 else 0.0
            val currentScreen:Screen = com.sun.javafx.util.Utils.getScreenForPoint(screenX, 0.0)
            val bounds:Rectangle2D = currentScreen.bounds

            if (menuX < bounds.minX) {
                control.properties["CONTEXT_MENU_SCREEN_X"] = screenX
                control.properties["CONTEXT_MENU_SCENE_X"] = sceneX
                contextMenu.show(control, bounds.minX, screenY)
            } else if (screenX + menuWidth > bounds.maxX) {
                val leftOver = menuWidth - ( bounds.maxX - screenX)
                control.properties["CONTEXT_MENU_SCREEN_X"] = screenX
                control.properties["CONTEXT_MENU_SCENE_X"] = sceneX
                contextMenu.show(control, screenX - leftOver, screenY)
            } else {
                control.properties["CONTEXT_MENU_SCREEN_X"] = 0
                control.properties["CONTEXT_MENU_SCENE_X"] = 0
                contextMenu.show(control, menuX, screenY)
            }
        }

        e.consume()
    }

    override fun setCaretAnimating(play:Boolean) {
        skin.setCaretAnimating(play)
    }

    private fun mouseDoubleClick(hit:HitInfo) {
        control.previousWord()
        if (isWindows()) {
            control.selectNextWord()
        } else {
            control.selectEndOfNextWord()
        }
    }

    private fun mouseTripleClick(hit:HitInfo) {
        // select the line
        skin.paragraphStart(false, false)
        skin.paragraphEnd(false, isWindows(), true)
    }

    //    public function mouseWheelMove(e:MouseEvent):Void {
//        def textBox = bind skin.control as TextBox
//        // we never respond to events if disabled, but we do notify any onXXX
//        // event listeners on the control
//        if (not textBox.disabled) {
//            var rot = Math.abs(e.wheelRotation)
//            while (rot > 0) {
//                rot--
//                scrollText(e.wheelRotation > 0)
//            }
//        }
//    }

}
