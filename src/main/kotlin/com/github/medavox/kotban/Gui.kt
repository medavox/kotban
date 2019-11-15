package com.github.medavox.kotban

import javafx.application.Application
import javafx.geometry.Orientation
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.stage.Stage
import java.io.File
import javafx.scene.layout.VBox
import tornadofx.fitToHeight

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
/**Provides a Desktop GUI for the library.
 * Implemented with JavaFX, available as part of Java 8's language API.
 * Note that after Java 8, JavaFX was made an external library.
 * @see [https://docs.oracle.com/javase/8/javafx/api](JavaFX javadoc)*/
class Gui : Application() {
    /*Instead of making entries editable (and effectively having to write our own text editor),
    * make each entry, upon being clicked, open itself in the user's choice of editor.
    * That allows us to focus on prettifying the Markdown */
    override fun start(primaryStage:Stage) {

        val columns = HBox()
        val root = AnchorPane().also { rwt ->
            rwt.children.add(ScrollPane().also { colScrol ->
                AnchorPane.setTopAnchor(colScrol, 0.0)
                AnchorPane.setBottomAnchor(colScrol, 0.0)
                AnchorPane.setLeftAnchor(colScrol, 0.0)
                AnchorPane.setRightAnchor(colScrol, 0.0)
                colScrol.isFitToHeight = true
                //colScrol.isFitToWidth = true
                colScrol.content = columns
            })
        }

        //root.setPrefSize(600.0, 600.0)

        System.setErr(System.err)
        val board = Loader.load(File("./testboard"))
        for((name, entries) in board.panes) {
            columns.children.add(VBox().also { col ->
                col.children.add(Label(name))
                col.children.add(AnchorPane().also { anch ->
                    anch.children.add(ScrollPane().also { scrol ->
                        scrol.isFitToWidth = true
                        AnchorPane.setTopAnchor(scrol, 0.0)
                        AnchorPane.setBottomAnchor(scrol, 0.0)
                        AnchorPane.setLeftAnchor(scrol, 0.0)
                        AnchorPane.setRightAnchor(scrol, 0.0)
                        scrol.content = VBox().also { notes ->
                            for (entry in entries) {
                                notes.children.add(TitledPane(entry.title,
                                    TextArea(entry.contents).apply{isEditable=false}))
                            }
                        }
                    })
                })
            })
        }

        primaryStage.title = board.name+" - Kotban"
        primaryStage.scene = Scene(root, 600.0, 600.0)
        primaryStage.show()
    }
}
