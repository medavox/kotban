package com.github.medavox.kotban.textaria

import com.sun.javafx.geom.transform.Affine3D
import javafx.scene.Node

object TextFieldBehavior {
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
}