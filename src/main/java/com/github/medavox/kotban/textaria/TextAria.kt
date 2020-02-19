package com.github.medavox.kotban.textaria

import javafx.beans.property.DoubleProperty
import javafx.scene.control.Skin
import javafx.scene.control.TextArea

class TextAria(text:String, @JvmField val printDebugOutput:Boolean = false) : TextArea(text) {
    private val taSkin:TextAriaSkin = TextAriaSkin(this)
    override fun createDefaultSkin(): Skin<*> {
        return taSkin
    }

    fun getTaeFuck(width:Double):Double = taSkin.getTaeFuck(width)
    val doubleBinding: DoubleProperty = taSkin.doubleBinding
}