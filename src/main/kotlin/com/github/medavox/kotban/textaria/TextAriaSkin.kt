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

/**
 * A fork of JavaFX 8's Text area skin. Its internal scrollPane (and corresponding functionality) has been removed,
 * and a public Property for its actual display-time added, for computing its correct height
 * (given the number of lines its TextAria contains, and their wrapping).
 */
class TextAriaSkin(private val textArea:TextAria) : TextInputControlSkin<TextAria, TextAriaBehavior>(textArea, TextAriaBehavior(textArea)) {
    private var computedMinWidth: Double = Double.NEGATIVE_INFINITY
    private var computedMinHeight: Double = Double.NEGATIVE_INFINITY
    private var computedPrefWidth: Double = Double.NEGATIVE_INFINITY
    private var computedPrefHeight: Double = Double.NEGATIVE_INFINITY
    private var widthForComputedPrefHeight: Double = Double.NEGATIVE_INFINITY
    private var characterWidth: Double = 0.0
    private var lineHeight: Double = 0.0
    private var contentView: ContentView = ContentView()
    val displayTimeHeight: DoubleProperty = contentView.hajt
    private var paragraphNodes: Group = Group()

    private var promptNode: Text? = null
    private var usePromptText: ObservableBooleanValue

    private var caretPosition: ObservableIntegerValue = object:IntegerBinding() {
        init{ bind(textArea.caretPositionProperty()) }
        override protected fun computeValue(): Int {
            return textArea.getCaretPosition()
        }
    }
    private var selectionHighlightGroup: Group = Group()

    private var characterBoundingPath: Path = Path()

    // For dragging handles on embedded
    private var pressX: Double = 0.0
    private var pressY: Double = 0.0
    private var handlePressed: Boolean = false

    /*this is not an ideal solution, but it prevents an unreproducible runtime Exception:
    Exception in thread "JavaFX Application Thread" java.lang.IllegalAccessError:
    tried to access field com.sun.javafx.scene.control.skin.TextInputControlSkin.SHOW_HANDLES
    from class com.github.medavox.kotban.textaria.TextAriaSkin$ContentView
	at com.github.medavox.kotban.textaria.TextAriaSkin$ContentView.layoutChildren(TextAriaSkin.kt:516)*/
    private val SHOW_HANDLES = TextInputControlSkin.SHOW_HANDLES

    /**
     * Remembers horizontal position when traversing up / down.
     */
    var targetCaretX: Double = -1.0
    /** A shared helper object, used only by downLines(). */
    private /*static*/ val tmpCaretPath: Path = Path()

    init {
        behavior.skin = this//sadly needed AFAICS, to prevent null pointer exceptions.
        //the source of the problem is the design of the super classes:
        // TextInputControl -Skin and -Behavior need references to instances of each other
        //but that is JavaFX 8 code, so nothing can be done without importing & forking even more of it
        caretPosition.addListener { _, oldValue, newValue ->
            targetCaretX = -1.0
            if (newValue.toInt() > oldValue.toInt()) {
                isForwardBias = true
            }
        }

        forwardBiasProperty().addListener { _ ->
            if (textArea.width > 0) {
                updateTextNodeCaretPos(textArea.caretPosition)
            }
        }

        // Initialize content
        children.add(contentView)

        skinnable.addEventFilter(ScrollEvent.ANY) { event ->
            if (event.isDirect && handlePressed) {
                event.consume()
            }
        }

        // Add selection
        selectionHighlightGroup.isManaged = false
        selectionHighlightGroup.isVisible = false
        contentView.children.add(selectionHighlightGroup)

        // Add content view
        paragraphNodes.isManaged = false
        contentView.children.add(paragraphNodes)

        // Add caret
        caretPath.isManaged = false
        caretPath.strokeWidth = 1.0
        caretPath.fillProperty().bind(textFill)
        caretPath.strokeProperty().bind(textFill)
        // modifying visibility of the caret forces a layout-pass (RT-32373), so
        // instead we modify the opacity.
        caretPath.opacityProperty().bind(object:DoubleBinding() {
            init{ bind(caretVisible) }
            override protected fun computeValue(): Double {
                return if(caretVisible.get()) 1.0 else 0.0
            }
        })
        contentView.children.add(caretPath)

        if (SHOW_HANDLES) {
            contentView.children.addAll(caretHandle, selectionHandle1, selectionHandle2)
        }

        // Add initial text content
        val paragraph: CharSequence = textArea.textProperty().valueSafe
        addParagraphNode(0, paragraph.toString())

        textArea.selectionProperty().addListener { _, _, _ ->
            // TODO Why do we need two calls here?
            textArea.requestLayout()
            contentView.requestLayout()
        }

        textArea.wrapTextProperty().addListener { _, _, _ ->
            invalidateMetrics()
        }

        textArea.prefColumnCountProperty().addListener { _, _, _ ->
            invalidateMetrics()
            updatePrefViewportWidth()
        }

        textArea.prefRowCountProperty().addListener { _, _, _ ->
            invalidateMetrics()
            updatePrefViewportHeight()
        }

        updateFontMetrics()
        fontMetrics.addListener{ _ ->
            updateFontMetrics()
        }

        contentView.paddingProperty().addListener { _ ->
            updatePrefViewportWidth()
            updatePrefViewportHeight()
        }

        textArea.textProperty().addListener { _ ->
            invalidateMetrics()
            (paragraphNodes.children[0] as Text).text = textArea.textProperty().valueSafe
            contentView.requestLayout()
        }

        usePromptText = object:BooleanBinding() {
            init{ bind(textArea.textProperty(), textArea.promptTextProperty()); }
            override protected fun computeValue(): Boolean {
                val txt: String? = textArea.text
                val promptTxt: String? = textArea.promptText
                return ((txt == null || txt.isEmpty()) &&
                        promptTxt != null && promptTxt.isNotEmpty())
            }
        }

        if (usePromptText.get()) {
            createPromptNode()
        }

        usePromptText.addListener { _ ->
            createPromptNode()
            textArea.requestLayout()
        }

        updateHighlightFill()
        updatePrefViewportWidth()
        updatePrefViewportHeight()
        if (textArea.isFocused) setCaretAnimating(true)

        if (SHOW_HANDLES) {
            selectionHandle1.rotate = 180.0

            val handlePressHandler:EventHandler<MouseEvent> =  EventHandler<MouseEvent>{e ->
                pressX = e.x
                pressY = e.y
                handlePressed = true
                e.consume()
            }

            val handleReleaseHandler:EventHandler<MouseEvent> =  EventHandler<MouseEvent>{event ->
                handlePressed = false
            }

            caretHandle.onMousePressed = handlePressHandler
            selectionHandle1.onMousePressed = handlePressHandler
            selectionHandle2.onMousePressed = handlePressHandler

            caretHandle.onMouseReleased = handleReleaseHandler
            selectionHandle1.onMouseReleased = handleReleaseHandler
            selectionHandle2.onMouseReleased = handleReleaseHandler

            caretHandle.setOnMouseDragged {e ->
                val textNode: Text = getTextNode()
                val tp: Point2D = textNode.localToScene(0.0, 0.0)
                val p = Point2D(e.sceneX - tp.x + 10/*??*/ - pressX + caretHandle.width / 2,
                    e.sceneY - tp.y - pressY - 6)
                val hit: HitInfo = textNode.impl_hitTestChar(p)
                val pos: Int = hit.charIndex
                if (pos > 0) {
                    val oldPos: Int = textNode.impl_caretPosition
                    textNode.impl_caretPosition = pos
                    val element: PathElement = textNode.impl_caretShape[0]
                    if (element is MoveTo && (element as MoveTo).y > e.y - contentView.snappedTopInset()) {
                        hit.charIndex = pos - 1
                    }
                    textNode.impl_caretPosition = oldPos
                }
                positionCaret(hit, select = false, extendSelection = false)
                e.consume()
            }

            selectionHandle1.setOnMouseDragged { e ->
                val textArea: TextAria = skinnable
                val textNode: Text = getTextNode()
                val tp: Point2D = textNode.localToScene(0.0, 0.0)
                val p = Point2D(e.sceneX - tp.x + 10/*??*/ - pressX + selectionHandle1.width / 2,
                    e.sceneY - tp.y - pressY + selectionHandle1.height + 5)
                val hit: HitInfo = textNode.impl_hitTestChar(p)
                var pos: Int = hit.charIndex
                if (textArea.anchor < textArea.caretPosition) {
                    // Swap caret and anchor
                    textArea.selectRange(textArea.caretPosition, textArea.anchor)
                }
                if (pos > 0) {
                    if (pos >= textArea.anchor) {
                        pos = textArea.anchor
                    }
                    val oldPos: Int = textNode.impl_caretPosition
                    textNode.impl_caretPosition = pos
                    val element: PathElement = textNode.impl_caretShape[0]
                    if (element is MoveTo && element.y > e.y - contentView.snappedTopInset()) {
                        hit.charIndex = pos - 1
                    }
                    textNode.impl_caretPosition = oldPos
                }
                positionCaret(hit, select = true, extendSelection = false)
                e.consume()
            }

            selectionHandle2.setOnMouseDragged { e ->
                val textArea: TextAria = skinnable
                val textNode: Text = getTextNode()
                val tp: Point2D = textNode.localToScene(0.0, 0.0)
                val p = Point2D(e.sceneX - tp.x + 10/*??*/ - pressX + selectionHandle2.width / 2,
                    e.sceneY - tp.y - pressY - 6)
                val hit: HitInfo = textNode.impl_hitTestChar(p)
                var pos: Int = hit.charIndex
                if (textArea.anchor > textArea.caretPosition) {
                    // Swap caret and anchor
                    textArea.selectRange(textArea.caretPosition, textArea.anchor)
                }
                if (pos > 0) {
                    if (pos <= textArea.anchor + 1) {
                        pos = Math.min(textArea.anchor + 2, textArea.length)
                    }
                    val oldPos: Int = textNode.impl_caretPosition
                    textNode.impl_caretPosition = pos
                    val element: PathElement = textNode.impl_caretShape[0]
                    if (element is MoveTo && element.y > e.y - contentView.snappedTopInset()) {
                        hit.charIndex = pos - 1
                    }
                    textNode.impl_caretPosition = oldPos
                    positionCaret(hit, select = true, extendSelection = false)
                }
                e.consume()
            }
        }
    }

    override protected fun invalidateMetrics() {
        computedMinWidth = Double.NEGATIVE_INFINITY
        computedMinHeight = Double.NEGATIVE_INFINITY
        computedPrefWidth = Double.NEGATIVE_INFINITY
        computedPrefHeight = Double.NEGATIVE_INFINITY
    }

    private inner class ContentView : Region() {
        init{
            styleClass.add("content")

            val eventHandler:((MouseEvent) -> Unit) = { event ->
                behavior.mousePressed(event)
                event.consume()
            }

            addEventHandler(MouseEvent.MOUSE_PRESSED, eventHandler)
            addEventHandler(MouseEvent.MOUSE_RELEASED, eventHandler)
            addEventHandler(MouseEvent.MOUSE_DRAGGED, eventHandler)
        }

        override fun getContentBias(): Orientation {
            return Orientation.HORIZONTAL
        }

        override protected fun computePrefWidth(height: Double): Double {
            if (computedPrefWidth < 0) {
                var prefWidth: Double = 0.0

                for (node: Node in paragraphNodes.children) {
                    val paragraphNode: Text = node as Text
                    prefWidth = Math.max(prefWidth,
                            Utils.computeTextWidth(paragraphNode.font,
                                    paragraphNode.text, 0.0))
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
                //FIXME: never check floating point for equality
                val wrappingWidth: Double = if (width == -1.0) 0.0 else {
                    Math.max(width - (snappedLeftInset() + snappedRightInset()), 0.0)
                }

                var prefHeight = 0.0

                for (node: Node in paragraphNodes.children) {
                    val paragraphNode: Text = node as Text
                    prefHeight += Utils.computeTextHeight(
                            paragraphNode.font,
                            paragraphNode.text,
                            wrappingWidth,
                            paragraphNode.boundsType
                    )
                }

                prefHeight += snappedTopInset() + snappedBottomInset()

                computedPrefHeight = prefHeight
            }
            return computedPrefHeight
        }

        override protected fun computeMinWidth(height: Double): Double {
            if (computedMinWidth < 0) {
                val hInsets: Double = snappedLeftInset() + snappedRightInset()
                computedMinWidth = Math.min(characterWidth + hInsets, computePrefWidth(height))
            }
            return computedMinWidth
        }

        override protected fun computeMinHeight(width: Double): Double {
            if (computedMinHeight < 0) {
                val vInsets: Double = snappedTopInset() + snappedBottomInset()
                computedMinHeight = Math.min(lineHeight + vInsets, computePrefHeight(width))
            }
            return computedMinHeight
        }

        public override fun getChildren(): ObservableList<Node> {
            return super.getChildren()
        }

        public override fun layoutChildren() {
            val textArea: TextAria = skinnable
            val width: Double = width
            //System.out.println("HERE width: "+width);

            // Lay out paragraphs
            val topPadding: Double = snappedTopInset()
            val leftPadding: Double = snappedLeftInset()

            val wrappingWidth: Double = Math.max(width - (leftPadding + snappedRightInset()), 0.0)

            var y: Double = topPadding
            //System.out.println("HERE  snappedTopInset: "+y);

            val paragraphNodesChildren:List<Node> = paragraphNodes.children

            for (i in paragraphNodesChildren.indices) {
                val node: Node = paragraphNodesChildren[i]
                val paragraphNode: Text = node as Text
                paragraphNode.wrappingWidth = wrappingWidth

                val bounds: Bounds = paragraphNode.boundsInLocal
                paragraphNode.layoutX = leftPadding
                paragraphNode.layoutY = y
                y += bounds.height
            }

            //notify as a property from here
            //System.out.println("HERE  contentView 'y': "+y);
            //System.out.println("HERE  textArea.prefHeight BEFORE: "+textArea.getPrefHeight());
            hajt.set(y)

            promptNode?.let {
                it.layoutX = leftPadding
                it.layoutY = topPadding + it.baselineOffset
                it.wrappingWidth = wrappingWidth
            }

            // Update the selection
            val selection: IndexRange = textArea.selection
            val oldCaretBounds: Bounds? = caretPath.boundsInParent

            selectionHighlightGroup.children.clear()

            val caretPos: Int = textArea.caretPosition
            val anchorPos: Int = textArea.anchor

            if (SHOW_HANDLES) {
                // Install and resize the handles for caret and anchor.
                if (selection.length > 0) {
                    selectionHandle1.resize(selectionHandle1.prefWidth(-1.0),
                            selectionHandle1.prefHeight(-1.0))
                    selectionHandle2.resize(selectionHandle2.prefWidth(-1.0),
                            selectionHandle2.prefHeight(-1.0))
                } else {
                    caretHandle.resize(caretHandle.prefWidth(-1.0),
                            caretHandle.prefHeight(-1.0))
                }

                // Position the handle for the anchor. This could be handle1 or handle2.
                // Do this before positioning the actual caret.
                if (selection.length > 0) {
                    var paragraphIndex: Int = paragraphNodesChildren.size
                    var paragraphOffset: Int = textArea.length + 1
                    var paragraphNode: Text
                    do {
                        paragraphNode = paragraphNodesChildren[--paragraphIndex] as Text
                        paragraphOffset -= paragraphNode.text.length + 1
                    } while (anchorPos < paragraphOffset)

                    updateTextNodeCaretPos(anchorPos - paragraphOffset)
                    caretPath.elements.clear()
                    caretPath.elements.addAll(paragraphNode.impl_caretShape)
                    caretPath.layoutX = paragraphNode.layoutX
                    caretPath.layoutY = paragraphNode.layoutY

                    val b: Bounds = caretPath.boundsInParent
                    if (caretPos < anchorPos) {
                        selectionHandle2.layoutX = b.minX - selectionHandle2.width / 2
                        selectionHandle2.layoutY = b.maxY - 1
                    } else {
                        selectionHandle1.layoutX = b.minX - selectionHandle1.width / 2
                        selectionHandle1.layoutY = b.minY - selectionHandle1.height + 1
                    }
                }
            }

            // Position caret
            var paragraphIndex: Int = paragraphNodesChildren.size
            var paragraphOffset: Int = textArea.length + 1

            var paragraphNode: Text
            do {
                paragraphNode = paragraphNodesChildren[--paragraphIndex] as Text
                paragraphOffset -= paragraphNode.text.length + 1
            } while (caretPos < paragraphOffset)

            updateTextNodeCaretPos(caretPos - paragraphOffset)

            caretPath.elements.clear()
            caretPath.elements.addAll(paragraphNode.impl_caretShape)

            caretPath.layoutX = paragraphNode.layoutX

            // TODO: Remove this temporary workaround for RT-27533
            paragraphNode.layoutX = 2 * paragraphNode.layoutX - paragraphNode.boundsInParent.minX

            caretPath.layoutY = paragraphNode.layoutY
            if (oldCaretBounds == null || oldCaretBounds != caretPath.boundsInParent) {
                scrollCaretToVisible()
            }

            // Update selection fg and bg
            var start: Int = selection.start
            var end: Int = selection.end
            for (i in paragraphNodesChildren.indices) {
                val paragraphNode: Node = paragraphNodesChildren[i]
                val textNode: Text = paragraphNode as Text
                val paragraphLength: Int = textNode.text.length + 1
                if (end > start && start < paragraphLength) {
                    textNode.impl_selectionStart = start
                    textNode.impl_selectionEnd = Math.min(end, paragraphLength)

                    val selectionHighlightPath: Path = Path()
                    selectionHighlightPath.isManaged = false
                    selectionHighlightPath.stroke = null
                    val selectionShape: Array<PathElement>? = textNode.impl_selectionShape
                    if (selectionShape != null) {
                        selectionHighlightPath.elements.addAll(selectionShape)
                    }
                    selectionHighlightGroup.children.add(selectionHighlightPath)
                    selectionHighlightGroup.isVisible = true
                    selectionHighlightPath.layoutX = textNode.layoutX
                    selectionHighlightPath.layoutY = textNode.layoutY
                    updateHighlightFill()
                } else {
                    textNode.impl_selectionStart = -1
                    textNode.impl_selectionEnd = -1
                    selectionHighlightGroup.isVisible = false
                }
                start = Math.max(0, start - paragraphLength)
                end   = Math.max(0, end   - paragraphLength)
            }

            if (SHOW_HANDLES) {
                // Position handle for the caret. This could be handle1 or handle2 when
                // a selection is active.
                val b: Bounds = caretPath.boundsInParent
                if (selection.length > 0) {
                    if (caretPos < anchorPos) {
                        selectionHandle1.layoutX = b.minX - selectionHandle1.width / 2
                        selectionHandle1.layoutY = b.minY - selectionHandle1.height + 1
                    } else {
                        selectionHandle2.layoutX = b.minX - selectionHandle2.width / 2
                        selectionHandle2.layoutY = b.maxY - 1
                    }
                } else {
                    caretHandle.layoutX = b.minX - caretHandle.width / 2 + 1
                    caretHandle.layoutY = b.maxY
                }
            }

            if (contentView.prefWidth == 0.0 || contentView.prefHeight == 0.0) {
                updatePrefViewportWidth()
                updatePrefViewportHeight()
                if (parent != null && contentView.prefWidth > 0
                        || contentView.prefHeight > 0) {
                    // Force layout of viewRect in ScrollPaneSkin
                    parent.requestLayout()
                }
            }
        }

        var hajt: DoubleProperty = object:DoublePropertyBase() {
            override fun getBean(): Any? {
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
            promptNode = Text().apply {
                contentView.children.add(0, this)
                isManaged = false
                styleClass.add("text")
                visibleProperty().bind(usePromptText)
                fontProperty().bind(skinnable.fontProperty())
                textProperty().bind(skinnable.promptTextProperty())
                fillProperty().bind(promptTextFill)
            }
        }
    }

    private fun addParagraphNode(i: Int, string: String) {
        val textArea: TextAria = skinnable
        val paragraphNode = Text(string)
        paragraphNode.textOrigin = VPos.TOP
        paragraphNode.isManaged = false
        paragraphNode.styleClass.add("text")
        paragraphNode.boundsTypeProperty().addListener { _, _, _ ->
            invalidateMetrics()
            updateFontMetrics()
        }
        paragraphNodes.children.add(i, paragraphNode)

        paragraphNode.fontProperty().bind(textArea.fontProperty())
        paragraphNode.fillProperty().bind(textFill)
        paragraphNode.impl_selectionFillProperty().bind(highlightTextFill)
    }

    override fun dispose() {
        // TODO Unregister listeners on text editor, paragraph list
        throw UnsupportedOperationException()
    }

    override fun computeBaselineOffset(topInset: Double, rightInset: Double, bottomInset: Double, leftInset: Double): Double {
        val firstParagraph: Text = paragraphNodes.children[0] as Text
        return Utils.getAscent(skinnable.font, firstParagraph.boundsType) +
                contentView.snappedTopInset() + textArea.snappedTopInset()
    }

    override fun getCharacter(index:Int):Char {
        var offset: Int = index

        //var paragraph: String = paragraphNodes.children.first { offset < (it as Text).text.length +1  }
        lateinit var paragraph: String
        for (i in 0 until paragraphNodes.children.size) {
            paragraph = (paragraphNodes.children[i] as Text).text
            val count: Int = paragraph.length + 1

            if (offset < count) {
                break
            }

            offset -= count
        }

        return if(offset == paragraph.length) '\n' else paragraph[offset]
    }

    override fun getInsertionPoint(x:Double, y:Double):Int {
        val textArea: TextAria = skinnable

        val n: Int = paragraphNodes.children.size
        var index: Int = -1

        if (n > 0) {
            if (y < contentView.snappedTopInset()) {
                // Select the character at x in the first row
                val paragraphNode: Text = paragraphNodes.children[0] as Text
                index = getNextInsertionPoint(paragraphNode, x, -1, VerticalDirection.DOWN)
            } else if (y > contentView.snappedTopInset() + contentView.height) {
                // Select the character at x in the last row
                val lastParagraphIndex: Int = n - 1
                val lastParagraphView: Text = paragraphNodes.children[lastParagraphIndex] as Text

                index = getNextInsertionPoint(lastParagraphView, x, -1, VerticalDirection.UP)
                        + (textArea.length - lastParagraphView.text.length)
            } else {
                // Select the character at x in the row at y
                var paragraphOffset: Int = 0
                for (i in 0 until n) {
                    val paragraphNode: Text = paragraphNodes.children[i] as Text

                    val bounds: Bounds = paragraphNode.boundsInLocal
                    val paragraphViewY: Double = paragraphNode.layoutY + bounds.minY
                    if (y >= paragraphViewY
                            && y < paragraphViewY + paragraphNode.boundsInLocal.height) {
                        index = getInsertionPoint(paragraphNode,
                                x - paragraphNode.layoutX,
                                y - paragraphNode.layoutY) + paragraphOffset
                        break
                    }

                    paragraphOffset += paragraphNode.text.length + 1
                }
            }
        }

        return index
    }

    fun positionCaret(hit: HitInfo, select: Boolean, extendSelection: Boolean) {
        var pos: Int = Utils.getHitInsertionIndex(hit, skinnable.text)
        val isNewLine: Boolean =
                (pos > 0 &&
                        pos <= skinnable.length &&
                        skinnable.text.codePointAt(pos-1) == 0x0a)

        // special handling for a line
        if (!hit.isLeading && isNewLine) {
            hit.isLeading = true
            pos -= 1
        }

        if (select) {
            if (extendSelection) {
                skinnable.extendSelection(pos)
            } else {
                skinnable.selectPositionCaret(pos)
            }
        } else {
            skinnable.positionCaret(pos)
        }

        isForwardBias = hit.isLeading
    }

    private fun getInsertionPoint(paragraphNode: Text, x: Double, y: Double):Int {
        val hitInfo: HitInfo = paragraphNode.impl_hitTestChar(Point2D(x, y))
        return Utils.getHitInsertionIndex(hitInfo, paragraphNode.text)
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
        val textArea: TextAria = skinnable

        var paragraphIndex: Int = paragraphNodes.children.size
        var paragraphOffset: Int = textArea.length + 1

        var paragraphNode: Text
        do {
            paragraphNode = paragraphNodes.children[--paragraphIndex] as Text
            paragraphOffset -= paragraphNode.text.length + 1
        } while (index < paragraphOffset)

        var characterIndex: Int = index - paragraphOffset
        var terminator: Boolean = false

        if (characterIndex == paragraphNode.text.length) {
            characterIndex--
            terminator = true
        }

        characterBoundingPath.elements.clear()
        characterBoundingPath.elements.addAll(paragraphNode.impl_getRangeShape(characterIndex, characterIndex + 1))
        characterBoundingPath.layoutX = paragraphNode.layoutX
        characterBoundingPath.layoutY = paragraphNode.layoutY

        val bounds: Bounds = characterBoundingPath.boundsInLocal

        var x: Double = bounds.minX + paragraphNode.layoutX - textArea.scrollLeft
        val y: Double = bounds.minY + paragraphNode.layoutY - textArea.scrollTop

        // Sometimes the bounds is empty, in which case we must ignore the width/height
        var width: Double = if(bounds.isEmpty) 0.0 else bounds.width
        val height: Double = if(bounds.isEmpty) 0.0 else bounds.height

        if (terminator) {
            x += width
            width = 0.0
        }

        return Rectangle2D(x, y, width, height)
    }

    private fun scrollCaretToVisible() {
        val textArea: TextAria = skinnable
        val bounds: Bounds = caretPath.layoutBounds
        var x: Double = bounds.minX - textArea.scrollLeft
        var y: Double = bounds.minY - textArea.scrollTop
        var w: Double = bounds.width
        var h: Double = bounds.height

        if (SHOW_HANDLES) {
            if (caretHandle.isVisible) {
                h += caretHandle.height
            } else if (selectionHandle1.isVisible && selectionHandle2.isVisible) {
                x -= selectionHandle1.width / 2
                y -= selectionHandle1.height
                w += selectionHandle1.width / 2 + selectionHandle2.width / 2
                h += selectionHandle1.height + selectionHandle2.height
            }
        }
    }

    private fun updatePrefViewportWidth() {
        val columnCount: Int = skinnable.prefColumnCount
        contentView.prefWidth = columnCount * characterWidth + contentView.snappedLeftInset() + contentView.snappedRightInset()
        contentView.minWidth = characterWidth + contentView.snappedLeftInset() + contentView.snappedRightInset()
    }

    private fun updatePrefViewportHeight() {
        val rowCount: Int = skinnable.prefRowCount
        contentView.prefHeight = rowCount * lineHeight + contentView.snappedTopInset() + contentView.snappedBottomInset()
        contentView.minHeight = lineHeight + contentView.snappedTopInset() + contentView.snappedBottomInset()
    }

    private fun updateFontMetrics() {
        val firstParagraph: Text = paragraphNodes.children[0] as Text
        lineHeight = Utils.getLineHeight(skinnable.font,firstParagraph.boundsType)
        characterWidth = fontMetrics.get().computeStringWidth("W").toDouble()
    }

    override protected fun updateHighlightFill() {
        for (node: Node in selectionHighlightGroup.children) {
            var selectionHighlightPath: Path = node as Path
            selectionHighlightPath.fill = highlightFill.get()
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

    private fun getTextNode(): Text = paragraphNodes.children[0] as Text

    fun getIndex(x: Double, y: Double): HitInfo {
        // adjust the event to be in the same coordinate space as the
        // text content of the textInputControl
        val textNode: Text = getTextNode()
        val p: Point2D = Point2D(x - textNode.layoutX, y - contentView.snappedTopInset())
        val hit: HitInfo = textNode.impl_hitTestChar(p)
        val pos: Int = hit.charIndex
        if (pos > 0) {
            val oldPos: Int = textNode.impl_caretPosition
            textNode.impl_caretPosition = pos
            val element: PathElement = textNode.impl_caretShape[0]
            if (element is MoveTo && (element as MoveTo).y > y - contentView.snappedTopInset()) {
                hit.charIndex = pos - 1
            }
            textNode.impl_caretPosition = oldPos
        }
        return hit
    }

    override fun nextCharacterVisually(muvRajt: Boolean) {
        // mirror Text node if we're in Right-To-Left
        val moveRight = if (isRTL) !muvRajt else muvRajt

        val textNode: Text = getTextNode()
        var caretBounds: Bounds = caretPath.layoutBounds
        if (caretPath.elements.size == 4) {
            // The caret is split
            // TODO: Find a better way to get the primary caret position
            // instead of depending on the internal implementation.
            // See RT-25465.
            caretBounds = Path(caretPath.elements[0], caretPath.elements[1]).layoutBounds
        }
        val hitX: Double = if(moveRight) caretBounds.maxX else caretBounds.minX
        val hitY: Double = (caretBounds.minY + caretBounds.maxY) / 2
        val hit: HitInfo = textNode.impl_hitTestChar(Point2D(hitX, hitY))
        val charShape: Path = Path(*textNode.impl_getRangeShape(hit.charIndex, hit.charIndex + 1))
        if ((moveRight && charShape.layoutBounds.maxX > caretBounds.maxX) ||
                (!moveRight && charShape.layoutBounds.minX < caretBounds.minX)) {
            hit.isLeading = !hit.isLeading
            positionCaret(hit, select = false, extendSelection = false)
        } else {
            // We're at beginning or end of line. Try moving up / down.
            val dot: Int = textArea.caretPosition
            targetCaretX = if(moveRight) 0.0 else Double.MAX_VALUE
            // TODO: Use Bidi sniffing instead of assuming right means forward here?
            downLines(if(moveRight) 1 else -1, false, false)
            targetCaretX = -1.0
            if (dot == textArea.caretPosition) {
                if (moveRight) {
                    textArea.forward()
                } else {
                    textArea.backward()
                }
            }
        }
    }

    protected fun downLines(nLines: Int, select: Boolean, extendSelection: Boolean) {
        val textNode: Text = getTextNode()
        val caretBounds: Bounds = caretPath.layoutBounds

        // The middle y coordinate of the the line we want to go to.
        var targetLineMidY: Double = (caretBounds.minY + caretBounds.maxY) / 2 + nLines * lineHeight
        if (targetLineMidY < 0) {
            targetLineMidY = 0.0
        }

        // The target x for the caret. This may have been set during a
        // previous call.
        val x: Double = if(targetCaretX >= 0)  targetCaretX else (caretBounds.maxX)

        // Find a text position for the target x,y.
        val hit: HitInfo = textNode.impl_hitTestChar(Point2D(x, targetLineMidY))
        val pos: Int = hit.charIndex

        // Save the old pos temporarily while testing the one.
        val oldPos: Int = textNode.impl_caretPosition
        val oldBias: Boolean = textNode.isImpl_caretBias
        textNode.isImpl_caretBias = hit.isLeading
        textNode.impl_caretPosition = pos
        tmpCaretPath.elements.clear()
        tmpCaretPath.elements.addAll(textNode.impl_caretShape)
        tmpCaretPath.layoutX = textNode.layoutX
        tmpCaretPath.layoutY = textNode.layoutY
        val tmpCaretBounds: Bounds = tmpCaretPath.layoutBounds
        // The y for the middle of the row we found.
        val foundLineMidY: Double = (tmpCaretBounds.minY + tmpCaretBounds.maxY) / 2
        textNode.isImpl_caretBias = oldBias
        textNode.impl_caretPosition = oldPos

        if (pos > 0) {
            if (nLines > 0 && foundLineMidY > targetLineMidY) {
                // We went too far and ended up after a newline.
                hit.charIndex = pos - 1
            }

            if (pos >= textArea.length && getCharacter(pos - 1) == '\n') {
                // Special case for newline at end of text.
                hit.isLeading = true
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
        targetCaretX = 0.0
        downLines(0, select, extendSelection)
        targetCaretX = -1.0
    }

    fun lineEnd(select: Boolean, extendSelection: Boolean) {
        targetCaretX = Double.MAX_VALUE
        downLines(0, select, extendSelection)
        targetCaretX = -1.0
    }


    fun paragraphStart(previousIfAtStart: Boolean, select: Boolean) {
        val textArea: TextAria = skinnable
        val text: String = textArea.textProperty().valueSafe
        var pos: Int = textArea.caretPosition

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
        val textArea: TextAria = skinnable
        val text: String = textArea.textProperty().valueSafe
        var pos: Int = textArea.caretPosition
        val len: Int = text.length
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
        val textNode: Text = getTextNode()
        if (isForwardBias) {
            textNode.impl_caretPosition = pos
        } else {
            textNode.impl_caretPosition = pos - 1
        }
        textNode.impl_caretBiasProperty().set(isForwardBias)
    }

    override protected fun getUnderlineShape(start: Int, end: Int):Array<PathElement>? {
        var pStart: Int = 0
        for (node: Node in paragraphNodes.children) {
            val p: Text = node as Text
            val pEnd: Int = pStart + p.textProperty().valueSafe.length
            if (pEnd >= start) {
                return p.impl_getUnderlineShape(start - pStart, end - pStart)
            }
            pStart = pEnd + 1
        }
        return null
    }

    override protected fun getRangeShape(start: Int, end: Int):Array<PathElement>? {
        var pStart: Int = 0
        for (node: Node in paragraphNodes.children) {
            val p: Text = node as Text
            val pEnd: Int = pStart + p.textProperty().valueSafe.length
            if (pEnd >= start) {
                return p.impl_getRangeShape(start - pStart, end - pStart)
            }
            pStart = pEnd + 1
        }
        return null
    }

    override protected fun addHighlight(nodes:List<Node>, start: Int) {
        var pStart: Int = 0
        var paragraphNode: Text? = null
        for (node: Node in paragraphNodes.children) {
            val p: Text = node as Text
            val pEnd: Int = pStart + p.textProperty().valueSafe.length
            if (pEnd >= start) {
                paragraphNode = p
                break
            }
            pStart = pEnd + 1
        }

        if (paragraphNode != null) {
            for (node: Node in nodes) {
                node.layoutX = paragraphNode.layoutX
                node.layoutY = paragraphNode.layoutY
            }
        }
        contentView.children.addAll(nodes)
    }

    override protected fun removeHighlight(nodes:List<Node> ) {
        contentView.children.removeAll(nodes)
    }

    /**
     * Use this implementation instead of the one provided on TextInputControl
     * Simply calls into TextInputControl.deletePrevious/NextChar and responds appropriately
     * based on the return value.
     */
    fun deleteChar(previous: Boolean) {
//        final double textMaxXOld = textNode.getBoundsInParent().getMaxX();
//        final double caretMaxXOld = caretPath.getLayoutBounds().getMaxX() + textTranslateX.get();
        if(previous) {
            !skinnable.deletePreviousChar()
        } else{
            !skinnable.deleteNextChar()
        }
    }

    override fun getMenuPosition(): Point2D {
        contentView.layoutChildren()
        var p: Point2D = super.getMenuPosition()
        if (p != null) {
            p = Point2D(Math.max(0.0, p.x - contentView.snappedLeftInset() - skinnable.scrollLeft),
                    Math.max(0.0, p.y - contentView.snappedTopInset() - skinnable.scrollTop))
        }
        return p
    }

    override protected fun queryAccessibleAttribute(attribute: AccessibleAttribute, vararg parameters:Any): Any {
        return when (attribute) {
            AccessibleAttribute.LINE_FOR_OFFSET,
            AccessibleAttribute.LINE_START,
            AccessibleAttribute.LINE_END,
            AccessibleAttribute.BOUNDS_FOR_RANGE,
            AccessibleAttribute.OFFSET_AT_POINT -> getTextNode().queryAccessibleAttribute(attribute, parameters)
            else -> super.queryAccessibleAttribute(attribute, parameters)
        }
    }
}
