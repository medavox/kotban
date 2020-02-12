/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.github.medavox.kotban.textaria;

import com.sun.javafx.scene.control.skin.TextInputControlSkin;


import com.sun.javafx.scene.text.HitInfo;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.geometry.VerticalDirection;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.List;

/**
 * Text area skin.
 */
public class TextAriaSkin extends TextInputControlSkin<TextAria, TextAriaBehavior> {

    final private TextAria textArea;

    // *** NOTE: Multiple node mode is not yet fully implemented *** //
    private final boolean USE_MULTIPLE_NODES = false;

    private double computedMinWidth = Double.NEGATIVE_INFINITY;
    private double computedMinHeight = Double.NEGATIVE_INFINITY;
    private double computedPrefWidth = Double.NEGATIVE_INFINITY;
    private double computedPrefHeight = Double.NEGATIVE_INFINITY;
    private double widthForComputedPrefHeight = Double.NEGATIVE_INFINITY;
    private double characterWidth;
    private double lineHeight;

    @Override protected void invalidateMetrics() {
        computedMinWidth = Double.NEGATIVE_INFINITY;
        computedMinHeight = Double.NEGATIVE_INFINITY;
        computedPrefWidth = Double.NEGATIVE_INFINITY;
        computedPrefHeight = Double.NEGATIVE_INFINITY;
    }

    private ContentView contentView = new ContentView(this);
    private Group paragraphNodes = new Group();

    private Text promptNode;
    private ObservableBooleanValue usePromptText;

    private ObservableIntegerValue caretPosition;
    private Group selectionHighlightGroup = new Group();

    private ScrollPane scrollPane;
    private Bounds oldViewportBounds;

    private VerticalDirection scrollDirection = null;

    private Path characterBoundingPath = new Path();

    private Timeline scrollSelectionTimeline = new Timeline();
    private EventHandler<ActionEvent> scrollSelectionHandler = event -> {
        switch (scrollDirection) {
            case UP: {
                // TODO Get previous offset
                break;
            }

            case DOWN: {
                // TODO Get next offset
                break;
            }
        }
    };

    public static final int SCROLL_RATE = 30;

    private double pressX, pressY; // For dragging handles on embedded
    private boolean handlePressed;

    public TextAriaSkin(final TextAria textArea) {
        super(textArea, new TextAriaBehavior(textArea));
        getBehavior().setTextAreaSkin(this);
        this.textArea = textArea;

        caretPosition = new IntegerBinding() {
            { bind(textArea.caretPositionProperty()); }
            @Override protected int computeValue() {
                return textArea.getCaretPosition();
            }
        };
        caretPosition.addListener((observable, oldValue, newValue) -> {
            targetCaretX = -1;
            if (newValue.intValue() > oldValue.intValue()) {
                setForwardBias(true);
            }
        });

        forwardBiasProperty().addListener(observable -> {
            if (textArea.getWidth() > 0) {
                updateTextNodeCaretPos(textArea.getCaretPosition());
            }
        });

//        setManaged(false);

        // Initialize content
        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(textArea.isWrapText());
        scrollPane.setContent(contentView);
        getChildren().add(scrollPane);

        getSkinnable().addEventFilter(ScrollEvent.ANY, event -> {
            if (event.isDirect() && handlePressed) {
                event.consume();
            }
        });

        // Add selection
        selectionHighlightGroup.setManaged(false);
        selectionHighlightGroup.setVisible(false);
        contentView.getChildren().add(selectionHighlightGroup);

        // Add content view
        paragraphNodes.setManaged(false);
        contentView.getChildren().add(paragraphNodes);

        // Add caret
        caretPath.setManaged(false);
        caretPath.setStrokeWidth(1);
        caretPath.fillProperty().bind(textFill);
        caretPath.strokeProperty().bind(textFill);
        // modifying visibility of the caret forces a layout-pass (RT-32373), so
        // instead we modify the opacity.
        caretPath.opacityProperty().bind(new DoubleBinding() {
            { bind(caretVisible); }
            @Override protected double computeValue() {
                return caretVisible.get() ? 1.0 : 0.0;
            }
        });
        contentView.getChildren().add(caretPath);

        if (SHOW_HANDLES) {
            contentView.getChildren().addAll(caretHandle, selectionHandle1, selectionHandle2);
        }

        scrollPane.hvalueProperty().addListener((observable, oldValue, newValue) -> {
            getSkinnable().setScrollLeft(newValue.doubleValue() * getScrollLeftMax());
        });

        scrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> {
            getSkinnable().setScrollTop(newValue.doubleValue() * getScrollTopMax());
        });

        // Initialize the scroll selection timeline
        scrollSelectionTimeline.setCycleCount(Timeline.INDEFINITE);
        List<KeyFrame> scrollSelectionFrames = scrollSelectionTimeline.getKeyFrames();
        scrollSelectionFrames.clear();
        scrollSelectionFrames.add(new KeyFrame(Duration.millis(350), scrollSelectionHandler));

        // Add initial text content
        for (int i = 0, n = USE_MULTIPLE_NODES ? textArea.getParagraphs().size() : 1; i < n; i++) {
            CharSequence paragraph = (n == 1) ? textArea.textProperty().getValueSafe() : textArea.getParagraphs().get(i);
            addParagraphNode(i, paragraph.toString());
        }

        textArea.selectionProperty().addListener((observable, oldValue, newValue) -> {
            // TODO Why do we need two calls here?
            textArea.requestLayout();
            contentView.requestLayout();
        });

        textArea.wrapTextProperty().addListener((observable, oldValue, newValue) -> {
            invalidateMetrics();
            scrollPane.setFitToWidth(newValue);
        });

        textArea.prefColumnCountProperty().addListener((observable, oldValue, newValue) -> {
            invalidateMetrics();
            updatePrefViewportWidth();
        });

        textArea.prefRowCountProperty().addListener((observable, oldValue, newValue) -> {
            invalidateMetrics();
            updatePrefViewportHeight();
        });

        updateFontMetrics();
        fontMetrics.addListener(valueModel -> {
            updateFontMetrics();
        });

        contentView.paddingProperty().addListener(valueModel -> {
            updatePrefViewportWidth();
            updatePrefViewportHeight();
        });

        scrollPane.viewportBoundsProperty().addListener(valueModel -> {
            if (scrollPane.getViewportBounds() != null) {
                // ScrollPane creates a new Bounds instance for each
                // layout pass, so we need to check if the width/height
                // have really changed to avoid infinite layout requests.
                Bounds newViewportBounds = scrollPane.getViewportBounds();
                if (oldViewportBounds == null ||
                        oldViewportBounds.getWidth() != newViewportBounds.getWidth() ||
                        oldViewportBounds.getHeight() != newViewportBounds.getHeight()) {

                    invalidateMetrics();
                    oldViewportBounds = newViewportBounds;
                    contentView.requestLayout();
                }
            }
        });

        textArea.scrollTopProperty().addListener((observable, oldValue, newValue) -> {
            double vValue = (newValue.doubleValue() < getScrollTopMax())
                    ? (newValue.doubleValue() / getScrollTopMax()) : 1.0;
            scrollPane.setVvalue(vValue);
        });

        textArea.scrollLeftProperty().addListener((observable, oldValue, newValue) -> {
            double hValue = (newValue.doubleValue() < getScrollLeftMax())
                    ? (newValue.doubleValue() / getScrollLeftMax()) : 1.0;
            scrollPane.setHvalue(hValue);
        });

        if (USE_MULTIPLE_NODES) {
            textArea.getParagraphs().addListener((ListChangeListener.Change<? extends CharSequence> change) -> {
                while (change.next()) {
                    int from = change.getFrom();
                    int to = change.getTo();
                    List<? extends CharSequence> removed = change.getRemoved();
                    if (from < to) {

                        if (removed.isEmpty()) {
                            // This is an add
                            for (int i = from, n = to; i < n; i++) {
                                addParagraphNode(i, change.getList().get(i).toString());
                            }
                        } else {
                            // This is an update
                            for (int i = from, n = to; i < n; i++) {
                                Node node = paragraphNodes.getChildren().get(i);
                                Text paragraphNode = (Text) node;
                                paragraphNode.setText(change.getList().get(i).toString());
                            }
                        }
                    } else {
                        // This is a remove
                        paragraphNodes.getChildren().subList(from, from + removed.size()).clear();
                    }
                }
            });
        } else {
            textArea.textProperty().addListener(observable -> {
                invalidateMetrics();
                ((Text)paragraphNodes.getChildren().get(0)).setText(textArea.textProperty().getValueSafe());
                contentView.requestLayout();
            });
        }

        usePromptText = new BooleanBinding() {
            { bind(textArea.textProperty(), textArea.promptTextProperty()); }
            @Override protected boolean computeValue() {
                String txt = textArea.getText();
                String promptTxt = textArea.getPromptText();
                return ((txt == null || txt.isEmpty()) &&
                        promptTxt != null && !promptTxt.isEmpty());
            }
        };

        if (usePromptText.get()) {
            createPromptNode();
        }

        usePromptText.addListener(observable -> {
            createPromptNode();
            textArea.requestLayout();
        });

        updateHighlightFill();
        updatePrefViewportWidth();
        updatePrefViewportHeight();
        if (textArea.isFocused()) setCaretAnimating(true);

        if (SHOW_HANDLES) {
            selectionHandle1.setRotate(180);

            EventHandler<MouseEvent> handlePressHandler = e -> {
                pressX = e.getX();
                pressY = e.getY();
                handlePressed = true;
                e.consume();
            };

            EventHandler<MouseEvent> handleReleaseHandler = event -> {
                handlePressed = false;
            };

            caretHandle.setOnMousePressed(handlePressHandler);
            selectionHandle1.setOnMousePressed(handlePressHandler);
            selectionHandle2.setOnMousePressed(handlePressHandler);

            caretHandle.setOnMouseReleased(handleReleaseHandler);
            selectionHandle1.setOnMouseReleased(handleReleaseHandler);
            selectionHandle2.setOnMouseReleased(handleReleaseHandler);

            caretHandle.setOnMouseDragged(e -> {
                Text textNode = getTextNode();
                Point2D tp = textNode.localToScene(0, 0);
                Point2D p = new Point2D(e.getSceneX() - tp.getX() + 10/*??*/ - pressX + caretHandle.getWidth() / 2,
                        e.getSceneY() - tp.getY() - pressY - 6);
                HitInfo hit = textNode.impl_hitTestChar(translateCaretPosition(p));
                int pos = hit.getCharIndex();
                if (pos > 0) {
                    int oldPos = textNode.getImpl_caretPosition();
                    textNode.setImpl_caretPosition(pos);
                    PathElement element = textNode.getImpl_caretShape()[0];
                    if (element instanceof MoveTo && ((MoveTo)element).getY() > e.getY() - getTextTranslateY()) {
                        hit.setCharIndex(pos - 1);
                    }
                    textNode.setImpl_caretPosition(oldPos);
                }
                positionCaret(hit, false, false);
                e.consume();
            });

            selectionHandle1.setOnMouseDragged(new EventHandler<MouseEvent>() {
                @Override public void handle(MouseEvent e) {
                    TextAria textArea = getSkinnable();
                    Text textNode = getTextNode();
                    Point2D tp = textNode.localToScene(0, 0);
                    Point2D p = new Point2D(e.getSceneX() - tp.getX() + 10/*??*/ - pressX + selectionHandle1.getWidth() / 2,
                            e.getSceneY() - tp.getY() - pressY + selectionHandle1.getHeight() + 5);
                    HitInfo hit = textNode.impl_hitTestChar(translateCaretPosition(p));
                    int pos = hit.getCharIndex();
                    if (textArea.getAnchor() < textArea.getCaretPosition()) {
                        // Swap caret and anchor
                        textArea.selectRange(textArea.getCaretPosition(), textArea.getAnchor());
                    }
                    if (pos > 0) {
                        if (pos >= textArea.getAnchor()) {
                            pos = textArea.getAnchor();
                        }
                        int oldPos = textNode.getImpl_caretPosition();
                        textNode.setImpl_caretPosition(pos);
                        PathElement element = textNode.getImpl_caretShape()[0];
                        if (element instanceof MoveTo && ((MoveTo)element).getY() > e.getY() - getTextTranslateY()) {
                            hit.setCharIndex(pos - 1);
                        }
                        textNode.setImpl_caretPosition(oldPos);
                    }
                    positionCaret(hit, true, false);
                    e.consume();
                }
            });

            selectionHandle2.setOnMouseDragged(new EventHandler<MouseEvent>() {
                @Override public void handle(MouseEvent e) {
                    TextAria textArea = getSkinnable();
                    Text textNode = getTextNode();
                    Point2D tp = textNode.localToScene(0, 0);
                    Point2D p = new Point2D(e.getSceneX() - tp.getX() + 10/*??*/ - pressX + selectionHandle2.getWidth() / 2,
                            e.getSceneY() - tp.getY() - pressY - 6);
                    HitInfo hit = textNode.impl_hitTestChar(translateCaretPosition(p));
                    int pos = hit.getCharIndex();
                    if (textArea.getAnchor() > textArea.getCaretPosition()) {
                        // Swap caret and anchor
                        textArea.selectRange(textArea.getCaretPosition(), textArea.getAnchor());
                    }
                    if (pos > 0) {
                        if (pos <= textArea.getAnchor() + 1) {
                            pos = Math.min(textArea.getAnchor() + 2, textArea.getLength());
                        }
                        int oldPos = textNode.getImpl_caretPosition();
                        textNode.setImpl_caretPosition(pos);
                        PathElement element = textNode.getImpl_caretShape()[0];
                        if (element instanceof MoveTo && ((MoveTo)element).getY() > e.getY() - getTextTranslateY()) {
                            hit.setCharIndex(pos - 1);
                        }
                        textNode.setImpl_caretPosition(oldPos);
                        positionCaret(hit, true, false);
                    }
                    e.consume();
                }
            });
        }
    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        scrollPane.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
    }

    private void createPromptNode() {
        if (promptNode == null && usePromptText.get()) {
            promptNode = new Text();
            contentView.getChildren().add(0, promptNode);
            promptNode.setManaged(false);
            promptNode.getStyleClass().add("text");
            promptNode.visibleProperty().bind(usePromptText);
            promptNode.fontProperty().bind(getSkinnable().fontProperty());
            promptNode.textProperty().bind(getSkinnable().promptTextProperty());
            promptNode.fillProperty().bind(promptTextFill);
        }
    }

    private void addParagraphNode(int i, String string) {
        final TextAria textArea = getSkinnable();
        Text paragraphNode = new Text(string);
        paragraphNode.setTextOrigin(VPos.TOP);
        paragraphNode.setManaged(false);
        paragraphNode.getStyleClass().add("text");
        paragraphNode.boundsTypeProperty().addListener((observable, oldValue, newValue) -> {
            invalidateMetrics();
            updateFontMetrics();
        });
        paragraphNodes.getChildren().add(i, paragraphNode);

        paragraphNode.fontProperty().bind(textArea.fontProperty());
        paragraphNode.fillProperty().bind(textFill);
        paragraphNode.impl_selectionFillProperty().bind(highlightTextFill);
    }

    @Override
    public void dispose() {
        // TODO Unregister listeners on text editor, paragraph list
        throw new UnsupportedOperationException();
    }

    @Override
    public double computeBaselineOffset(double topInset, double rightInset, double bottomInset, double leftInset) {
        Text firstParagraph = (Text) paragraphNodes.getChildren().get(0);
        return Utils.getAscent(getSkinnable().getFont(),firstParagraph.getBoundsType())
                + contentView.snappedTopInset() + textArea.snappedTopInset();
    }

    @Override
    public char getCharacter(int index) {
        int n = paragraphNodes.getChildren().size();

        int paragraphIndex = 0;
        int offset = index;

        String paragraph = null;
        while (paragraphIndex < n) {
            Text paragraphNode = (Text)paragraphNodes.getChildren().get(paragraphIndex);
            paragraph = paragraphNode.getText();
            int count = paragraph.length() + 1;

            if (offset < count) {
                break;
            }

            offset -= count;
            paragraphIndex++;
        }

        return offset == paragraph.length() ? '\n' : paragraph.charAt(offset);
    }

    @Override
    public int getInsertionPoint(double x, double y) {
        TextAria textArea = getSkinnable();

        int n = paragraphNodes.getChildren().size();
        int index = -1;

        if (n > 0) {
            if (y < contentView.snappedTopInset()) {
                // Select the character at x in the first row
                Text paragraphNode = (Text)paragraphNodes.getChildren().get(0);
                index = getNextInsertionPoint(paragraphNode, x, -1, VerticalDirection.DOWN);
            } else if (y > contentView.snappedTopInset() + contentView.getHeight()) {
                // Select the character at x in the last row
                int lastParagraphIndex = n - 1;
                Text lastParagraphView = (Text)paragraphNodes.getChildren().get(lastParagraphIndex);

                index = getNextInsertionPoint(lastParagraphView, x, -1, VerticalDirection.UP)
                        + (textArea.getLength() - lastParagraphView.getText().length());
            } else {
                // Select the character at x in the row at y
                int paragraphOffset = 0;
                for (int i = 0; i < n; i++) {
                    Text paragraphNode = (Text)paragraphNodes.getChildren().get(i);

                    Bounds bounds = paragraphNode.getBoundsInLocal();
                    double paragraphViewY = paragraphNode.getLayoutY() + bounds.getMinY();
                    if (y >= paragraphViewY
                            && y < paragraphViewY + paragraphNode.getBoundsInLocal().getHeight()) {
                        index = getInsertionPoint(paragraphNode,
                                x - paragraphNode.getLayoutX(),
                                y - paragraphNode.getLayoutY()) + paragraphOffset;
                        break;
                    }

                    paragraphOffset += paragraphNode.getText().length() + 1;
                }
            }
        }

        return index;
    }

    public void positionCaret(HitInfo hit, boolean select, boolean extendSelection) {
        int pos = Utils.getHitInsertionIndex(hit, getSkinnable().getText());
        boolean isNewLine =
                (pos > 0 &&
                        pos <= getSkinnable().getLength() &&
                        getSkinnable().getText().codePointAt(pos-1) == 0x0a);

        // special handling for a new line
        if (!hit.isLeading() && isNewLine) {
            hit.setLeading(true);
            pos -= 1;
        }

        if (select) {
            if (extendSelection) {
                getSkinnable().extendSelection(pos);
            } else {
                getSkinnable().selectPositionCaret(pos);
            }
        } else {
            getSkinnable().positionCaret(pos);
        }

        setForwardBias(hit.isLeading());
    }

    private double getScrollTopMax() {
        return Math.max(0, contentView.getHeight() - scrollPane.getViewportBounds().getHeight());
    }

    private double getScrollLeftMax() {
        return Math.max(0, contentView.getWidth() - scrollPane.getViewportBounds().getWidth());
    }

    private int getInsertionPoint(Text paragraphNode, double x, double y) {
        HitInfo hitInfo = paragraphNode.impl_hitTestChar(new Point2D(x, y));
        return Utils.getHitInsertionIndex(hitInfo, paragraphNode.getText());
    }

    public int getNextInsertionPoint(double x, int from, VerticalDirection scrollDirection) {
        // TODO
        return 0;
    }

    private int getNextInsertionPoint(Text paragraphNode, double x, int from,
                                      VerticalDirection scrollDirection) {
        // TODO
        return 0;
    }

    @Override
    public Rectangle2D getCharacterBounds(int index) {
        TextAria textArea = getSkinnable();

        int paragraphIndex = paragraphNodes.getChildren().size();
        int paragraphOffset = textArea.getLength() + 1;

        Text paragraphNode = null;
        do {
            paragraphNode = (Text)paragraphNodes.getChildren().get(--paragraphIndex);
            paragraphOffset -= paragraphNode.getText().length() + 1;
        } while (index < paragraphOffset);

        int characterIndex = index - paragraphOffset;
        boolean terminator = false;

        if (characterIndex == paragraphNode.getText().length()) {
            characterIndex--;
            terminator = true;
        }

        characterBoundingPath.getElements().clear();
        characterBoundingPath.getElements().addAll(paragraphNode.impl_getRangeShape(characterIndex, characterIndex + 1));
        characterBoundingPath.setLayoutX(paragraphNode.getLayoutX());
        characterBoundingPath.setLayoutY(paragraphNode.getLayoutY());

        Bounds bounds = characterBoundingPath.getBoundsInLocal();

        double x = bounds.getMinX() + paragraphNode.getLayoutX() - textArea.getScrollLeft();
        double y = bounds.getMinY() + paragraphNode.getLayoutY() - textArea.getScrollTop();

        // Sometimes the bounds is empty, in which case we must ignore the width/height
        double width = bounds.isEmpty() ? 0 : bounds.getWidth();
        double height = bounds.isEmpty() ? 0 : bounds.getHeight();

        if (terminator) {
            x += width;
            width = 0;
        }

        return new Rectangle2D(x, y, width, height);
    }

    @Override public void scrollCharacterToVisible(final int index) {
        // TODO We queue a callback because when characters are added or
        // removed the bounds are not immediately updated; is this really
        // necessary?

        Platform.runLater(() -> {
            if (getSkinnable().getLength() == 0) {
                return;
            }
            Rectangle2D characterBounds = getCharacterBounds(index);
            scrollBoundsToVisible(characterBounds);
        });
    }

    private void scrollCaretToVisible() {
        TextAria textArea = getSkinnable();
        Bounds bounds = caretPath.getLayoutBounds();
        double x = bounds.getMinX() - textArea.getScrollLeft();
        double y = bounds.getMinY() - textArea.getScrollTop();
        double w = bounds.getWidth();
        double h = bounds.getHeight();

        if (SHOW_HANDLES) {
            if (caretHandle.isVisible()) {
                h += caretHandle.getHeight();
            } else if (selectionHandle1.isVisible() && selectionHandle2.isVisible()) {
                x -= selectionHandle1.getWidth() / 2;
                y -= selectionHandle1.getHeight();
                w += selectionHandle1.getWidth() / 2 + selectionHandle2.getWidth() / 2;
                h += selectionHandle1.getHeight() + selectionHandle2.getHeight();
            }
        }

        if (w > 0 && h > 0) {
            scrollBoundsToVisible(new Rectangle2D(x, y, w, h));
        }
    }

    private void scrollBoundsToVisible(Rectangle2D bounds) {
        TextAria textArea = getSkinnable();
        Bounds viewportBounds = scrollPane.getViewportBounds();

        double viewportWidth = viewportBounds.getWidth();
        double viewportHeight = viewportBounds.getHeight();
        double scrollTop = textArea.getScrollTop();
        double scrollLeft = textArea.getScrollLeft();
        double slop = 6.0;

        if (bounds.getMinY() < 0) {
            double y = scrollTop + bounds.getMinY();
            if (y <= contentView.snappedTopInset()) {
                y = 0;
            }
            textArea.setScrollTop(y);
        } else if (contentView.snappedTopInset() + bounds.getMaxY() > viewportHeight) {
            double y = scrollTop + contentView.snappedTopInset() + bounds.getMaxY() - viewportHeight;
            if (y >= getScrollTopMax() - contentView.snappedBottomInset()) {
                y = getScrollTopMax();
            }
            textArea.setScrollTop(y);
        }


        if (bounds.getMinX() < 0) {
            double x = scrollLeft + bounds.getMinX() - slop;
            if (x <= contentView.snappedLeftInset() + slop) {
                x = 0;
            }
            textArea.setScrollLeft(x);
        } else if (contentView.snappedLeftInset() + bounds.getMaxX() > viewportWidth) {
            double x = scrollLeft + contentView.snappedLeftInset() + bounds.getMaxX() - viewportWidth + slop;
            if (x >= getScrollLeftMax() - contentView.snappedRightInset() - slop) {
                x = getScrollLeftMax();
            }
            textArea.setScrollLeft(x);
        }
    }

    private void updatePrefViewportWidth() {
        int columnCount = getSkinnable().getPrefColumnCount();
        scrollPane.setPrefViewportWidth(columnCount * characterWidth + contentView.snappedLeftInset() + contentView.snappedRightInset());
        scrollPane.setMinViewportWidth(characterWidth + contentView.snappedLeftInset() + contentView.snappedRightInset());
    }

    private void updatePrefViewportHeight() {
        int rowCount = getSkinnable().getPrefRowCount();
        scrollPane.setPrefViewportHeight(rowCount * lineHeight + contentView.snappedTopInset() + contentView.snappedBottomInset());
        scrollPane.setMinViewportHeight(lineHeight + contentView.snappedTopInset() + contentView.snappedBottomInset());
    }

    private void updateFontMetrics() {
        Text firstParagraph = (Text)paragraphNodes.getChildren().get(0);
        lineHeight = Utils.getLineHeight(getSkinnable().getFont(),firstParagraph.getBoundsType());
        characterWidth = fontMetrics.get().computeStringWidth("W");
    }

    @Override
    protected void updateHighlightFill() {
        for (Node node : selectionHighlightGroup.getChildren()) {
            Path selectionHighlightPath = (Path)node;
            selectionHighlightPath.setFill(highlightFill.get());
        }
    }

//     protected void handleMouseReleasedEvent(MouseEvent event) {
// //        super.handleMouseReleasedEvent(event);

//         // Stop the scroll selection timer
//         scrollSelectionTimeline.stop();
//         scrollDirection = null;

//         // Select all if the user double-clicked
//         if (event.getButton() == MouseButton.PRIMARY
//             && event.getClickCount() == 3) {
//             // TODO Select the current row
//         }
//     }

    // Callbacks from Behavior class

    private double getTextTranslateX() {
        return contentView.snappedLeftInset();
    }

    private double getTextTranslateY() {
        return contentView.snappedTopInset();
    }

    private double getTextLeft() {
        return 0;
    }

    private Point2D translateCaretPosition(Point2D p) {
        return p;
    }

    private Text getTextNode() {
        if (USE_MULTIPLE_NODES) {
            throw new IllegalArgumentException("Multiple node traversal is not yet implemented.");
        }
        return (Text)paragraphNodes.getChildren().get(0);
    }

    public HitInfo getIndex(double x, double y) {
        // adjust the event to be in the same coordinate space as the
        // text content of the textInputControl
        Text textNode = getTextNode();
        Point2D p = new Point2D(x - textNode.getLayoutX(), y - getTextTranslateY());
        HitInfo hit = textNode.impl_hitTestChar(translateCaretPosition(p));
        int pos = hit.getCharIndex();
        if (pos > 0) {
            int oldPos = textNode.getImpl_caretPosition();
            textNode.setImpl_caretPosition(pos);
            PathElement element = textNode.getImpl_caretShape()[0];
            if (element instanceof MoveTo && ((MoveTo)element).getY() > y - getTextTranslateY()) {
                hit.setCharIndex(pos - 1);
            }
            textNode.setImpl_caretPosition(oldPos);
        }
        return hit;
    };

    /**
     * Remembers horizontal position when traversing up / down.
     */
    double targetCaretX = -1;

    @Override public void nextCharacterVisually(boolean moveRight) {
        if (isRTL()) {
            // Text node is mirrored.
            moveRight = !moveRight;
        }

        Text textNode = getTextNode();
        Bounds caretBounds = caretPath.getLayoutBounds();
        if (caretPath.getElements().size() == 4) {
            // The caret is split
            // TODO: Find a better way to get the primary caret position
            // instead of depending on the internal implementation.
            // See RT-25465.
            caretBounds = new Path(caretPath.getElements().get(0), caretPath.getElements().get(1)).getLayoutBounds();
        }
        double hitX = moveRight ? caretBounds.getMaxX() : caretBounds.getMinX();
        double hitY = (caretBounds.getMinY() + caretBounds.getMaxY()) / 2;
        HitInfo hit = textNode.impl_hitTestChar(new Point2D(hitX, hitY));
        Path charShape = new Path(textNode.impl_getRangeShape(hit.getCharIndex(), hit.getCharIndex() + 1));
        if ((moveRight && charShape.getLayoutBounds().getMaxX() > caretBounds.getMaxX()) ||
                (!moveRight && charShape.getLayoutBounds().getMinX() < caretBounds.getMinX())) {
            hit.setLeading(!hit.isLeading());
            positionCaret(hit, false, false);
        } else {
            // We're at beginning or end of line. Try moving up / down.
            int dot = textArea.getCaretPosition();
            targetCaretX = moveRight ? 0 : Double.MAX_VALUE;
            // TODO: Use Bidi sniffing instead of assuming right means forward here?
            downLines(moveRight ? 1 : -1, false, false);
            targetCaretX = -1;
            if (dot == textArea.getCaretPosition()) {
                if (moveRight) {
                    textArea.forward();
                } else {
                    textArea.backward();
                }
            }
        }
    }

    /** A shared helper object, used only by downLines(). */
    private static final Path tmpCaretPath = new Path();

    protected void downLines(int nLines, boolean select, boolean extendSelection) {
        Text textNode = getTextNode();
        Bounds caretBounds = caretPath.getLayoutBounds();

        // The middle y coordinate of the the line we want to go to.
        double targetLineMidY = (caretBounds.getMinY() + caretBounds.getMaxY()) / 2 + nLines * lineHeight;
        if (targetLineMidY < 0) {
            targetLineMidY = 0;
        }

        // The target x for the caret. This may have been set during a
        // previous call.
        double x = (targetCaretX >= 0) ? targetCaretX : (caretBounds.getMaxX());

        // Find a text position for the target x,y.
        HitInfo hit = textNode.impl_hitTestChar(translateCaretPosition(new Point2D(x, targetLineMidY)));
        int pos = hit.getCharIndex();

        // Save the old pos temporarily while testing the new one.
        int oldPos = textNode.getImpl_caretPosition();
        boolean oldBias = textNode.isImpl_caretBias();
        textNode.setImpl_caretBias(hit.isLeading());
        textNode.setImpl_caretPosition(pos);
        tmpCaretPath.getElements().clear();
        tmpCaretPath.getElements().addAll(textNode.getImpl_caretShape());
        tmpCaretPath.setLayoutX(textNode.getLayoutX());
        tmpCaretPath.setLayoutY(textNode.getLayoutY());
        Bounds tmpCaretBounds = tmpCaretPath.getLayoutBounds();
        // The y for the middle of the row we found.
        double foundLineMidY = (tmpCaretBounds.getMinY() + tmpCaretBounds.getMaxY()) / 2;
        textNode.setImpl_caretBias(oldBias);
        textNode.setImpl_caretPosition(oldPos);

        if (pos > 0) {
            if (nLines > 0 && foundLineMidY > targetLineMidY) {
                // We went too far and ended up after a newline.
                hit.setCharIndex(pos - 1);
            }

            if (pos >= textArea.getLength() && getCharacter(pos - 1) == '\n') {
                // Special case for newline at end of text.
                hit.setLeading(true);
            }
        }

        // Test if the found line is in the correct direction and move
        // the caret.
        if (nLines == 0 ||
                (nLines > 0 && foundLineMidY > caretBounds.getMaxY()) ||
                (nLines < 0 && foundLineMidY < caretBounds.getMinY())) {

            positionCaret(hit, select, extendSelection);
            targetCaretX = x;
        }
    }

    public void previousLine(boolean select) {
        downLines(-1, select, false);
    }

    public void nextLine(boolean select) {
        downLines(1, select, false);
    }

    public void previousPage(boolean select) {
        downLines(-(int)(scrollPane.getViewportBounds().getHeight() / lineHeight),
                select, false);
    }

    public void nextPage(boolean select) {
        downLines((int)(scrollPane.getViewportBounds().getHeight() / lineHeight),
                select, false);
    }

    public void lineStart(boolean select, boolean extendSelection) {
        targetCaretX = 0;
        downLines(0, select, extendSelection);
        targetCaretX = -1;
    }

    public void lineEnd(boolean select, boolean extendSelection) {
        targetCaretX = Double.MAX_VALUE;
        downLines(0, select, extendSelection);
        targetCaretX = -1;
    }


    public void paragraphStart(boolean previousIfAtStart, boolean select) {
        TextAria textArea = getSkinnable();
        String text = textArea.textProperty().getValueSafe();
        int pos = textArea.getCaretPosition();

        if (pos > 0) {
            if (previousIfAtStart && text.codePointAt(pos-1) == 0x0a) {
                // We are at the beginning of a paragraph.
                // Back up to the previous paragraph.
                pos--;
            }
            // Back up to the beginning of this paragraph
            while (pos > 0 && text.codePointAt(pos-1) != 0x0a) {
                pos--;
            }
            if (select) {
                textArea.selectPositionCaret(pos);
            } else {
                textArea.positionCaret(pos);
            }
        }
    }

    public void paragraphEnd(boolean goPastInitialNewline, boolean goPastTrailingNewline, boolean select) {
        TextAria textArea = getSkinnable();
        String text = textArea.textProperty().getValueSafe();
        int pos = textArea.getCaretPosition();
        int len = text.length();
        boolean wentPastInitialNewline = false;

        if (pos < len) {
            if (goPastInitialNewline && text.codePointAt(pos) == 0x0a) {
                // We are at the end of a paragraph, start by moving to the
                // next paragraph.
                pos++;
                wentPastInitialNewline = true;
            }
            if (!(goPastTrailingNewline && wentPastInitialNewline)) {
                // Go to the end of this paragraph
                while (pos < len && text.codePointAt(pos) != 0x0a) {
                    pos++;
                }
                if (goPastTrailingNewline && pos < len) {
                    // We are at the end of a paragraph, finish by moving to
                    // the beginning of the next paragraph (Windows behavior).
                    pos++;
                }
            }
            if (select) {
                textArea.selectPositionCaret(pos);
            } else {
                textArea.positionCaret(pos);
            }
        }
    }

    private void updateTextNodeCaretPos(int pos) {
        Text textNode = getTextNode();
        if (isForwardBias()) {
            textNode.setImpl_caretPosition(pos);
        } else {
            textNode.setImpl_caretPosition(pos - 1);
        }
        textNode.impl_caretBiasProperty().set(isForwardBias());
    }

    @Override protected PathElement[] getUnderlineShape(int start, int end) {
        int pStart = 0;
        for (Node node : paragraphNodes.getChildren()) {
            Text p = (Text)node;
            int pEnd = pStart + p.textProperty().getValueSafe().length();
            if (pEnd >= start) {
                return p.impl_getUnderlineShape(start - pStart, end - pStart);
            }
            pStart = pEnd + 1;
        }
        return null;
    }

    @Override protected PathElement[] getRangeShape(int start, int end) {
        int pStart = 0;
        for (Node node : paragraphNodes.getChildren()) {
            Text p = (Text)node;
            int pEnd = pStart + p.textProperty().getValueSafe().length();
            if (pEnd >= start) {
                return p.impl_getRangeShape(start - pStart, end - pStart);
            }
            pStart = pEnd + 1;
        }
        return null;
    }

    @Override protected void addHighlight(List<? extends Node> nodes, int start) {
        int pStart = 0;
        Text paragraphNode = null;
        for (Node node : paragraphNodes.getChildren()) {
            Text p = (Text)node;
            int pEnd = pStart + p.textProperty().getValueSafe().length();
            if (pEnd >= start) {
                paragraphNode = p;
                break;
            }
            pStart = pEnd + 1;
        }

        if (paragraphNode != null) {
            for (Node node : nodes) {
                node.setLayoutX(paragraphNode.getLayoutX());
                node.setLayoutY(paragraphNode.getLayoutY());
            }
        }
        contentView.getChildren().addAll(nodes);
    }

    @Override protected void removeHighlight(List<? extends Node> nodes) {
        contentView.getChildren().removeAll(nodes);
    }

    /**
     * Use this implementation instead of the one provided on TextInputControl
     * Simply calls into TextInputControl.deletePrevious/NextChar and responds appropriately
     * based on the return value.
     */
    public void deleteChar(boolean previous) {
//        final double textMaxXOld = textNode.getBoundsInParent().getMaxX();
//        final double caretMaxXOld = caretPath.getLayoutBounds().getMaxX() + textTranslateX.get();
        final boolean shouldBeep = previous ?
                !getSkinnable().deletePreviousChar() :
                !getSkinnable().deleteNextChar();

        if (shouldBeep) {
//            beep();
        } else {
//            scrollAfterDelete(textMaxXOld, caretMaxXOld);
        }
    }

    @Override public Point2D getMenuPosition() {
        contentView.layoutChildren();
        Point2D p = super.getMenuPosition();
        if (p != null) {
            p = new Point2D(Math.max(0, p.getX() - contentView.snappedLeftInset() - getSkinnable().getScrollLeft()),
                    Math.max(0, p.getY() - contentView.snappedTopInset() - getSkinnable().getScrollTop()));
        }
        return p;
    }

    public Bounds getCaretBounds() {
        return getSkinnable().sceneToLocal(caretPath.localToScene(caretPath.getBoundsInLocal()));
    }

    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case LINE_FOR_OFFSET:
            case LINE_START:
            case LINE_END:
            case BOUNDS_FOR_RANGE:
            case OFFSET_AT_POINT:
                Text text = getTextNode();
                return text.queryAccessibleAttribute(attribute, parameters);
            default: return super.queryAccessibleAttribute(attribute, parameters);
        }
    }
}
