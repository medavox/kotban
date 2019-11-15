package com.github.medavox.kotban

import javafx.application.Application
import javafx.geometry.Orientation
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.TitledPane
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.stage.Stage
import tornadofx.*
import tornadofx.Stylesheet.Companion.datagrid
import java.io.File
import javafx.scene.layout.VBox



//some more tutorial & learning resources;
//https://docs.oracle.com/javase/8/javafx/get-started-tutorial/form.htm#CFHEAHGB
//https://docs.oracle.com/javase/8/javafx/get-started-tutorial/hello_world.htm
/**Provides a Desktop GUI for the library.
 * Implemented with JavaFX, available as part of Java 8's language API.
 * Note that after Java 8, JavaFX was made an external library.
 * @see [https://docs.oracle.com/javase/8/javafx/api](JavaFX javadoc)*/
class Gui : Application() {
    //input = multi-line text input field
    //language choice = dropdown list
    //result = multi-line text output field
    //errors = multi-line text output field

    /*Instead of making entries editable (andd effedctively having to write our own text editor),
    * make each entry, upon being clicked, open itself in the user's choice of editor.
    * That allows us to focus on making thw Markdown pretty, too*/
    /*override val root: Parent = flowpane {
        orientation = Orientation.VERTICAL
        //label(board.name)
        for((name, entries) in board.panes) {
            *//*panes*//*hbox{
                label(name)//todo: do the label some other way
                anchorpane {
                    anchorpaneConstraints{ AnchorPaneConstraint(0.0, 0.0, 0.0, 0.0)}
                    scrollpane(fitToWidth = true) {
                        //useMaxHeight = true
                        //maxHeight = 100.0
                        vbarPolicy = ScrollPane.ScrollBarPolicy.ALWAYS
                        vbox {
                            for (entry in entries) {
                                titledpane {
                                    text = entry.title
                                    content = textarea { text = entry.contents }
                                }
                            }
                        }
                    }
                }
            }
        }
    }*/

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
            //root.children.add()
            val anchorPane = AnchorPane()
            val scrollPane = ScrollPane()
            val vb2 = VBox()

            AnchorPane.setTopAnchor(scrollPane, 0.0)
            AnchorPane.setBottomAnchor(scrollPane, 0.0)
            AnchorPane.setLeftAnchor(scrollPane, 0.0)
            AnchorPane.setRightAnchor(scrollPane, 0.0)
            anchorPane.children.add(scrollPane)
            //Add content ScrollPane
            scrollPane.content = vb2
            for (entry in entries) {
                vb2.children.add(TitledPane(entry.title, TextArea(entry.contents)))
            }
            root.children.add(anchorPane)
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
