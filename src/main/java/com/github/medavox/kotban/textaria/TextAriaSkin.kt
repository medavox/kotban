/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.github.medavox.kotban.textaria

import com.sun.javafx.scene.control.skin.TextInputControlSkin


import com.sun.javafx.scene.text.HitInfo
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.DoubleBinding
import javafx.beans.binding.IntegerBinding
import javafx.beans.property.DoubleProperty
import javafx.beans.property.DoublePropertyBase
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableIntegerValue
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.*
import javafx.scene.AccessibleAttribute
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.IndexRange
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.Region
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import javafx.scene.shape.PathElement
import javafx.scene.text.Text
import javafx.util.Duration

import java.util.List

/**
 * Text area skin.
 */
class TextAriaSkin(private val textArea:TextAria) : TextInputControlSkin<TextAria, TextAriaBehavior>(textArea, TextAriaBehavior(textArea)) {
    // *** NOTE: Multiple node mode is not yet fully implemented *** //
    private val USE_MULTIPLE_NODES = false

    private var computedMinWidth: Double = Double.NEGATIVE_INFINITY
    private var computedMinHeight: Double = Double.NEGATIVE_INFINITY
    private var computedPrefWidth: Double = Double.NEGATIVE_INFINITY
    private var computedPrefHeight: Double = Double.NEGATIVE_INFINITY
    private var widthForComputedPrefHeight: Double = Double.NEGATIVE_INFINITY
    private var characterWidth: Double
    private var lineHeight: Double
    private var contentView: ContentView = ContentView()
    var doubleBinding: DoubleProperty = contentView.hajt
    private var paragraphNodes: Group = Group()

    private var promptNode: Text
    private var usePromptText: ObservableBooleanValue

    private var caretPosition: ObservableIntegerValue
    private var selectionHighlightGroup: Group = Group()

    private var oldViewportBounds: Bounds

    private var scrollDirection: VerticalDirection = null

    private var characterBoundingPath: Path = Path()

    private var scrollSelectionTimeline: Timeline = Timeline()

    static final int SCROLL_RATE = 30

    private pressX: Double, pressY; // For dragging handles on embedded
    private var handlePressed: Boolean

    private EventHandler<ActionEvent> scrollSelectionHandler = event -> {
        switch (scrollDirection) {
            case UP: {
                // TODO Get previous offset
                break
            }

            case DOWN: {
                // TODO Get next offset
                break
            }
        }
    }

    init {
        caretPosition = IntegerBinding() {
            { bind(textArea.caretPositionProperty()); }
            override protected var computeValue: Int() {
            return textArea.getCaretPosition()
        }
        }
        caretPosition.addListener((observable, oldValue, newValue) -> {
            targetCaretX = -1
            if (newValue.intValue() > oldValue.intValue()) {
                setForwardBias(true)
            }
        })

        forwardBiasProperty().addListener(observable -> {
            if (textArea.getWidth() > 0) {
                updateTextNodeCaretPos(textArea.getCaretPosition())
            }
        })

        // Initialize content
        getChildren().add(contentView)

        getSkinnable().addEventFilter(ScrollEvent.ANY, event -> {
            if (event.isDirect() && handlePressed) {
                event.consume()
            }
        })

        // Add selection
        selectionHighlightGroup.setManaged(false)
        selectionHighlightGroup.setVisible(false)
        contentView.getChildren().add(selectionHighlightGroup)

        // Add content view
        paragraphNodes.setManaged(false)
        contentView.getChildren().add(paragraphNodes)

        // Add caret
        caretPath.setManaged(false)
        caretPath.setStrokeWidth(1)
        caretPath.fillProperty().bind(textFill)
        caretPath.strokeProperty().bind(textFill)
        // modifying visibility of the caret forces a layout-pass (RT-32373), so
        // instead we modify the opacity.
        caretPath.opacityProperty().bind(DoubleBinding() {
            { bind(caretVisible); }
            override protected var computeValue: Double() {
            return caretVisible.get() ? 1.0 : 0.0
        }
        })
        contentView.getChildren().add(caretPath)

        if (SHOW_HANDLES) {
            contentView.getChildren().addAll(caretHandle, selectionHandle1, selectionHandle2)
        }

        // Initialize the scroll selection timeline
        scrollSelectionTimeline.setCycleCount(Timeline.INDEFINITE)
        List<KeyFrame> scrollSelectionFrames = scrollSelectionTimeline.getKeyFrames()
        scrollSelectionFrames.clear()
        scrollSelectionFrames.add(KeyFrame(Duration.millis(350), scrollSelectionHandler))

        // Add initial text content
        for (var i: Int = 0, n = USE_MULTIPLE_NODES ? textArea.getParagraphs().size() : 1; i < n; i++) {
            var paragraph: CharSequence = (n == 1) ? textArea.textProperty().getValueSafe() : textArea.getParagraphs().get(i)
            addParagraphNode(i, paragraph.toString())
        }

        textArea.selectionProperty().addListener((observable, oldValue, newValue) -> {
            // TODO Why do we need two calls here?
            textArea.requestLayout()
            contentView.requestLayout()
        })

        textArea.wrapTextProperty().addListener((observable, oldValue, newValue) -> {
            invalidateMetrics()
        })

        textArea.prefColumnCountProperty().addListener((observable, oldValue, newValue) -> {
            invalidateMetrics()
            updatePrefViewportWidth()
        })

        textArea.prefRowCountProperty().addListener((observable, oldValue, newValue) -> {
            invalidateMetrics()
            updatePrefViewportHeight()
        })

        updateFontMetrics()
        fontMetrics.addListener(valueModel -> {
            updateFontMetrics()
        })

        contentView.paddingProperty().addListener(valueModel -> {
            updatePrefViewportWidth()
            updatePrefViewportHeight()
        })

        if (USE_MULTIPLE_NODES) {
            textArea.getParagraphs().addListener((ListChangeListener.Change<? : CharSequence> change) -> {
                while (change.next()) {
                    var from: Int = change.getFrom()
                    var to: Int = change.getTo()
                    List<? : CharSequence> removed = change.getRemoved()
                    if (from < to) {

                        if (removed.isEmpty()) {
                            // This is an add
                            for (var i: Int = from, n = to; i < n; i++) {
                                addParagraphNode(i, change.getList().get(i).toString())
                            }
                        } else {
                            // This is an update
                            for (var i: Int = from, n = to; i < n; i++) {
                                var node: Node = paragraphNodes.getChildren().get(i)
                                var paragraphNode: Text = (Text) node
                                        paragraphNode.setText(change.getList().get(i).toString())
                            }
                        }
                    } else {
                        // This is a remove
                        paragraphNodes.getChildren().subList(from, from + removed.size()).clear()
                    }
                }
            })
        } else {
            textArea.textProperty().addListener(observable -> {
                invalidateMetrics()
                ((Text)paragraphNodes.getChildren().get(0)).setText(textArea.textProperty().getValueSafe())
                contentView.requestLayout()
            })
        }

        usePromptText = BooleanBinding() {
            { bind(textArea.textProperty(), textArea.promptTextProperty()); }
            override protected var computeValue: Boolean() {
            var txt: String = textArea.getText()
            var promptTxt: String = textArea.getPromptText()
            return ((txt == null || txt.isEmpty()) &&
                    promptTxt != null && !promptTxt.isEmpty())
        }
        }

        if (usePromptText.get()) {
            createPromptNode()
        }

        usePromptText.addListener(observable -> {
            createPromptNode()
            textArea.requestLayout()
        })

        updateHighlightFill()
        updatePrefViewportWidth()
        updatePrefViewportHeight()
        if (textArea.isFocused()) setCaretAnimating(true)

        if (SHOW_HANDLES) {
            selectionHandle1.setRotate(180)

            EventHandler<MouseEvent> handlePressHandler = e -> {
                pressX = e.getX()
                pressY = e.getY()
                handlePressed = true
                e.consume()
            }

            EventHandler<MouseEvent> handleReleaseHandler = event -> {
                handlePressed = false
            }

            caretHandle.setOnMousePressed(handlePressHandler)
            selectionHandle1.setOnMousePressed(handlePressHandler)
            selectionHandle2.setOnMousePressed(handlePressHandler)

            caretHandle.setOnMouseReleased(handleReleaseHandler)
            selectionHandle1.setOnMouseReleased(handleReleaseHandler)
            selectionHandle2.setOnMouseReleased(handleReleaseHandler)

            caretHandle.setOnMouseDragged(e -> {
                var textNode: Text = getTextNode()
                var tp: Point2D = textNode.localToScene(0, 0)
                var p: Point2D = Point2D(e.getSceneX() - tp.getX() + 10/*??*/ - pressX + caretHandle.getWidth() / 2,
                    e.getSceneY() - tp.getY() - pressY - 6)
                var hit: HitInfo = textNode.impl_hitTestChar(translateCaretPosition(p))
                var pos: Int = hit.getCharIndex()
                if (pos > 0) {
                    var oldPos: Int = textNode.getImpl_caretPosition()
                    textNode.setImpl_caretPosition(pos)
                    var element: PathElement = textNode.getImpl_caretShape()[0]
                    if (element instanceof MoveTo && ((MoveTo)element).getY() > e.getY() - getTextTranslateY()) {
                        hit.setCharIndex(pos - 1)
                    }
                    textNode.setImpl_caretPosition(oldPos)
                }
                positionCaret(hit, false, false)
                e.consume()
            })

            selectionHandle1.setOnMouseDragged(EventHandler<MouseEvent>() {
                override fun handle(e: MouseEvent) {
                    var textArea: TextAria = getSkinnable()
                    var textNode: Text = getTextNode()
                    var tp: Point2D = textNode.localToScene(0, 0)
                    var p: Point2D = Point2D(e.getSceneX() - tp.getX() + 10/*??*/ - pressX + selectionHandle1.getWidth() / 2,
                        e.getSceneY() - tp.getY() - pressY + selectionHandle1.getHeight() + 5)
                    var hit: HitInfo = textNode.impl_hitTestChar(translateCaretPosition(p))
                    var pos: Int = hit.getCharIndex()
                    if (textArea.getAnchor() < textArea.getCaretPosition()) {
                        // Swap caret and anchor
                        textArea.selectRange(textArea.getCaretPosition(), textArea.getAnchor())
                    }
                    if (pos > 0) {
                        if (pos >= textArea.getAnchor()) {
                            pos = textArea.getAnchor()
                        }
                        var oldPos: Int = textNode.getImpl_caretPosition()
                        textNode.setImpl_caretPosition(pos)
                        var element: PathElement = textNode.getImpl_caretShape()[0]
                        if (element instanceof MoveTo && ((MoveTo)element).getY() > e.getY() - getTextTranslateY()) {
                            hit.setCharIndex(pos - 1)
                        }
                        textNode.setImpl_caretPosition(oldPos)
                    }
                    positionCaret(hit, true, false)
                    e.consume()
                }
            })

            selectionHandle2.setOnMouseDragged(EventHandler<MouseEvent>() {
                override fun handle(e: MouseEvent) {
                    var textArea: TextAria = getSkinnable()
                    var textNode: Text = getTextNode()
                    var tp: Point2D = textNode.localToScene(0, 0)
                    var p: Point2D = Point2D(e.getSceneX() - tp.getX() + 10/*??*/ - pressX + selectionHandle2.getWidth() / 2,
                        e.getSceneY() - tp.getY() - pressY - 6)
                    var hit: HitInfo = textNode.impl_hitTestChar(translateCaretPosition(p))
                    var pos: Int = hit.getCharIndex()
                    if (textArea.getAnchor() > textArea.getCaretPosition()) {
                        // Swap caret and anchor
                        textArea.selectRange(textArea.getCaretPosition(), textArea.getAnchor())
                    }
                    if (pos > 0) {
                        if (pos <= textArea.getAnchor() + 1) {
                            pos = Math.min(textArea.getAnchor() + 2, textArea.getLength())
                        }
                        var oldPos: Int = textNode.getImpl_caretPosition()
                        textNode.setImpl_caretPosition(pos)
                        var element: PathElement = textNode.getImpl_caretShape()[0]
                        if (element instanceof MoveTo && ((MoveTo)element).getY() > e.getY() - getTextTranslateY()) {
                            hit.setCharIndex(pos - 1)
                        }
                        textNode.setImpl_caretPosition(oldPos)
                        positionCaret(hit, true, false)
                    }
                    e.consume()
                }
            })
        }
    }



    override protected fun invalidateMetrics() {
        computedMinWidth = Double.NEGATIVE_INFINITY
        computedMinHeight = Double.NEGATIVE_INFINITY
        computedPrefWidth = Double.NEGATIVE_INFINITY
        computedPrefHeight = Double.NEGATIVE_INFINITY
    }

    private class ContentView : Region() {
        {
            getStyleClass().add("content")

            addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                getBehavior().mousePressed(event)
                event.consume()
            })

            addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
                getBehavior().mouseReleased(event)
                event.consume()
            })

            addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
                getBehavior().mouseDragged(event)
                event.consume()
            })
        }

        override protected ObservableList<Node> getChildren() {
            return super.getChildren()
        }

        override fun getContentBias(): Orientation {
            return Orientation.HORIZONTAL
        }

        override protected fun computePrefWidth(height: Double): Double {
            if (computedPrefWidth < 0) {
                var prefWidth: Double = 0

                for (var node: Node : paragraphNodes.getChildren()) {
                    var paragraphNode: Text = (Text)node
                    prefWidth = Math.max(prefWidth,
                            Utils.computeTextWidth(paragraphNode.getFont(),
                                    paragraphNode.getText(), 0))
                }

                prefWidth += snappedLeftInset() + snappedRightInset()

                computedPrefWidth = prefWidth
            }
            return computedPrefWidth
        }

        override protected fun computePrefHeight(width: Double): Double {
            if (width != widthForComputedPrefHeight) {
                invalidateMetrics()
                widthForComputedPrefHeight = width
            }

            if (computedPrefHeight < 0) {
                var wrappingWidth: Double
                if (width == -1) {
                    wrappingWidth = 0
                } else {
                    wrappingWidth = Math.max(width - (snappedLeftInset() + snappedRightInset()), 0)
                }

                var prefHeight: Double = 0

                for (var node: Node : paragraphNodes.getChildren()) {
                    var paragraphNode: Text = (Text)node
                    prefHeight += Utils.computeTextHeight(
                            paragraphNode.getFont(),
                            paragraphNode.getText(),
                            wrappingWidth,
                            paragraphNode.getBoundsType())
                }

                prefHeight += snappedTopInset() + snappedBottomInset()

                computedPrefHeight = prefHeight
            }
            return computedPrefHeight
        }

        override protected fun computeMinWidth(height: Double): Double {
            if (computedMinWidth < 0) {
                var hInsets: Double = snappedLeftInset() + snappedRightInset()
                computedMinWidth = Math.min(characterWidth + hInsets, computePrefWidth(height))
            }
            return computedMinWidth
        }

        override protected fun computeMinHeight(width: Double): Double {
            if (computedMinHeight < 0) {
                var vInsets: Double = snappedTopInset() + snappedBottomInset()
                computedMinHeight = Math.min(lineHeight + vInsets, computePrefHeight(width))
            }
            return computedMinHeight
        }

        override fun layoutChildren() {
            var textArea: TextAria = getSkinnable()
            var width: Double = getWidth()
            //System.out.println("HERE width: "+width);

            // Lay out paragraphs
            val topPadding: Double = snappedTopInset()
            val leftPadding: Double = snappedLeftInset()

            var wrappingWidth: Double = Math.max(width - (leftPadding + snappedRightInset()), 0)

            var y: Double = topPadding
            //System.out.println("HERE  snappedTopInset: "+y);

            final List<Node> paragraphNodesChildren = paragraphNodes.getChildren()

            for (var i: Int = 0; i < paragraphNodesChildren.size(); i++) {
                var node: Node = paragraphNodesChildren.get(i)
                var paragraphNode: Text = (Text)node
                paragraphNode.setWrappingWidth(wrappingWidth)

                var bounds: Bounds = paragraphNode.getBoundsInLocal()
                paragraphNode.setLayoutX(leftPadding)
                paragraphNode.setLayoutY(y)
                y += bounds.getHeight()
            }

            //notify as a property from here
            //System.out.println("HERE  contentView 'y': "+y);
            //System.out.println("HERE  textArea.prefHeight BEFORE: "+textArea.getPrefHeight());
            hajt.set(y)

            if (promptNode != null) {
                promptNode.setLayoutX(leftPadding)
                promptNode.setLayoutY(topPadding + promptNode.getBaselineOffset())
                promptNode.setWrappingWidth(wrappingWidth)
            }

            // Update the selection
            var selection: IndexRange = textArea.getSelection()
            var oldCaretBounds: Bounds = caretPath.getBoundsInParent()

            selectionHighlightGroup.getChildren().clear()

            var caretPos: Int = textArea.getCaretPosition()
            var anchorPos: Int = textArea.getAnchor()

            if (SHOW_HANDLES) {
                // Install and resize the handles for caret and anchor.
                if (selection.getLength() > 0) {
                    selectionHandle1.resize(selectionHandle1.prefWidth(-1),
                            selectionHandle1.prefHeight(-1))
                    selectionHandle2.resize(selectionHandle2.prefWidth(-1),
                            selectionHandle2.prefHeight(-1))
                } else {
                    caretHandle.resize(caretHandle.prefWidth(-1),
                            caretHandle.prefHeight(-1))
                }

                // Position the handle for the anchor. This could be handle1 or handle2.
                // Do this before positioning the actual caret.
                if (selection.getLength() > 0) {
                    var paragraphIndex: Int = paragraphNodesChildren.size()
                    var paragraphOffset: Int = textArea.getLength() + 1
                    var paragraphNode: Text = null
                    do {
                        paragraphNode = (Text)paragraphNodesChildren.get(--paragraphIndex)
                        paragraphOffset -= paragraphNode.getText().length() + 1
                    } while (anchorPos < paragraphOffset)

                    updateTextNodeCaretPos(anchorPos - paragraphOffset)
                    caretPath.getElements().clear()
                    caretPath.getElements().addAll(paragraphNode.getImpl_caretShape())
                    caretPath.setLayoutX(paragraphNode.getLayoutX())
                    caretPath.setLayoutY(paragraphNode.getLayoutY())

                    var b: Bounds = caretPath.getBoundsInParent()
                    if (caretPos < anchorPos) {
                        selectionHandle2.setLayoutX(b.getMinX() - selectionHandle2.getWidth() / 2)
                        selectionHandle2.setLayoutY(b.getMaxY() - 1)
                    } else {
                        selectionHandle1.setLayoutX(b.getMinX() - selectionHandle1.getWidth() / 2)
                        selectionHandle1.setLayoutY(b.getMinY() - selectionHandle1.getHeight() + 1)
                    }
                }
            }

            {
                // Position caret
                var paragraphIndex: Int = paragraphNodesChildren.size()
                var paragraphOffset: Int = textArea.getLength() + 1

                var paragraphNode: Text = null
                do {
                    paragraphNode = (Text)paragraphNodesChildren.get(--paragraphIndex)
                    paragraphOffset -= paragraphNode.getText().length() + 1
                } while (caretPos < paragraphOffset)

                updateTextNodeCaretPos(caretPos - paragraphOffset)

                caretPath.getElements().clear()
                caretPath.getElements().addAll(paragraphNode.getImpl_caretShape())

                caretPath.setLayoutX(paragraphNode.getLayoutX())

                // TODO: Remove this temporary workaround for RT-27533
                paragraphNode.setLayoutX(2 * paragraphNode.getLayoutX() - paragraphNode.getBoundsInParent().getMinX())

                caretPath.setLayoutY(paragraphNode.getLayoutY())
                if (oldCaretBounds == null || !oldCaretBounds.equals(caretPath.getBoundsInParent())) {
                    scrollCaretToVisible()
                }
            }

            // Update selection fg and bg
            var start: Int = selection.getStart()
            var end: Int = selection.getEnd()
            for (var i: Int = 0, max = paragraphNodesChildren.size(); i < max; i++) {
                var paragraphNode: Node = paragraphNodesChildren.get(i)
                var textNode: Text = (Text)paragraphNode
                var paragraphLength: Int = textNode.getText().length() + 1
                if (end > start && start < paragraphLength) {
                    textNode.setImpl_selectionStart(start)
                    textNode.setImpl_selectionEnd(Math.min(end, paragraphLength))

                    var selectionHighlightPath: Path = Path()
                    selectionHighlightPath.setManaged(false)
                    selectionHighlightPath.setStroke(null)
                    var selectionShape: PathElement[] = textNode.getImpl_selectionShape()
                    if (selectionShape != null) {
                        selectionHighlightPath.getElements().addAll(selectionShape)
                    }
                    selectionHighlightGroup.getChildren().add(selectionHighlightPath)
                    selectionHighlightGroup.setVisible(true)
                    selectionHighlightPath.setLayoutX(textNode.getLayoutX())
                    selectionHighlightPath.setLayoutY(textNode.getLayoutY())
                    updateHighlightFill()
                } else {
                    textNode.setImpl_selectionStart(-1)
                    textNode.setImpl_selectionEnd(-1)
                    selectionHighlightGroup.setVisible(false)
                }
                start = Math.max(0, start - paragraphLength)
                end   = Math.max(0, end   - paragraphLength)
            }

            if (SHOW_HANDLES) {
                // Position handle for the caret. This could be handle1 or handle2 when
                // a selection is active.
                var b: Bounds = caretPath.getBoundsInParent()
                if (selection.getLength() > 0) {
                    if (caretPos < anchorPos) {
                        selectionHandle1.setLayoutX(b.getMinX() - selectionHandle1.getWidth() / 2)
                        selectionHandle1.setLayoutY(b.getMinY() - selectionHandle1.getHeight() + 1)
                    } else {
                        selectionHandle2.setLayoutX(b.getMinX() - selectionHandle2.getWidth() / 2)
                        selectionHandle2.setLayoutY(b.getMaxY() - 1)
                    }
                } else {
                    caretHandle.setLayoutX(b.getMinX() - caretHandle.getWidth() / 2 + 1)
                    caretHandle.setLayoutY(b.getMaxY())
                }
            }

            if (contentView.getPrefWidth() == 0 || contentView.getPrefHeight() == 0) {
                updatePrefViewportWidth()
                updatePrefViewportHeight()
                if (getParent() != null && contentView.getPrefWidth() > 0
                        || contentView.getPrefHeight() > 0) {
                    // Force layout of viewRect in ScrollPaneSkin
                    getParent().requestLayout()
                }
            }
        }

        var hajt: DoubleProperty = DoublePropertyBase() {
            override fun getBean(): Object {
                return null
            }

            override fun getName(): String {
                return "my fucking height"
            }
        }
    }

    override protected fun layoutChildren(contentX: Double, contentY: Double, contentWidth: Double, contentHeight: Double) {
        contentView.resizeRelocate(contentX, contentY, contentWidth, contentHeight)
    }

    private fun createPromptNode() {
        if (promptNode == null && usePromptText.get()) {
            promptNode = Text()
            contentView.getChildren().add(0, promptNode)
            promptNode.setManaged(false)
            promptNode.getStyleClass().add("text")
            promptNode.visibleProperty().bind(usePromptText)
            promptNode.fontProperty().bind(getSkinnable().fontProperty())
            promptNode.textProperty().bind(getSkinnable().promptTextProperty())
            promptNode.fillProperty().bind(promptTextFill)
        }
    }

    private fun addParagraphNode(i: Int, string: String) {
        val textArea: TextAria = getSkinnable()
        var paragraphNode: Text = Text(string)
        paragraphNode.setTextOrigin(VPos.TOP)
        paragraphNode.setManaged(false)
        paragraphNode.getStyleClass().add("text")
        paragraphNode.boundsTypeProperty().addListener((observable, oldValue, newValue) -> {
            invalidateMetrics()
            updateFontMetrics()
        })
        paragraphNodes.getChildren().add(i, paragraphNode)

        paragraphNode.fontProperty().bind(textArea.fontProperty())
        paragraphNode.fillProperty().bind(textFill)
        paragraphNode.impl_selectionFillProperty().bind(highlightTextFill)
    }

    override fun dispose() {
        // TODO Unregister listeners on text editor, paragraph list
        throw UnsupportedOperationException()
    }

    override fun computeBaselineOffset(topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double {
        var firstParagraph: Text = (Text) paragraphNodes.getChildren().get(0)
        return Utils.getAscent(getSkinnable().getFont(),firstParagraph.getBoundsType())
                + contentView.snappedTopInset() + textArea.snappedTopInset()
    }

    override fun getCharacter(index:Int):Char {
        var n: Int = paragraphNodes.getChildren().size()

        var paragraphIndex: Int = 0
        var offset: Int = index

        var paragraph: String = null
        while (paragraphIndex < n) {
            var paragraphNode: Text = (Text)paragraphNodes.getChildren().get(paragraphIndex)
            paragraph = paragraphNode.getText()
            var count: Int = paragraph.length() + 1

            if (offset < count) {
                break
            }

            offset -= count
            paragraphIndex++
        }

        return offset == paragraph.length() ? '\n' : paragraph.charAt(offset)
    }

    override fun getInsertionPoint(x:Double, y:Double):Int {
        var textArea: TextAria = getSkinnable()

        var n: Int = paragraphNodes.getChildren().size()
        var index: Int = -1

        if (n > 0) {
            if (y < contentView.snappedTopInset()) {
                // Select the character at x in the first row
                var paragraphNode: Text = (Text)paragraphNodes.getChildren().get(0)
                index = getNextInsertionPoint(paragraphNode, x, -1, VerticalDirection.DOWN)
            } else if (y > contentView.snappedTopInset() + contentView.getHeight()) {
                // Select the character at x in the last row
                var lastParagraphIndex: Int = n - 1
                var lastParagraphView: Text = (Text)paragraphNodes.getChildren().get(lastParagraphIndex)

                index = getNextInsertionPoint(lastParagraphView, x, -1, VerticalDirection.UP)
                        + (textArea.getLength() - lastParagraphView.getText().length())
            } else {
                // Select the character at x in the row at y
                var paragraphOffset: Int = 0
                for (var i: Int = 0; i < n; i++) {
                    var paragraphNode: Text = (Text)paragraphNodes.getChildren().get(i)

                    var bounds: Bounds = paragraphNode.getBoundsInLocal()
                    var paragraphViewY: Double = paragraphNode.getLayoutY() + bounds.getMinY()
                    if (y >= paragraphViewY
                            && y < paragraphViewY + paragraphNode.getBoundsInLocal().getHeight()) {
                        index = getInsertionPoint(paragraphNode,
                                x - paragraphNode.getLayoutX(),
                                y - paragraphNode.getLayoutY()) + paragraphOffset
                        break
                    }

                    paragraphOffset += paragraphNode.getText().length() + 1
                }
            }
        }

        return index
    }

    fun positionCaret(hit: HitInfo, select: Boolean, extendSelection: Boolean) {
        var pos: Int = Utils.getHitInsertionIndex(hit, getSkinnable().getText())
        var isNewLine: Boolean =
                (pos > 0 &&
                        pos <= getSkinnable().getLength() &&
                        getSkinnable().getText().codePointAt(pos-1) == 0x0a)

        // special handling for a line
        if (!hit.isLeading() && isNewLine) {
            hit.setLeading(true)
            pos -= 1
        }

        if (select) {
            if (extendSelection) {
                getSkinnable().extendSelection(pos)
            } else {
                getSkinnable().selectPositionCaret(pos)
            }
        } else {
            getSkinnable().positionCaret(pos)
        }

        setForwardBias(hit.isLeading())
    }

    private fun getInsertionPoint(paragraphNode: Text, x: Double, y: Double):Int {
        var hitInfo: HitInfo = paragraphNode.impl_hitTestChar(Point2D(x, y))
        return Utils.getHitInsertionIndex(hitInfo, paragraphNode.getText())
    }

    fun getNextInsertionPoint(x: Double, from: Int, scrollDirection: VerticalDirection):Int {
        // TODO
        return 0
    }

    private fun getNextInsertionPoint(paragraphNode: Text, x: Double, from: Int,
                                      scrollDirection: VerticalDirection):Int {
        // TODO
        return 0
    }

    override fun getCharacterBounds(index: Int): Rectangle2D {
        var textArea: TextAria = getSkinnable()

        var paragraphIndex: Int = paragraphNodes.getChildren().size()
        var paragraphOffset: Int = textArea.getLength() + 1

        var paragraphNode: Text = null
        do {
            paragraphNode = (Text)paragraphNodes.getChildren().get(--paragraphIndex)
            paragraphOffset -= paragraphNode.getText().length() + 1
        } while (index < paragraphOffset)

        var characterIndex: Int = index - paragraphOffset
        var terminator: Boolean = false

        if (characterIndex == paragraphNode.getText().length()) {
            characterIndex--
            terminator = true
        }

        characterBoundingPath.getElements().clear()
        characterBoundingPath.getElements().addAll(paragraphNode.impl_getRangeShape(characterIndex, characterIndex + 1))
        characterBoundingPath.setLayoutX(paragraphNode.getLayoutX())
        characterBoundingPath.setLayoutY(paragraphNode.getLayoutY())

        var bounds: Bounds = characterBoundingPath.getBoundsInLocal()

        var x: Double = bounds.getMinX() + paragraphNode.getLayoutX() - textArea.getScrollLeft()
        var y: Double = bounds.getMinY() + paragraphNode.getLayoutY() - textArea.getScrollTop()

        // Sometimes the bounds is empty, in which case we must ignore the width/height
        var width: Double = bounds.isEmpty() ? 0 : bounds.getWidth()
        var height: Double = bounds.isEmpty() ? 0 : bounds.getHeight()

        if (terminator) {
            x += width
            width = 0
        }

        return Rectangle2D(x, y, width, height)
    }

    override fun scrollCharacterToVisible(final index: Int) {
        //TODO We queue a callback because when characters are added or
        // removed the bounds are not immediately updated; is this really
        // necessary?

        Platform.runLater(() -> {
            if (getSkinnable().getLength() == 0) {
                return
            }
            var characterBounds: Rectangle2D = getCharacterBounds(index)
        })
    }

    private fun scrollCaretToVisible() {
        var textArea: TextAria = getSkinnable()
        var bounds: Bounds = caretPath.getLayoutBounds()
        var x: Double = bounds.getMinX() - textArea.getScrollLeft()
        var y: Double = bounds.getMinY() - textArea.getScrollTop()
        var w: Double = bounds.getWidth()
        var h: Double = bounds.getHeight()

        if (SHOW_HANDLES) {
            if (caretHandle.isVisible()) {
                h += caretHandle.getHeight()
            } else if (selectionHandle1.isVisible() && selectionHandle2.isVisible()) {
                x -= selectionHandle1.getWidth() / 2
                y -= selectionHandle1.getHeight()
                w += selectionHandle1.getWidth() / 2 + selectionHandle2.getWidth() / 2
                h += selectionHandle1.getHeight() + selectionHandle2.getHeight()
            }
        }
    }

    private fun updatePrefViewportWidth() {
        var columnCount: Int = getSkinnable().getPrefColumnCount()
        contentView.setPrefWidth(columnCount * characterWidth + contentView.snappedLeftInset() + contentView.snappedRightInset())
        contentView.setMinWidth(characterWidth + contentView.snappedLeftInset() + contentView.snappedRightInset())
    }

    private fun updatePrefViewportHeight() {
        var rowCount: Int = getSkinnable().getPrefRowCount()
        contentView.setPrefHeight(rowCount * lineHeight + contentView.snappedTopInset() + contentView.snappedBottomInset())
        contentView.setMinHeight(lineHeight + contentView.snappedTopInset() + contentView.snappedBottomInset())
    }

    private fun updateFontMetrics() {
        var firstParagraph: Text = (Text)paragraphNodes.getChildren().get(0)
        lineHeight = Utils.getLineHeight(getSkinnable().getFont(),firstParagraph.getBoundsType())
        characterWidth = fontMetrics.get().computeStringWidth("W")
    }

    override protected fun updateHighlightFill() {
        for (var node: Node : selectionHighlightGroup.getChildren()) {
            var selectionHighlightPath: Path = (Path)node
            selectionHighlightPath.setFill(highlightFill.get())
        }
    }

//     protected fun handleMouseReleasedEvent(MouseEvent event) {
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

    private fun getTextTranslateX():Double {
        return contentView.snappedLeftInset()
    }

    private fun getTextTranslateY():Double {
        return contentView.snappedTopInset()
    }

    private fun getTextLeft():Double {
        return 0
    }

    private fun translateCaretPosition(p: Point2D): Point2D {
        return p
    }

    private fun getTextNode(): Text {
        if (USE_MULTIPLE_NODES) {
            throw IllegalArgumentException("Multiple node traversal is not yet implemented.")
        }
        return (Text)paragraphNodes.getChildren().get(0)
    }

    fun getIndex(x: Double, y: Double): HitInfo {
        // adjust the event to be in the same coordinate space as the
        // text content of the textInputControl
        var textNode: Text = getTextNode()
        var p: Point2D = Point2D(x - textNode.getLayoutX(), y - getTextTranslateY())
        var hit: HitInfo = textNode.impl_hitTestChar(translateCaretPosition(p))
        var pos: Int = hit.getCharIndex()
        if (pos > 0) {
            var oldPos: Int = textNode.getImpl_caretPosition()
            textNode.setImpl_caretPosition(pos)
            var element: PathElement = textNode.getImpl_caretShape()[0]
            if (element instanceof MoveTo && ((MoveTo)element).getY() > y - getTextTranslateY()) {
                hit.setCharIndex(pos - 1)
            }
            textNode.setImpl_caretPosition(oldPos)
        }
        return hit
    }

    /**
     * Remembers horizontal position when traversing up / down.
     */
    var targetCaretX: Double = -1

    override fun nextCharacterVisually(moveRight: Boolean) {
        if (isRTL()) {
            // Text node is mirrored.
            moveRight = !moveRight
        }

        var textNode: Text = getTextNode()
        var caretBounds: Bounds = caretPath.getLayoutBounds()
        if (caretPath.getElements().size() == 4) {
            // The caret is split
            // TODO: Find a better way to get the primary caret position
            // instead of depending on the internal implementation.
            // See RT-25465.
            caretBounds = Path(caretPath.getElements().get(0), caretPath.getElements().get(1)).getLayoutBounds()
        }
        var hitX: Double = moveRight ? caretBounds.getMaxX() : caretBounds.getMinX()
        var hitY: Double = (caretBounds.getMinY() + caretBounds.getMaxY()) / 2
        var hit: HitInfo = textNode.impl_hitTestChar(Point2D(hitX, hitY))
        var charShape: Path = Path(textNode.impl_getRangeShape(hit.getCharIndex(), hit.getCharIndex() + 1))
        if ((moveRight && charShape.getLayoutBounds().getMaxX() > caretBounds.getMaxX()) ||
                (!moveRight && charShape.getLayoutBounds().getMinX() < caretBounds.getMinX())) {
            hit.setLeading(!hit.isLeading())
            positionCaret(hit, false, false)
        } else {
            // We're at beginning or end of line. Try moving up / down.
            var dot: Int = textArea.getCaretPosition()
            targetCaretX = moveRight ? 0 : Double.MAX_VALUE
            // TODO: Use Bidi sniffing instead of assuming right means forward here?
            downLines(moveRight ? 1 : -1, false, false)
            targetCaretX = -1
            if (dot == textArea.getCaretPosition()) {
                if (moveRight) {
                    textArea.forward()
                } else {
                    textArea.backward()
                }
            }
        }
    }

    /** A shared helper object, used only by downLines(). */
    private static val tmpCaretPath: Path = Path()

    protected fun downLines(nLines: Int, select: Boolean, extendSelection: Boolean) {
        var textNode: Text = getTextNode()
        var caretBounds: Bounds = caretPath.getLayoutBounds()

        // The middle y coordinate of the the line we want to go to.
        var targetLineMidY: Double = (caretBounds.getMinY() + caretBounds.getMaxY()) / 2 + nLines * lineHeight
        if (targetLineMidY < 0) {
            targetLineMidY = 0
        }

        // The target x for the caret. This may have been set during a
        // previous call.
        var x: Double = (targetCaretX >= 0) ? targetCaretX : (caretBounds.getMaxX())

        // Find a text position for the target x,y.
        var hit: HitInfo = textNode.impl_hitTestChar(translateCaretPosition(Point2D(x, targetLineMidY)))
        var pos: Int = hit.getCharIndex()

        // Save the old pos temporarily while testing the one.
        var oldPos: Int = textNode.getImpl_caretPosition()
        var oldBias: Boolean = textNode.isImpl_caretBias()
        textNode.setImpl_caretBias(hit.isLeading())
        textNode.setImpl_caretPosition(pos)
        tmpCaretPath.getElements().clear()
        tmpCaretPath.getElements().addAll(textNode.getImpl_caretShape())
        tmpCaretPath.setLayoutX(textNode.getLayoutX())
        tmpCaretPath.setLayoutY(textNode.getLayoutY())
        var tmpCaretBounds: Bounds = tmpCaretPath.getLayoutBounds()
        // The y for the middle of the row we found.
        var foundLineMidY: Double = (tmpCaretBounds.getMinY() + tmpCaretBounds.getMaxY()) / 2
        textNode.setImpl_caretBias(oldBias)
        textNode.setImpl_caretPosition(oldPos)

        if (pos > 0) {
            if (nLines > 0 && foundLineMidY > targetLineMidY) {
                // We went too far and ended up after a newline.
                hit.setCharIndex(pos - 1)
            }

            if (pos >= textArea.getLength() && getCharacter(pos - 1) == '\n') {
                // Special case for newline at end of text.
                hit.setLeading(true)
            }
        }

        // Test if the found line is in the correct direction and move
        // the caret.
        if (nLines == 0 ||
                (nLines > 0 && foundLineMidY > caretBounds.getMaxY()) ||
                (nLines < 0 && foundLineMidY < caretBounds.getMinY())) {

            positionCaret(hit, select, extendSelection)
            targetCaretX = x
        }
    }

    fun previousLine(select: Boolean) {
        downLines(-1, select, false)
    }

    fun nextLine(select: Boolean) {
        downLines(1, select, false)
    }

    fun lineStart(select: Boolean, extendSelection: Boolean) {
        targetCaretX = 0
        downLines(0, select, extendSelection)
        targetCaretX = -1
    }

    fun lineEnd(select: Boolean, extendSelection: Boolean) {
        targetCaretX = Double.MAX_VALUE
        downLines(0, select, extendSelection)
        targetCaretX = -1
    }


    fun paragraphStart(previousIfAtStart: Boolean, select: Boolean) {
        var textArea: TextAria = getSkinnable()
        var text: String = textArea.textProperty().getValueSafe()
        var pos: Int = textArea.getCaretPosition()

        if (pos > 0) {
            if (previousIfAtStart && text.codePointAt(pos-1) == 0x0a) {
                // We are at the beginning of a paragraph.
                // Back up to the previous paragraph.
                pos--
            }
            // Back up to the beginning of this paragraph
            while (pos > 0 && text.codePointAt(pos-1) != 0x0a) {
                pos--
            }
            if (select) {
                textArea.selectPositionCaret(pos)
            } else {
                textArea.positionCaret(pos)
            }
        }
    }

    fun paragraphEnd(goPastInitialNewline: Boolean, goPastTrailingNewline: Boolean, select: Boolean) {
        var textArea: TextAria = getSkinnable()
        var text: String = textArea.textProperty().getValueSafe()
        var pos: Int = textArea.getCaretPosition()
        var len: Int = text.length()
        var wentPastInitialNewline: Boolean = false

        if (pos < len) {
            if (goPastInitialNewline && text.codePointAt(pos) == 0x0a) {
                // We are at the end of a paragraph, start by moving to the
                // next paragraph.
                pos++
                wentPastInitialNewline = true
            }
            if (!(goPastTrailingNewline && wentPastInitialNewline)) {
                // Go to the end of this paragraph
                while (pos < len && text.codePointAt(pos) != 0x0a) {
                    pos++
                }
                if (goPastTrailingNewline && pos < len) {
                    // We are at the end of a paragraph, finish by moving to
                    // the beginning of the next paragraph (Windows behavior).
                    pos++
                }
            }
            if (select) {
                textArea.selectPositionCaret(pos)
            } else {
                textArea.positionCaret(pos)
            }
        }
    }

    private fun updateTextNodeCaretPos(pos: Int) {
        var textNode: Text = getTextNode()
        if (isForwardBias()) {
            textNode.setImpl_caretPosition(pos)
        } else {
            textNode.setImpl_caretPosition(pos - 1)
        }
        textNode.impl_caretBiasProperty().set(isForwardBias())
    }

    override protected fun getUnderlineShape(start: Int, end: Int):Array<PathElement> {
        var pStart: Int = 0
        for (node: Node in paragraphNodes.getChildren()) {
            var p: Text = (Text)node
            var pEnd: Int = pStart + p.textProperty().getValueSafe().length()
            if (pEnd >= start) {
                return p.impl_getUnderlineShape(start - pStart, end - pStart)
            }
            pStart = pEnd + 1
        }
        return null
    }

    override protected fun getRangeShape(start: Int, end: Int):Array<PathElement> {
        var pStart: Int = 0
        for (node: Node in paragraphNodes.getChildren()) {
            var p: Text = (Text)node
            var pEnd: Int = pStart + p.textProperty().getValueSafe().length()
            if (pEnd >= start) {
                return p.impl_getRangeShape(start - pStart, end - pStart)
            }
            pStart = pEnd + 1
        }
        return null
    }

    override protected fun addHighlight(List<? : Node> nodes, start: Int) {
        var pStart: Int = 0
        var paragraphNode: Text = null
        for (node: Node in paragraphNodes.getChildren()) {
            var p: Text = (Text)node
            var pEnd: Int = pStart + p.textProperty().getValueSafe().length()
            if (pEnd >= start) {
                paragraphNode = p
                break
            }
            pStart = pEnd + 1
        }

        if (paragraphNode != null) {
            for (node: Node in nodes) {
                node.setLayoutX(paragraphNode.getLayoutX())
                node.setLayoutY(paragraphNode.getLayoutY())
            }
        }
        contentView.getChildren().addAll(nodes)
    }

    override protected fun removeHighlight(List<? : Node> nodes) {
        contentView.getChildren().removeAll(nodes)
    }

    /**
     * Use this implementation instead of the one provided on TextInputControl
     * Simply calls into TextInputControl.deletePrevious/NextChar and responds appropriately
     * based on the return value.
     */
    fun deleteChar(previous: Boolean) {
//        final double textMaxXOld = textNode.getBoundsInParent().getMaxX();
//        final double caretMaxXOld = caretPath.getLayoutBounds().getMaxX() + textTranslateX.get();
        val shouldBeep: Boolean = previous ?
                !getSkinnable().deletePreviousChar() :
                !getSkinnable().deleteNextChar()

        if (shouldBeep) {
//            beep();
        } else {
//            scrollAfterDelete(textMaxXOld, caretMaxXOld);
        }
    }

    override fun getMenuPosition(): Point2D {
        contentView.layoutChildren()
        var p: Point2D = super.getMenuPosition()
        if (p != null) {
            p = Point2D(Math.max(0, p.getX() - contentView.snappedLeftInset() - getSkinnable().getScrollLeft()),
                    Math.max(0, p.getY() - contentView.snappedTopInset() - getSkinnable().getScrollTop()))
        }
        return p
    }

    fun getCaretBounds(): Bounds {
        return getSkinnable().sceneToLocal(caretPath.localToScene(caretPath.getBoundsInLocal()))
    }

    override protected fun queryAccessibleAttribute(attribute: AccessibleAttribute, Object... parameters): Object {
        switch (attribute) {
            case LINE_FOR_OFFSET:
            case LINE_START:
            case LINE_END:
            case BOUNDS_FOR_RANGE:
            case OFFSET_AT_POINT:
                var text: Text = getTextNode()
                return text.queryAccessibleAttribute(attribute, parameters)
            default: return super.queryAccessibleAttribute(attribute, parameters)
        }
    }
}
