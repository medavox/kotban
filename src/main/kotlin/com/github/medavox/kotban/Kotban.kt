package com.github.medavox.kotban

import com.github.medavox.kotban.textaria.TextAria
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType.CONFIRMATION
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Duration
import java.io.File


//todo:
// line numbers in note contents
// button: maximise a single note
// tags - supported through a custom line in the note's text
// filter by tag
// wrap long column names
// wrap long note names
// show a visual hint about where the note will go
// togglable MarkDown preview for .md files
// allow dragging to a specific placement in the column?
//   goes against our "ordering is alphabetical only" approach
// (fix) prevent a drag-move from overwriting an existing file with the same name
// button: Expand/collapse all notes in a single column
// a way to minimise a column (hide it like notes can be hidden)
/**terminology:
 * board: the whole thing. A folder with subfolders that each contain 0 or more text files
 * column: contains notes/tasks. represented on-disk by a subfolder of the board
 * note: a task. can be moved between columns. represented on-disk by a text file
 * Implemented with JavaFX, available as part of Java 8's language API.
 * Note that after Java 8, JavaFX was made an external library.
 * @see [https://docs.oracle.com/javase/8/javafx/api](JavaFX javadoc)*/
class Kotban : Application() {
    private var chosenDirFile:File? = null
    private lateinit var contentContainer: ScrollPane
    private lateinit var mainButtonBar: ButtonBar
    private var allNoteNodes:MutableSet<TitledPane> = mutableSetOf()
    private var scrollVelocity:Double = 0.0

    val COLUMN_WIDTH = 300.0
    val EDGE_DRAG_SCROLLING_MARGIN = 64.0

    /*Instead of making entries editable (and effectively having to write our own text editor),
    * make each entry, upon being clicked, open itself in the user's choice of editor.
    * That allows us to focus on prettifying the Markdown */
    override fun start(primaryStage:Stage) {

        val root = VBox()
        contentContainer = ScrollPane().apply {//horizontal scrollpane for columns
            prefViewportWidthProperty().bind(root.widthProperty())
            isFitToHeight = true

            val scrollTimeline = Timeline().also { tl ->
                tl.cycleCount = Timeline.INDEFINITE
                tl.keyFrames.add(KeyFrame(Duration.millis(20.0), EventHandler {
                    var newValue = this.hvalue + scrollVelocity
                    newValue = Math.min(newValue, 1.0)
                    newValue = Math.max(newValue, 0.0)
                    this.hvalue = newValue
                }))
            }

            setOnDragOver {event: DragEvent ->
                //Higher speed value = slower scroll.
                val speed = 70
                val minDistanceFromRightEdge = this.width - EDGE_DRAG_SCROLLING_MARGIN
                if(event.x < EDGE_DRAG_SCROLLING_MARGIN) {//dragging is occurring near the left edge; scroll left
                    val distanceMultiplier = (EDGE_DRAG_SCROLLING_MARGIN - event.x) / EDGE_DRAG_SCROLLING_MARGIN
                    scrollVelocity = (-1.0 / speed) * distanceMultiplier
                    scrollTimeline.play()
                }
                else if (event.x > minDistanceFromRightEdge) {//near right edge, so scroll right
                    val distanceMultiplier = (event.x - minDistanceFromRightEdge) / EDGE_DRAG_SCROLLING_MARGIN
                    scrollVelocity = (1.0 / speed) * distanceMultiplier
                    scrollTimeline.play()
                }
                else {//dragging is occurring near neither edge; stop scrolling
                    scrollVelocity = 0.0
                    scrollTimeline.stop()
                }
                println("drag over event x:"+event.x)
                event.consume()
            }
        }

        mainButtonBar = ButtonBar().apply {
            //bar.nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
            buttons.addAll(
                Button("Open Board…").apply{setOnAction {
                    val dc = DirectoryChooser()
                    //use the directory of the previous choice, if we've made one
                    dc.initialDirectory = chosenDirFile?.parentFile ?: File(System.getProperty("user.home", "."))
                    dc.title = "Choose a board directory"
                    chosenDirFile = dc.showDialog(null)
                    chosenDirFile?.let {
                        val board = Board.loadFrom(it)
                        primaryStage.title = board.name + " - Kotban"
                        contentContainer.content = layoutColumnContents(board.columns, it)
                    }
                }},
                Button("Refresh").apply{ setOnAction {
                    chosenDirFile?.let {
                        val board = Board.loadFrom(it)
                        primaryStage.title = board.name + " - Kotban"
                        contentContainer.content = layoutColumnContents(board.columns, it)
                    }
                }},
                Button("Expand/Collapse All").apply{ setOnAction {
                    allNoteNodes.forEach { it.isExpanded = !it.isExpanded }
                }},
                Button("New Column").apply{ setOnAction {
                    chosenDirFile?.let { dirFileNotNull ->
                        promptForFileName(
                            true, dirFileNotNull,
                            "Name of new column:", "New column"
                        )?.let {
                            it.mkdir()
                            contentContainer.content = layoutColumnContents(Board.loadFrom(dirFileNotNull).columns, it)
                        }
                    }
                }}
            )
        }

        contentContainer.prefViewportHeightProperty().bind(root.heightProperty().subtract(mainButtonBar.heightProperty()))

        root.children.add(mainButtonBar)
        root.children.add(contentContainer)
        primaryStage.scene = Scene(root, 800.0, 600.0)
        //val board = Board.loadFrom(dirFile)
        primaryStage.title = "Kotban"
        //primaryStage.title = board.name+" - Kotban"
        primaryStage.show()//stage must be shown before colscrol content is rendered, for some reason
        //contentContainer.content = layoutColumnContents(board.columns)
    }

    /**Generates the UI component hierarchy for a single Note.*/
    private fun uiOf(note:Note, dirFile:File): Node = TitledPane().apply {
        //text = if(note.title.length < 30) note.title else note.title.substring(0, 30)+"…"//truncate long filenames
        text = note.title
        isExpanded = false
        isAnimated = false
        content = TextAria(note.contents).also { textArea ->
            textArea.isEditable=false
            textArea.isWrapText = true
            textArea.prefHeightProperty().bind(textArea.doubleBinding)
            textArea.font = Font.font("monospace")
        }
        contextMenu = ContextMenu(
            MenuItem("Open note in editor").apply{setOnAction{
                note.file.openInDefaultTextEditor()
            }},
            MenuItem("Rename note").apply{setOnAction{
                promptForFileName(false, note.file.parentFile,
                    "rename note file \n\'${note.file.name}\'", note.file.name
                )?.let {
                    note.file.renameTo(it)
                    contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns, dirFile)
                }
            }},
            MenuItem("Delete note").apply{setOnAction{
                val confirmation = Alert(CONFIRMATION).apply {
                    headerText = "delete this note \'${note.title}\'?"
                    //dialogPane.
                }.showAndWait()
                if(confirmation.isPresent && confirmation.get() == ButtonType.OK) {
                    note.file.delete()
                    contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns, dirFile)
                }
            }}
        )

        onDragDetected = EventHandler {event ->
            //println("drag detected:$it")
            val dragBoard = startDragAndDrop(TransferMode.MOVE)
            dragBoard.dragView = (event.source as Node).snapshot(null, null)
            println("dragBoard:$dragBoard")
            //put a file on the dragboard
            val content = ClipboardContent()
            content.putFiles(listOf(note.file))
            dragBoard.setContent(content)

            event.consume()
        }
        allNoteNodes.add(this)
    }

    private fun uiOf(column:Column, dirFile:File):Node = VBox().also { colContainer ->
        val columnButtonBar = HBox().also { bar ->
            bar.children.addAll(
                Label(column.name+" - "+column.notes.size).apply{
                    //maxWidth = 200.0
                    isWrapText = true
                    //prefHeight = 120.0
                    //this.autosize()
                    //prefHeightProperty().bind()
                },
                ButtonBar().also{ butts ->
                    butts.buttons.addAll(Button("New Note").apply {setOnAction {
                        promptForFileName(false, column.folder,
                            "Name of new note:", "new note.txt"
                        )?.let {
                            it.createNewFile()
                            contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns, dirFile)
                        }
                    }})
                }
            )
        }
        colContainer.children.add(columnButtonBar)
        colContainer.children.add(ScrollPane().also { notesScrollPane ->
            notesScrollPane.prefViewportWidth = COLUMN_WIDTH
            notesScrollPane.isFitToWidth = true

            notesScrollPane.prefHeightProperty().bind(contentContainer.heightProperty().
                subtract(columnButtonBar.heightProperty()).
                subtract(mainButtonBar.heightProperty())
            )
            notesScrollPane.content = VBox().apply {
                if(column.notes.isEmpty() || contentContainer.height >= notesScrollPane.height) {
                    //expand empty columns to fill the vertical space,
                    // so notes can be dragged into them
                    minHeightProperty().bind(contentContainer.heightProperty().
                        subtract(columnButtonBar.heightProperty()).
                        subtract(mainButtonBar.heightProperty())
                    )
                }
                notesScrollPane.vbarPolicy = ScrollPane.ScrollBarPolicy.ALWAYS
                notesScrollPane.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                onDragOver = EventHandler { event ->
                    /* accept only if it's not dragged from the same node,
                         * and if it has a File as data */
                    if (event.gestureSource !== this && event.dragboard.hasFiles()) {
                        //println("$this: drag over: $event")
                        event.acceptTransferModes(*TransferMode.COPY_OR_MOVE)
                    }
                    //now instead of consuming, allow the content container to receive the drag_over event too
                    //event.consume()
                }

                onDragDropped = EventHandler { event ->
                    println("$this: onDragDropped: $event")
                    val db = event.dragboard
                    var success = false
                    if (db.hasFiles()) {
                        println("files: ${db.files}")
                        db.files[0].renameTo(File(column.folder, db.files[0].name))
                        success = true
                        contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns, dirFile)
                    }
                    /* let the source know whether the string was successfully
                         * transferred and used */
                    event.isDropCompleted = success
                    event.consume()
                }
                for (note in column.notes) {
                    children.add(uiOf(note, dirFile))
                }
            }
        })
        val contextMenu = contextMenuFor(column, dirFile)
        colContainer.onContextMenuRequested = EventHandler {
            contextMenu.show(colContainer, it.screenX, it.screenY)
        }
        DragResizerX(colContainer).makeResizable()
    }

    private fun contextMenuFor(column:Column, dirFile:File):ContextMenu = ContextMenu(
        MenuItem("Rename Column").apply{setOnAction{
            promptForFileName(true, dirFile,
                "Rename column \'${column.name}\' to:", column.name
            )?.let {
                column.folder.renameTo(it)
                contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns, dirFile)
            }
        }},
        MenuItem("Delete Column").apply{setOnAction{
            val confirmation = Alert(CONFIRMATION).apply {
                headerText = "delete this column \'${column.name}\'?"
            }.showAndWait()
            if(confirmation.isPresent && confirmation.get() == ButtonType.OK) {
                if(column.notes.isNotEmpty()) {
                    val doubleCheck = Alert(CONFIRMATION).apply{
                        headerText = column.folder.list()!!.fold(
                            "The column \'${column.name}\' isn't empty!\n"+
                                    "Are you SURE you want to delete the column and all its contents:"
                        ) { acc:String, elem:String ->
                            acc + "\n"+elem
                        }+"?"
                    }.showAndWait()
                    if(doubleCheck.isPresent && doubleCheck.get() == ButtonType.OK) {
                        //in order to delete a directory with contents on the JVM,
                        //we have to do our own recursive delete
                        column.folder.recursivelyDelete()
                        println("deleted column ${column.name}")
                        contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns, dirFile)
                    }
                }else {
                    //column has no notes, so just delete it
                    column.folder.delete()
                    contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns, dirFile)
                }
            }
        }}
    )

    private fun layoutColumnContents(columns:List<Column>, dirFile:File):HBox {
        val uiColumns = HBox()
        allNoteNodes = mutableSetOf()//reset collection of all nodes, in case there was one before
        for(column in columns) {
            uiColumns.children.add(uiOf(column, dirFile))
        }
        return uiColumns
    }

    private fun promptForFileName(trueDirFalseFile:Boolean,
                                  parent:File,
                                  promptText:String,
                                  initialBoxText:String
    ):File? {
        var isValid: Boolean
        do{
            val tid = TextInputDialog(initialBoxText).apply {
                headerText  = promptText
                graphic = null
            }

            val output:String = tid.showAndWait().orElse("")
            val escaped = output.replace(Regex("[^a-zA-Z0-9 _.-]"), "_")
            val newFile = File(parent, escaped)

            if(output.isEmpty()) {//canceled
                isValid = true
            }else if(output.isBlank()) {
                isValid = false
                Alert(Alert.AlertType.ERROR,
                    "file name must not be blank").showAndWait()
            } else if(!trueDirFalseFile && !PLAIN_TEXT_FILE_EXTENSIONS.any{output.endsWith(".$it")}) {
                isValid = false
                Alert(Alert.AlertType.ERROR,
                    PLAIN_TEXT_FILE_EXTENSIONS.fold("file name must end in a supported extension:\n"){
                            acc, elem -> "$acc .$elem"
                    }).showAndWait()
            } else if(newFile.exists()) {
                isValid = false
                Alert(Alert.AlertType.ERROR,
                    "A File or directory with that name already exists").showAndWait()
            }else {
                //isValid = true
                return newFile
            }
        } while (!isValid)
        return null
    }

}
//in the column func, need a references to the stage to set the title