package com.github.medavox.kotban
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.input.MouseEvent
import javafx.scene.control.Label
import javafx.scene.control.ScrollBar
import javafx.scene.control.ScrollPane
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.Duration

/**from https://stackoverflow.com/a/41767014*/
class Main : Application() {

    private var scrollVelocity:Double = 0.0

    var dropped:Boolean = false

    //Higher speed value = slower scroll.
    val speed = 200

    @Throws(Exception::class)
    override fun start(primaryStage:Stage)  {
        val root = BorderPane()
        val sp = ScrollPane()
        sp.setPrefSize(300.0, 300.0)

        val outer = VBox(sp)

        val innerBox = VBox()
        innerBox.setPrefSize(200.0,1000.0)

        sp.setContent(innerBox)

        root.setCenter(outer)

        val dragMe = Label("drag me to edge!\n"+"or drop me in scrollpane!")
        root.setTop(dragMe)

        sp.setupScrolling()

        dragMe.setOnDragDetected { event:MouseEvent ->
            val db:Dragboard = dragMe.startDragAndDrop(TransferMode.ANY[0])
            db.setDragView((event.getSource() as Node).snapshot(null, null))

            val content = ClipboardContent()
            content.putString((dragMe.getText()))

            db.setContent(content)
            event.consume();      
        }


        val scene = Scene(root, 640.0, 480.0)
        primaryStage.setScene(scene)
        primaryStage.show()
    }

    private fun ScrollPane.setupScrolling() {
        val scrollTimeline = Timeline()
        scrollTimeline.setCycleCount(Timeline.INDEFINITE)
        scrollTimeline.keyFrames.add(KeyFrame(Duration.millis(20.0), EventHandler {
            //formerly getVerticalScrollbar()
            var sb:ScrollBar? = null
            for (n:Node in this.lookupAll(".scroll-bar")) {
                if (n is ScrollBar) {
                    if (n.getOrientation().equals(Orientation.VERTICAL)) {
                        sb = n
                    }
                }
            }

            //formerly dragScroll()
            if (sb != null) {
                var newValue = sb.getValue() + scrollVelocity
                newValue = Math.min(newValue, 1.0)
                newValue = Math.max(newValue, 0.0)
                sb.setValue(newValue)
            }
        }))

        setOnDragExited { event:DragEvent ->

            if (event.getY() > 0) {
                scrollVelocity = 1.0 / speed
            }
            else {
                scrollVelocity = -1.0 / speed
            }
            if (!dropped){
                scrollTimeline.play()
            }

        }

        setOnDragEntered {
            scrollTimeline.stop()
            dropped = false
        }
        setOnDragDone {
            print("test")
            scrollTimeline.stop()
        }
        setOnDragDropped {event:DragEvent ->
            val db:Dragboard = event.getDragboard()
            (this.getContent() as VBox).getChildren().add(Label(db.getString()));
            scrollTimeline.stop()
            event.setDropCompleted(true)
            dropped = true
        }

        setOnDragOver{ event:DragEvent ->
            event.acceptTransferModes(TransferMode.MOVE)
        }

        setOnScroll { scrollTimeline.stop() }
        setOnMouseClicked { println(scrollTimeline.getStatus()) }

    }
}
