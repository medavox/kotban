package com.github.medavox.kotban

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.stage.Stage
import java.io.File
import javafx.scene.layout.VBox
import javafx.scene.control.ButtonBar

//terminology:
//board: the whole thing. A folder with subfolders that each contain 0 or more text files
//column: contains notes/tasks. represented on-disk by a subfolder of the board
//note: a task. can be moved between columns. represented on-disk by a text file

//todo:
// need a filer watcher, to auto-refresh external changes
//   or just a refresh button
// need to be able to drag notes between columns,
// create and delete notes,
// create and delete columns
// edit column names (which are actually folder names)
// edit note names (which are actually file names)
// click-to-maximise a single note
// tags - supported through a custom line in the note's text
// filter by tag
/**Provides a Desktop GUI for the library.
 * Implemented with JavaFX, available as part of Java 8's language API.
 * Note that after Java 8, JavaFX was made an external library.
 * @see [https://docs.oracle.com/javase/8/javafx/api](JavaFX javadoc)*/
class Gui : Application() {
    /*Instead of making entries editable (and effectively having to write our own text editor),
    * make each entry, upon being clicked, open itself in the user's choice of editor.
    * That allows us to focus on prettifying the Markdown */
    override fun start(primaryStage:Stage) {
        val root = VBox()

        val colScrol = ScrollPane().apply {
            AnchorPane.setTopAnchor(this, 0.0)
            AnchorPane.setBottomAnchor(this, 0.0)
            AnchorPane.setLeftAnchor(this, 0.0)
            AnchorPane.setRightAnchor(this, 0.0)
            isFitToHeight = true
            val board = load(File("./testboard"))
            primaryStage.title = board.name+" - Kotban"
            content = layitout(board, primaryStage)
        }
        val content = AnchorPane()
        content.children.add(colScrol)

        root.children.add(ButtonBar().also{ bar ->
            //bar.nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
            //bar.ali
            bar.buttons.add(
                Button("Rifrash").apply{
                    setOnMouseClicked {
                        val board = load(File("./testboard"))
                        primaryStage.title = board.name+" - Kotban"
                        colScrol.content = layitout(board, primaryStage)
                    }
                }
            )
        })
        root.children.add(content)

        //root.setPrefSize(600.0, 600.0)

        layitout(load(File("./testboard")), primaryStage)

        primaryStage.scene = Scene(root, 600.0, 600.0)
        primaryStage.show()
    }

    private fun layitout(board:Board, stg:Stage):HBox {
        val columns = HBox()
        for((name, entries) in board.columns) {
            columns.children.add(VBox().also { col ->
                col.children.add(Label(name))
                col.children.add(AnchorPane().also { anch ->
                    anch.children.add(ScrollPane().also { scrol ->
                        scrol.prefViewportWidth = 300.0
                        scrol.isFitToWidth = true
                        AnchorPane.setTopAnchor(scrol, 0.0)
                        AnchorPane.setBottomAnchor(scrol, 0.0)
                        AnchorPane.setLeftAnchor(scrol, 0.0)
                        AnchorPane.setRightAnchor(scrol, 0.0)
                        scrol.content = VBox().also { notes ->
                            for (entry in entries) {
                                notes.children.add(TitledPane(entry.title,
                                    TextArea(entry.contents).apply{
                                        isEditable=false
                                        setOnMouseClicked {
                                            //println("$entry clicked")
                                            openInDefaultTextEditor(entry.file)
                                        }
                                    }).also{DragResizerXY.makeResizable(it)})
                            }
                        }
                    })
                })
            })
        }
        return columns
    }
}
