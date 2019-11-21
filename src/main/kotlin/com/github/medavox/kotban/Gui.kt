package com.github.medavox.kotban

import javafx.application.Application
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.stage.Stage
import java.io.File
import javafx.scene.layout.VBox
import javafx.scene.control.ButtonBar
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode

//terminology:
//board: the whole thing. A folder with subfolders that each contain 0 or more text files
//column: contains notes/tasks. represented on-disk by a subfolder of the board
//note: a task. can be moved between columns. represented on-disk by a text file

//todo:
// create and delete notes
// create and delete columns
// edit column names (which are actually folder names)
// edit note names (which are actually file names)
// click-to-maximise a single note
// tags - supported through a custom line in the note's text
// filter by tag
// show empty columns
// make column expand vertically,
//   so you can drag onto empty space below a column's notes, as expected
// show a visual hint about where the note will go
// allow dragging to a specific placement in the column?
//   goes against our "ordering is alphabetical only" approach
/**Provides a Desktop GUI for the library.
 * Implemented with JavaFX, available as part of Java 8's language API.
 * Note that after Java 8, JavaFX was made an external library.
 * @see [https://docs.oracle.com/javase/8/javafx/api](JavaFX javadoc)*/
class Gui : Application() {
    private val dir = "./testboard"
    /*Instead of making entries editable (and effectively having to write our own text editor),
    * make each entry, upon being clicked, open itself in the user's choice of editor.
    * That allows us to focus on prettifying the Markdown */
    override fun start(primaryStage:Stage) {
        val dirFile = File(dir)
        val root = VBox()

        val colScrol = ScrollPane().apply {
            AnchorPane.setTopAnchor(this, 0.0)
            AnchorPane.setBottomAnchor(this, 0.0)
            AnchorPane.setLeftAnchor(this, 0.0)
            AnchorPane.setRightAnchor(this, 0.0)
            isFitToHeight = true
            val board = load(dirFile)
            primaryStage.title = board.name+" - Kotban"
            content = layoutColumnContents(board)
        }
        val content = AnchorPane()
        content.children.add(colScrol)

        root.children.add(ButtonBar().also{ bar ->
            //bar.nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
            //bar.ali
            bar.buttons.add(
                Button("Refresh").apply{
                    setOnMouseClicked {
                        val board = load(dirFile)
                        primaryStage.title = board.name+" - Kotban"
                        colScrol.content = layoutColumnContents(board)
                    }
                }
            )
        })
        root.children.add(content)
        content.prefHeightProperty().bind(root.heightProperty())
        //content.minHeightProperty().bind(root.heightProperty())

        primaryStage.scene = Scene(root, 600.0, 600.0)
        primaryStage.show()
    }


    private fun uiOf(note:Note): Node = TitledPane(note.title,
        TextArea(note.contents).apply{
            isEditable=false
            DragResizerXY(this).makeResizable()
        }).apply {
        contextMenu = ContextMenu(
            MenuItem("Open in editor").apply{setOnAction{
                openInDefaultTextEditor(note.file)
            }},
            MenuItem("Rename").apply {setOnAction {
                this.text
            }}
        )
        setOnDragDone { println("$this: drag done:$it") }
        setOnDragEntered { println("$this: drag entered:$it") }
        setOnDragExited { println("$this: drag exited:$it") }
        //setOnDragDropped { println("drag dropped:$it") }
        onDragDetected = EventHandler {event ->
            //println("drag detected:$it")
            val dragBoard = this.startDragAndDrop(TransferMode.MOVE)
            println("dragBoard:$dragBoard")
            //put a file on the dragboard
            val content = ClipboardContent()
            content.putFiles(listOf(note.file))
            dragBoard.setContent(content)

            event.consume()
        }
    }

    private fun uiOf(column:Column):Node = VBox().also { col ->
        col.children.add(Label(column.name+" - "+column.notes.size))
        col.children.add(AnchorPane().also { anch ->
            anch.children.add(ScrollPane().also { scrol ->
                scrol.prefViewportWidth = 300.0
                scrol.isFitToWidth = true
                AnchorPane.setTopAnchor(scrol, 0.0)
                AnchorPane.setBottomAnchor(scrol, 0.0)
                AnchorPane.setLeftAnchor(scrol, 0.0)
                AnchorPane.setRightAnchor(scrol, 0.0)
                scrol.content = VBox().apply {
                    onDragOver = EventHandler { event ->
                        /* accept only if it's not dragged from the same node,
                             * and if it has a File as data */
                        if (event.gestureSource !== this && event.dragboard.hasFiles()) {
                            //println("$this: drag over: $event")
                            event.acceptTransferModes(*TransferMode.COPY_OR_MOVE)
                        }
                        event.consume()
                    }

                    onDragDropped = EventHandler { event ->
                        /* data dropped */
                        println("$this: onDragDropped: $event")
                        /* if there is a string data on dragboard, read it and use it */
                        val db = event.dragboard
                        var success = false
                        if (db.hasFiles()) {
                            println("files: ${db.files}")
                            db.files[0].renameTo(File(column.folder, db.files[0].name))
                            success = true
                            //layitout(load(dirFile))//todo: figure out how to do UI refresh here
                        }
                        /* let the source know whether the string was successfully
                             * transferred and used */
                        event.isDropCompleted = success

                        event.consume()
                    }
                    for (note in column.notes) {
                        children.add(uiOf(note))
                    }
                }
            })
        })
    }
    /*private fun column(title:String, vararg ):VBox {

    }
*/
    private fun layoutColumnContents(board:Board):HBox {
        val columns = HBox()
        for(column in board.columns) {
            columns.children.add(uiOf(column))
        }
        return columns
    }
}
