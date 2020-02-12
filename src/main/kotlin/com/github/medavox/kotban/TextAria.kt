package com.github.medavox.kotban

import com.sun.javafx.scene.control.skin.TextAreaSkin
import javafx.scene.control.Skin
import javafx.scene.control.TextArea

class TextAria : TextArea() {
    override fun createDefaultSkin(): Skin<*> {
        return OverrideSkin(this)
    }
    private inner class OverrideSkin(ta:TextArea):TextAreaSkin(ta) {
        override fun layoutChildren(contentX: Double, contentY: Double, contentWidth: Double, contentHeight: Double) {
            super.layoutChildren(contentX, contentY, contentWidth, contentHeight)
        }
    }
}