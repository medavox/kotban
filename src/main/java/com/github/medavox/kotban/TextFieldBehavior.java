package com.github.medavox.kotban;

import com.sun.javafx.geom.transform.Affine3D;
import javafx.scene.Node;

public class TextFieldBehavior {
    static Affine3D calculateNodeToSceneTransform(Node node) {
        final Affine3D transform = new Affine3D();
        do {
            transform.preConcatenate(node.impl_getLeafTransform());
            node = node.getParent();
        } while (node != null);

        return transform;
    }
    // Enumeration of all types of text input that can be simulated on
    // touch device, such as iPad. Type is passed to native code and
    // native text component is shown. It's used as workaround for iOS
    // devices since keyboard control is not possible without native
    // text component being displayed
    enum TextInputTypes {
        TEXT_FIELD,
        PASSWORD_FIELD,
        EDITABLE_COMBO,
        TEXT_AREA;
    }
}
