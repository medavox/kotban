package com.github.medavox.kotban;

import com.sun.javafx.scene.text.HitInfo;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.tk.Toolkit;
import javafx.scene.text.Font;
import javafx.scene.text.TextBoundsType;

import java.text.BreakIterator;

/**copied portions of package com.sun.javafx.scene.control.skin.Utils,
 * to support the import of TextAreaSkin*/
public class Utils {
    /* Using TextLayout directly for simple text measurement.
     * Instead of restoring the TextLayout attributes to default values
     * (each renders the TextLayout unable to efficiently cache layout data).
     * It always sets all the attributes pertinent to calculation being performed.
     * Note that lineSpacing and boundsType are important when computing the height
     * but irrelevant when computing the width.
     *
     * Note: This code assumes that TextBoundsType#VISUAL is never used by controls.
     * */
    static final TextLayout layout = Toolkit.getToolkit().getTextLayoutFactory().createLayout();

    static double getAscent(Font font, TextBoundsType boundsType) {
        layout.setContent("", font.impl_getNativeFont());
        layout.setWrapWidth(0);
        layout.setLineSpacing(0);
        if (boundsType == TextBoundsType.LOGICAL_VERTICAL_CENTER) {
            layout.setBoundsType(TextLayout.BOUNDS_CENTER);
        } else {
            layout.setBoundsType(0);
        }
        return -layout.getBounds().getMinY();
    }

    static double getLineHeight(Font font, TextBoundsType boundsType) {
        layout.setContent("", font.impl_getNativeFont());
        layout.setWrapWidth(0);
        layout.setLineSpacing(0);
        if (boundsType == TextBoundsType.LOGICAL_VERTICAL_CENTER) {
            layout.setBoundsType(TextLayout.BOUNDS_CENTER);
        } else {
            layout.setBoundsType(0);
        }

        // RT-37092: Use the line bounds specifically, to include font leading.
        return layout.getLines()[0].getBounds().getHeight();
    }

    static double computeTextWidth(Font font, String text, double wrappingWidth) {
        layout.setContent(text != null ? text : "", font.impl_getNativeFont());
        layout.setWrapWidth((float)wrappingWidth);
        return layout.getBounds().getWidth();
    }

    static double computeTextHeight(Font font, String text, double wrappingWidth, TextBoundsType boundsType) {
        return computeTextHeight(font, text, wrappingWidth, 0, boundsType);
    }

    static double computeTextHeight(Font font, String text, double wrappingWidth, double lineSpacing, TextBoundsType boundsType) {
        layout.setContent(text != null ? text : "", font.impl_getNativeFont());
        layout.setWrapWidth((float)wrappingWidth);
        layout.setLineSpacing((float)lineSpacing);
        if (boundsType == TextBoundsType.LOGICAL_VERTICAL_CENTER) {
            layout.setBoundsType(TextLayout.BOUNDS_CENTER);
        } else {
            layout.setBoundsType(0);
        }
        return layout.getBounds().getHeight();
    }

    // Workaround for RT-26961. HitInfo.getInsertionIndex() doesn't skip
    // complex character clusters / ligatures.
    private static BreakIterator charIterator = null;
    public static int getHitInsertionIndex(HitInfo hit, String text) {
        int charIndex = hit.getCharIndex();
        if (text != null && !hit.isLeading()) {
            if (charIterator == null) {
                charIterator = BreakIterator.getCharacterInstance();
            }
            charIterator.setText(text);
            int next = charIterator.following(charIndex);
            if (next == BreakIterator.DONE) {
                charIndex = hit.getInsertionIndex();
            } else {
                charIndex = next;
            }
        }
        return charIndex;
    }
}
