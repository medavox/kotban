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

    private var dropped:Boolean = false

    //Higher speed value = slower scroll.
    private val speed = 200

    @Throws(Exception::class)
    override fun start(primaryStage:Stage)  {
        val root = BorderPane()
        val sp = ScrollPane()
        sp.setPrefSize(300.0, 300.0)

        val outer = VBox(sp)

        val innerBox = VBox()
        innerBox.setPrefSize(200.0,1000.0)

        sp.content = innerBox

        root.center = outer

        val dragMe = Label("drag me to edge!\nor drop me in scrollpane!")
        root.top = dragMe

        sp.setupScrolling()

        dragMe.setOnDragDetected { event:MouseEvent ->
            val db:Dragboard = dragMe.startDragAndDrop(*TransferMode.ANY)
            db.dragView = (event.source as Node).snapshot(null, null)

            val content = ClipboardContent()
            content.putString((dragMe.text))

            db.setContent(content)
            event.consume()
        }


        val scene = Scene(root, 640.0, 480.0)
        primaryStage.scene = scene
        primaryStage.show()
    }

    private fun ScrollPane.setupScrolling() {
        val scrollTimeline = Timeline()
        scrollTimeline.cycleCount = Timeline.INDEFINITE
        scrollTimeline.keyFrames.add(KeyFrame(Duration.millis(20.0), EventHandler {
            //formerly getVerticalScrollbar()
            val sb:ScrollBar? = lookupAll(".scroll-bar").
                                filterIsInstance<ScrollBar>().
                                firstOrNull { it.orientation == Orientation.VERTICAL }

            //formerly dragScroll()
            sb?.let {
                var newValue = sb.value + scrollVelocity
                newValue = Math.min(newValue, 1.0)
                newValue = Math.max(newValue, 0.0)
                sb.value = newValue
            }
        }))

        setOnDragExited { event:DragEvent ->
            scrollVelocity = if (event.y > 0) {1.0 / speed} else {-1.0 / speed}
            if (!dropped) {
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
            val db:Dragboard = event.dragboard
            (this.content as VBox).children.add(Label(db.string));
            scrollTimeline.stop()
            event.isDropCompleted = true
            dropped = true
        }

        setOnDragOver{ event:DragEvent ->
            event.acceptTransferModes(TransferMode.MOVE)
        }

        setOnScroll { scrollTimeline.stop() }
        setOnMouseClicked { println(scrollTimeline.status) }
    }
}
