package com.github.medavox.kotban

import javafx.beans.binding.StringBinding
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.StackPaneBuilder

class ScrollFreeTextArea : StackPane() {

    private val label:Label
    private val textArea:TextArea
    private val enterChar:Char = 10.toChar()
    private lateinit var cauntint:Region
    private val contentHeight:SimpleDoubleProperty = SimpleDoubleProperty()

    private val NEW_LINE_HEIGHT = 18.0
    private val TOP_PADDING = 3.0
    private val BOTTOM_PADDING = 6.0

    init {
        alignment = Pos.TOP_LEFT
        this.textArea = object:TextArea() {
            override fun layoutChildren() {
                super.layoutChildren()
                cauntint = lookup(".content") as Region
                contentHeight.bind(cauntint.heightProperty())
                cauntint.heightProperty().addListener { _, _, _ ->
                    //System.out.println("Content View Height :"+paramT2.doubleValue());
                }
            }
        }
        this.textArea.setWrapText(true)

        this.label = Label()
        this.label.isWrapText = true
        this.label.prefWidthProperty().bind(this.textArea.widthProperty())
        label.textProperty().bind(object:StringBinding() {
            init {
                bind(textArea.textProperty())
            }
            override fun computeValue():String {
                if (textArea.getText() != null && textArea.text.length > 0) {
                    if ((textArea.text[textArea.text.length - 1]) != enterChar) {
                        return textArea.text + enterChar
                    }
                }
                return textArea.getText()
            }
        })

        val lblContainer:StackPane = StackPaneBuilder.create()
                .alignment(Pos.TOP_LEFT)
                .padding(Insets(4.0,7.0,7.0,7.0))
                .children(label)
                .build()
        // Binding the container width/height to the TextArea width.
        lblContainer.maxWidthProperty().bind(textArea.widthProperty())

        textArea.textProperty().addListener { _, _, _ ->
            layoutForNewLine(textArea.getText())
        }

        label.heightProperty().addListener { _, _, _ ->
            layoutForNewLine(textArea.getText())
        }

        children.addAll(lblContainer, textArea)
    }

    private fun layoutForNewLine(text:String) {
        if (text.length > 0 && (text[text.length - 1]) == enterChar) {
            textArea.prefHeight = label.height + NEW_LINE_HEIGHT + TOP_PADDING + BOTTOM_PADDING
            textArea.minHeight = textArea.prefHeight
        }
        else {
            textArea.prefHeight = label.height + TOP_PADDING + BOTTOM_PADDING
            textArea.minHeight = textArea.prefHeight
        }
    }

    fun getTextArea():TextArea {
        return textArea
    }
}