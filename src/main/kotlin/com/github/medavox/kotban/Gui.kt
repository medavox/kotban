package com.github.medavox.kotban

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.TitledPane
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.stage.Stage
import java.io.File
import javafx.scene.layout.VBox

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
    /*Instead of making entries editable (andd effedctively having to write our own text editor),
    * make each entry, upon being clicked, open itself in the user's choice of editor.
    * That allows us to focus on making thw Markdown pretty, too*/
    override fun start(primaryStage:Stage) {
        /*val root = GridPane()
        root.setAlignment(Pos.CENTER)
        root.setHgap(10.0); root.setVgap(10.0)
        root.setPadding(Insets(10.0, 10.0, 10.0, 10.0))*/

        //val out = PrintStreamCapturer(output, System.out)
        val root = HBox()
        root.setPrefSize(600.0, 600.0)

        System.setErr(System.err)
        val board = Loader.load(File("./testboard"))
        for((name, entries) in board.panes) {
            //label(name)//todo: do the label some other way
            root.children.add(VBox().also{ col ->
                col.children.add(Label(name))
                col.children.add(AnchorPane().also { anch ->
                    anch.children.add(ScrollPane().also { scrol ->
                        //scrol.isFitToWidth = true
                        AnchorPane.setTopAnchor(scrol, 0.0)
                        AnchorPane.setBottomAnchor(scrol, 0.0)
                        AnchorPane.setLeftAnchor(scrol, 0.0)
                        AnchorPane.setRightAnchor(scrol, 0.0)
                        scrol.content = VBox().also { vbox ->
                            for (entry in entries) {
                                vbox.children.add(TitledPane(entry.title,
                                    TextArea(entry.contents).apply{isEditable=false}))
                            }
                        }
                    })
                })
            })
        }


/*        root.add(input, 0, 0, 2, 1)
        root.add(menuButton, 0, 1)
        root.add(btn, 1, 1)
        root.add(output, 0, 3, 2, 1)
        root.add(errors, 0, 4, 2, 1)*/

        primaryStage.title = board.name+" - Kotban"
        primaryStage.scene = Scene(root, 400.0, 400.0)
        primaryStage.show()
    }
}
