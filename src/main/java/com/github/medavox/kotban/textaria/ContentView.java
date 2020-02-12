package com.github.medavox.kotban.textaria;

import com.sun.javafx.scene.control.skin.TextInputControlSkin;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;

import java.util.List;

class ContentView extends Region {
    private final TextAriaSkin textAriaSkin;
    private final ScrollPane scrollPane;

    public ContentView(TextAriaSkin textAriaSkin, ScrollPane scrollPane) {
        this.textAriaSkin = textAriaSkin;
        this.scrollPane = scrollPane;
        getStyleClass().add("content");

        addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            textAriaSkin.getBehavior().mousePressed(event);
            event.consume();
        });

        addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            textAriaSkin.getBehavior().mouseReleased(event);
            event.consume();
        });

        addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            textAriaSkin.getBehavior().mouseDragged(event);
            event.consume();
        });
    }

    @Override protected ObservableList<Node> getChildren() {
        return super.getChildren();
    }

    @Override public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override protected double computePrefWidth(double height) {
        if (textAriaSkin.computedPrefWidth < 0) {
            double prefWidth = 0;

            for (Node node : textAriaSkin.paragraphNodes.getChildren()) {
                Text paragraphNode = (Text)node;
                prefWidth = Math.max(prefWidth,
                        Utils.computeTextWidth(paragraphNode.getFont(),
                                paragraphNode.getText(), 0));
            }

            prefWidth += snappedLeftInset() + snappedRightInset();

            Bounds viewPortBounds = scrollPane.getViewportBounds();
            textAriaSkin.computedPrefWidth = Math.max(prefWidth, (viewPortBounds != null) ? viewPortBounds.getWidth() : 0);
        }
        return textAriaSkin.computedPrefWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        if (width != textAriaSkin.widthForComputedPrefHeight) {
            textAriaSkin.invalidateMetrics();
            textAriaSkin.widthForComputedPrefHeight = width;
        }

        if (textAriaSkin.computedPrefHeight < 0) {
            double wrappingWidth;
            if (width == -1) {
                wrappingWidth = 0;
            } else {
                wrappingWidth = Math.max(width - (snappedLeftInset() + snappedRightInset()), 0);
            }

            double prefHeight = 0;

            for (Node node : textAriaSkin.paragraphNodes.getChildren()) {
                Text paragraphNode = (Text)node;
                prefHeight += Utils.computeTextHeight(
                        paragraphNode.getFont(),
                        paragraphNode.getText(),
                        wrappingWidth,
                        paragraphNode.getBoundsType());
            }

            prefHeight += snappedTopInset() + snappedBottomInset();

            Bounds viewPortBounds = scrollPane.getViewportBounds();
            textAriaSkin.computedPrefHeight = Math.max(prefHeight, (viewPortBounds != null) ? viewPortBounds.getHeight() : 0);
        }
        return textAriaSkin.computedPrefHeight;
    }

    @Override protected double computeMinWidth(double height) {
        if (textAriaSkin.computedMinWidth < 0) {
            double hInsets = snappedLeftInset() + snappedRightInset();
            textAriaSkin.computedMinWidth = Math.min(textAriaSkin.characterWidth + hInsets, computePrefWidth(height));
        }
        return textAriaSkin.computedMinWidth;
    }

    @Override protected double computeMinHeight(double width) {
        if (textAriaSkin.computedMinHeight < 0) {
            double vInsets = snappedTopInset() + snappedBottomInset();
            textAriaSkin.computedMinHeight = Math.min(textAriaSkin.lineHeight + vInsets, computePrefHeight(width));
        }
        return textAriaSkin.computedMinHeight;
    }

    @Override
    public void layoutChildren() {
        TextAria textArea = textAriaSkin.getSkinnable();
        double width = getWidth();

        // Lay out paragraphs
        final double topPadding = snappedTopInset();
        final double leftPadding = snappedLeftInset();

        double wrappingWidth = Math.max(width - (leftPadding + snappedRightInset()), 0);

        double y = topPadding;

        final List<Node> paragraphNodesChildren = textAriaSkin.paragraphNodes.getChildren();

        for (int i = 0; i < paragraphNodesChildren.size(); i++) {
            Node node = paragraphNodesChildren.get(i);
            Text paragraphNode = (Text)node;
            paragraphNode.setWrappingWidth(wrappingWidth);

            Bounds bounds = paragraphNode.getBoundsInLocal();
            paragraphNode.setLayoutX(leftPadding);
            paragraphNode.setLayoutY(y);

            y += bounds.getHeight();
        }

        if (textAriaSkin.promptNode != null) {
            textAriaSkin.promptNode.setLayoutX(leftPadding);
            textAriaSkin.promptNode.setLayoutY(topPadding + textAriaSkin.promptNode.getBaselineOffset());
            textAriaSkin.promptNode.setWrappingWidth(wrappingWidth);
        }

        // Update the selection
        IndexRange selection = textArea.getSelection();
        Bounds oldCaretBounds = textAriaSkin.caretPath.getBoundsInParent();

        textAriaSkin.selectionHighlightGroup.getChildren().clear();

        int caretPos = textArea.getCaretPosition();
        int anchorPos = textArea.getAnchor();

        if (TextInputControlSkin.SHOW_HANDLES) {
            // Install and resize the handles for caret and anchor.
            if (selection.getLength() > 0) {
                textAriaSkin.selectionHandle1.resize(textAriaSkin.selectionHandle1.prefWidth(-1),
                        textAriaSkin.selectionHandle1.prefHeight(-1));
                textAriaSkin.selectionHandle2.resize(textAriaSkin.selectionHandle2.prefWidth(-1),
                        textAriaSkin.selectionHandle2.prefHeight(-1));
            } else {
                textAriaSkin.caretHandle.resize(textAriaSkin.caretHandle.prefWidth(-1),
                        textAriaSkin.caretHandle.prefHeight(-1));
            }

            // Position the handle for the anchor. This could be handle1 or handle2.
            // Do this before positioning the actual caret.
            if (selection.getLength() > 0) {
                int paragraphIndex = paragraphNodesChildren.size();
                int paragraphOffset = textArea.getLength() + 1;
                Text paragraphNode = null;
                do {
                    paragraphNode = (Text)paragraphNodesChildren.get(--paragraphIndex);
                    paragraphOffset -= paragraphNode.getText().length() + 1;
                } while (anchorPos < paragraphOffset);

                textAriaSkin.updateTextNodeCaretPos(anchorPos - paragraphOffset);
                textAriaSkin.caretPath.getElements().clear();
                textAriaSkin.caretPath.getElements().addAll(paragraphNode.getImpl_caretShape());
                textAriaSkin.caretPath.setLayoutX(paragraphNode.getLayoutX());
                textAriaSkin.caretPath.setLayoutY(paragraphNode.getLayoutY());

                Bounds b = textAriaSkin.caretPath.getBoundsInParent();
                if (caretPos < anchorPos) {
                    textAriaSkin.selectionHandle2.setLayoutX(b.getMinX() - textAriaSkin.selectionHandle2.getWidth() / 2);
                    textAriaSkin.selectionHandle2.setLayoutY(b.getMaxY() - 1);
                } else {
                    textAriaSkin.selectionHandle1.setLayoutX(b.getMinX() - textAriaSkin.selectionHandle1.getWidth() / 2);
                    textAriaSkin.selectionHandle1.setLayoutY(b.getMinY() - textAriaSkin.selectionHandle1.getHeight() + 1);
                }
            }
        }

        {
            // Position caret
            int paragraphIndex = paragraphNodesChildren.size();
            int paragraphOffset = textArea.getLength() + 1;

            Text paragraphNode = null;
            do {
                paragraphNode = (Text)paragraphNodesChildren.get(--paragraphIndex);
                paragraphOffset -= paragraphNode.getText().length() + 1;
            } while (caretPos < paragraphOffset);

            textAriaSkin.updateTextNodeCaretPos(caretPos - paragraphOffset);

            textAriaSkin.caretPath.getElements().clear();
            textAriaSkin.caretPath.getElements().addAll(paragraphNode.getImpl_caretShape());

            textAriaSkin.caretPath.setLayoutX(paragraphNode.getLayoutX());

            // TODO: Remove this temporary workaround for RT-27533
            paragraphNode.setLayoutX(2 * paragraphNode.getLayoutX() - paragraphNode.getBoundsInParent().getMinX());

            textAriaSkin.caretPath.setLayoutY(paragraphNode.getLayoutY());
            if (oldCaretBounds == null || !oldCaretBounds.equals(textAriaSkin.caretPath.getBoundsInParent())) {
                textAriaSkin.scrollCaretToVisible();
            }
        }

        // Update selection fg and bg
        int start = selection.getStart();
        int end = selection.getEnd();
        for (int i = 0, max = paragraphNodesChildren.size(); i < max; i++) {
            Node paragraphNode = paragraphNodesChildren.get(i);
            Text textNode = (Text)paragraphNode;
            int paragraphLength = textNode.getText().length() + 1;
            if (end > start && start < paragraphLength) {
                textNode.setImpl_selectionStart(start);
                textNode.setImpl_selectionEnd(Math.min(end, paragraphLength));

                Path selectionHighlightPath = new Path();
                selectionHighlightPath.setManaged(false);
                selectionHighlightPath.setStroke(null);
                PathElement[] selectionShape = textNode.getImpl_selectionShape();
                if (selectionShape != null) {
                    selectionHighlightPath.getElements().addAll(selectionShape);
                }
                textAriaSkin.selectionHighlightGroup.getChildren().add(selectionHighlightPath);
                textAriaSkin.selectionHighlightGroup.setVisible(true);
                selectionHighlightPath.setLayoutX(textNode.getLayoutX());
                selectionHighlightPath.setLayoutY(textNode.getLayoutY());
                textAriaSkin.updateHighlightFill();
            } else {
                textNode.setImpl_selectionStart(-1);
                textNode.setImpl_selectionEnd(-1);
                textAriaSkin.selectionHighlightGroup.setVisible(false);
            }
            start = Math.max(0, start - paragraphLength);
            end   = Math.max(0, end   - paragraphLength);
        }

        if (TextInputControlSkin.SHOW_HANDLES) {
            // Position handle for the caret. This could be handle1 or handle2 when
            // a selection is active.
            Bounds b = textAriaSkin.caretPath.getBoundsInParent();
            if (selection.getLength() > 0) {
                if (caretPos < anchorPos) {
                    textAriaSkin.selectionHandle1.setLayoutX(b.getMinX() - textAriaSkin.selectionHandle1.getWidth() / 2);
                    textAriaSkin.selectionHandle1.setLayoutY(b.getMinY() - textAriaSkin.selectionHandle1.getHeight() + 1);
                } else {
                    textAriaSkin.selectionHandle2.setLayoutX(b.getMinX() - textAriaSkin.selectionHandle2.getWidth() / 2);
                    textAriaSkin.selectionHandle2.setLayoutY(b.getMaxY() - 1);
                }
            } else {
                textAriaSkin.caretHandle.setLayoutX(b.getMinX() - textAriaSkin.caretHandle.getWidth() / 2 + 1);
                textAriaSkin.caretHandle.setLayoutY(b.getMaxY());
            }
        }

        if (scrollPane.getPrefViewportWidth() == 0
                || scrollPane.getPrefViewportHeight() == 0) {
            textAriaSkin.updatePrefViewportWidth();
            textAriaSkin.updatePrefViewportHeight();
            if (getParent() != null && scrollPane.getPrefViewportWidth() > 0
                    || scrollPane.getPrefViewportHeight() > 0) {
                // Force layout of viewRect in ScrollPaneSkin
                getParent().requestLayout();
            }
        }

        // RT-36454: Fit to width/height only if smaller than viewport.
        // That is, grow to fit but don't shrink to fit.
        Bounds viewportBounds = scrollPane.getViewportBounds();
        boolean wasFitToWidth = scrollPane.isFitToWidth();
        boolean wasFitToHeight = scrollPane.isFitToHeight();
        boolean setFitToWidth = textArea.isWrapText() || computePrefWidth(-1) <= viewportBounds.getWidth();
        boolean setFitToHeight = computePrefHeight(width) <= viewportBounds.getHeight();
        if (wasFitToWidth != setFitToWidth || wasFitToHeight != setFitToHeight) {
            Platform.runLater(() -> {
                scrollPane.setFitToWidth(setFitToWidth);
                scrollPane.setFitToHeight(setFitToHeight);
            });
            getParent().requestLayout();
        }
    }
}
