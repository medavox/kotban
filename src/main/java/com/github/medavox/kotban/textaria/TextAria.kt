package com.github.medavox.kotban.textaria

import com.sun.javafx.scene.control.skin.TextAreaSkin
import javafx.scene.control.Skin
import javafx.scene.control.TextArea

class TextAria(text:String) : TextArea(text) {
    private val taSkin:TextAriaSkin = TextAriaSkin(this)
    override fun createDefaultSkin(): Skin<*> {
        return taSkin
    }

    fun getTaeFuck(width:Double):Double = taSkin.getTaeFuck(width)
}