package com.github.medavox.kotban.textaria

import javafx.beans.property.DoubleProperty
import javafx.scene.control.Skin
import javafx.scene.control.TextArea

class TextAria(text:String) : TextArea(text) {
    internal val ariaSkin:TextAriaSkin = TextAriaSkin(this)
    override fun createDefaultSkin(): Skin<*> {
        return ariaSkin
    }

    val displayTimeHeight: DoubleProperty = ariaSkin.displayTimeHeight

    override fun toString(): String {
        return super<TextArea>.toString()+ "skin: $ariaSkin"
    }
}