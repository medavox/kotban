package com.github.medavox.kotban

import com.github.medavox.kotban.textaria.TextAria
import com.github.medavox.kotban.textaria.Utils.computeTextHeight
import com.sun.javafx.tk.FontMetrics
import com.sun.javafx.tk.Toolkit
import javafx.application.Application
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType.CONFIRMATION
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.Stage
import java.io.File
import kotlin.math.max


//todo:
// line numbers in note contents
// (fix) vertical scrollbars on columns that aren't tall enough to need them,
//   but which disappear after a refresh
// click-to-maximise a single note
// tags - supported through a custom line in the note's text
// filter by tag
// wrap long column names
// show a visual hint about where the note will go
// monospace font?
// togglable MarkDown preview for .md files
// allow dragging to a specific placement in the column?
//   goes against our "ordering is alphabetical only" approach
// when dragging a note, auto-scroll when the mouse is near the window's edge
// user can choose kanban directory
// (fix) prevent a drag-move from overwriting an existing file with the same name
// (fix) very long notes still have scrollbars,
//   cause scrollbars to appear on other notes in the same column
/**terminology:
 * board: the whole thing. A folder with subfolders that each contain 0 or more text files
 * column: contains notes/tasks. represented on-disk by a subfolder of the board
 * note: a task. can be moved between columns. represented on-disk by a text file
 * Implemented with JavaFX, available as part of Java 8's language API.
 * Note that after Java 8, JavaFX was made an external library.
 * @see [https://docs.oracle.com/javase/8/javafx/api](JavaFX javadoc)*/
class Kotban : Application() {
    private val dir = "./testboard"
    val dirFile = File(dir)
    private lateinit var contentContainer: ScrollPane
    val COLUMN_WIDTH = 300.0
    //discovered through experimentation.
    val SCROLLBAR_WIDTH = 40
    /*Instead of making entries editable (and effectively having to write our own text editor),
    * make each entry, upon being clicked, open itself in the user's choice of editor.
    * That allows us to focus on prettifying the Markdown */
    override fun start(primaryStage:Stage) {

        val root = VBox()

        val colScrol = ScrollPane().apply {//horizontal scrollpane for columns
            contentContainer = this

            prefViewportWidthProperty().bind(root.widthProperty())
            isFitToHeight = true
            //content = layoutColumnContents(board.columns)
        }

        val mainButtonBar = ButtonBar().apply {
            //bar.nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
            buttons.addAll(
                Button("Refresh").apply{
                    setOnMouseClicked {
                        val board = Board.loadFrom(dirFile)
                        primaryStage.title = board.name+" - Kotban"
                        colScrol.content = layoutColumnContents(board.columns)
                    }
                },
                Button("New Column").apply{setOnAction {
                    promptForFileName(true, dirFile,
                        "Name of new column:", "New column"
                    )?.let {
                        it.mkdir()
                        contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns)
                    }
                }}
            )
        }

        colScrol.prefViewportHeightProperty().bind(root.heightProperty().subtract(mainButtonBar.heightProperty()))

        root.children.add(mainButtonBar)
        root.children.add(colScrol)
        primaryStage.scene = Scene(root, 600.0, 600.0)
        val board = Board.loadFrom(dirFile)
        primaryStage.title = board.name+" - Kotban"
        primaryStage.show()//stage must be shown before colscrol content is rendered, for some reason
        colScrol.content = layoutColumnContents(board.columns)
    }

    private fun estimateWordBoundaryIgnorantWrapping(fontMetrics: FontMetrics, line:String, maxWidthPx:Double):List<String> {
        if(fontMetrics.computeStringWidth(line) <= maxWidthPx) return listOf(line)
        val output = mutableListOf<String>()
        var wraptLineStart = 0
        var wraptLineEnd = line.length
        while(wraptLineStart != line.length) {
            while (fontMetrics.computeStringWidth(line.substring(wraptLineStart, wraptLineEnd)) > maxWidthPx) {
                wraptLineEnd--
            }
            output.add(line.substring(wraptLineStart, wraptLineEnd))
            wraptLineStart = wraptLineEnd
            wraptLineEnd = line.length
        }
        return output
    }

    private fun estimateWordBoundaryAwareWrapping(fontMetrics: FontMetrics, line:String, maxWidthPx:Double):List<String> {
        if(fontMetrics.computeStringWidth(line) <= maxWidthPx) return listOf(line)
        val output = mutableListOf<String>()
        var wraptLineStart = 0
        var wraptLineEnd = line.length
        fun printIndices() {
            println(line)
            if(wraptLineStart < wraptLineEnd) {
                print(" ".repeat(wraptLineStart)+"^")
                println(" ".repeat(wraptLineEnd-(wraptLineStart+1) )+"^")
                print(" ".repeat(wraptLineStart)+"s")
                println(" ".repeat(wraptLineEnd-(wraptLineStart+1) )+"e")
            }else if(wraptLineStart > wraptLineEnd) {
                print(" ".repeat(wraptLineEnd)+"^")
                println(" ".repeat(wraptLineStart-(wraptLineEnd+1) )+"^")
                print(" ".repeat(wraptLineEnd)+"e")
                println(" ".repeat(wraptLineStart-(wraptLineEnd+1) )+"s")
            }else {
                println(" ".repeat(wraptLineEnd)+"^")
                println(" ".repeat(wraptLineEnd-1)+"s&e")
            }
        }
        //val spacesInARow = 500
        //println("$spacesInARow spaces in a row take up ${fontMetrics.computeStringWidth(" ".repeat(spacesInARow))}px")
        while(wraptLineStart != line.length) {
            while (fontMetrics.computeStringWidth(line.substring(wraptLineStart, wraptLineEnd)) > maxWidthPx) {

                val matchiz = Regex("\\s").findAll(line.substring(wraptLineStart, wraptLineEnd)).toList()
                val backTrackToWordBoundary = matchiz.lastOrNull()?.range?.endInclusive
                if(line.startsWith("Name")) {
                    println("squelch: $matchiz")
                }
                /*val backTrackToWordBoundary = Regex("\\s").
                    findAll(line.substring(wraptLineStart, wraptLineEnd)).lastOrNull()?.range?.endInclusive//?.plus(1)//after the last space*/
                /*backTrackToWordBoundary?.run {
                    println("RANGE first: $first; last: $last; start: $start; endInclusive: $endInclusive")
                }*/
                //the index numbers are relative to the string passed to findAll (which is a substring),
                //which means that after the first moving of the start index, they're all out of sync with the original line
                //solution: append the startPoint to ofsets received from findAll

                //printIndices()
                wraptLineEnd = backTrackToWordBoundary?.plus(wraptLineStart) ?: wraptLineEnd -1
                if(wraptLineEnd <= wraptLineStart) {
                    println("ABOUT TO FUCK UP! wraptStart: $wraptLineStart; wraptLineEnd: $wraptLineEnd; whole line:")
                    printIndices()
                }
            }
            //printIndices()
            output.add(line.substring(wraptLineStart, wraptLineEnd))
            wraptLineStart = wraptLineEnd
            wraptLineEnd = line.length
            //println("new endpoint: $wraptLineEnd")
        }
        return output
    }

    //IMPORTANT: the scrollbar for TextAreas is defined as a scrollpane inside TextAreaSkin.
    //this is also my best lead on word wrapping: it's done by this scrollpane, and is called fitToWidth

    /**Generates the UI component hierarchy for a single Note.*/
    private fun uiOf(note:Note): Node = TitledPane().apply {
        text = note.title
        content = TextAria(note.contents).also { textArea ->
            textArea.isEditable=false
            textArea.isWrapText = true
            //Utils.computeTextHeight(font = textArea.font, text = textArea.text, wrappingWidth = COLUMN_WIDTH, lineSpacing = ???)

            val fontMetrics: FontMetrics = Toolkit.getToolkit().fontLoader.getFontMetrics(textArea.font)
            //manually work out how many rows our text needs
            // the line is wrapped anyway only when the text area's height is larger than one of its containers
            //javafx's word wrap algo tries very hard to start each newly wrapped line on non-space characters
            //(I've only see it do otherwise when there were too many spaces in a row to do otherwise)
            println("\n\n\nNEW FILE\n\n\n")
            val calcedRows = textArea.paragraphs.fold(0) { acc: Int, line: CharSequence ->
                //work out how many wrapped lines each non-empty 'paragraph' takes up
/*                    if(line.isNotEmpty()) {
                        print("line \"${line.substring(0, min(line.length, 10))}\"... ")
                    }else print("empty line ")
                    println("width: $lineWidth; wrapped lines: $linesWhenWrapped")*/
                val selfWrappedLines = estimateWordBoundaryAwareWrapping(fontMetrics, line.toString(),
                //take into account the horizontal space lost to the TextArea's potential scroll bar
                    COLUMN_WIDTH-SCROLLBAR_WIDTH)
                selfWrappedLines.forEach { println(it) }
                acc + selfWrappedLines.size
            }
            //textArea.prefHeight = calcedRows.toDouble() * (fontMetrics.lineHeight + 1.25/*Calculated through sheer fucking trial and error.*/)
            //textArea.prefRowCount = calcedRows

            textArea.prefHeight = textArea.getTaeFuck(COLUMN_WIDTH)
            println("\t\"${note.title}\" calced rows: $calcedRows; calced height: "+textArea.prefHeight)

            //val length = fontMetrics.computeStringWidth(textArea.text)
            //println("\'${note.title}\' height: ${it.height}; prefHeight:${it.prefHeight}")
        }
        contextMenu = ContextMenu(
            MenuItem("Open note in editor").apply{setOnAction{
                openInDefaultTextEditor(note.file)
            }},
            MenuItem("Rename note").apply{setOnAction{
                promptForFileName(false, note.file.parentFile,
                    "rename note file \n\'${note.file.name}\'", note.file.name
                )?.let {
                    note.file.renameTo(it)
                    contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns)
                }
            }},
            MenuItem("Delete note").apply{setOnAction{
                val confirmation = Alert(CONFIRMATION).apply {
                    headerText = "delete this note \'${note.title}\'?"
                    //dialogPane.
                }.showAndWait()
                if(confirmation.isPresent && confirmation.get() == ButtonType.OK) {
                    note.file.delete()
                    contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns)
                }
            }}
        )
        /*setOnDragDone { println("$this: drag done:$it") }
        setOnDragEntered { println("$this: drag entered:$it") }
        setOnDragExited { println("$this: drag exited:$it") }*/
        onDragDetected = EventHandler {event ->
            //println("drag detected:$it")
            val dragBoard = startDragAndDrop(TransferMode.MOVE)
            println("dragBoard:$dragBoard")
            //put a file on the dragboard
            val content = ClipboardContent()
            content.putFiles(listOf(note.file))
            dragBoard.setContent(content)

            event.consume()
        }
    }

    private fun uiOf(column:Column):Node = VBox().also { colContainer ->
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
                            contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns)
                        }
                    }})
                }
            )
        }
        colContainer.children.add(columnButtonBar)
        colContainer.children.add(ScrollPane().also { notesScrollPane ->
            notesScrollPane.prefViewportWidth = COLUMN_WIDTH
            notesScrollPane.isFitToWidth = true
            notesScrollPane.content = VBox().apply {
                if(column.notes.isEmpty()) {
                    //expand empty columns to fill the vertical space,
                    // so notes can be dragged into them
                    prefHeightProperty().bind(contentContainer.heightProperty().
                        subtract(columnButtonBar.heightProperty()))
                    notesScrollPane.vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                }else {
                    notesScrollPane.vbarPolicy = ScrollPane.ScrollBarPolicy.ALWAYS
                }
                notesScrollPane.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
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
                    println("$this: onDragDropped: $event")
                    val db = event.dragboard
                    var success = false
                    if (db.hasFiles()) {
                        println("files: ${db.files}")
                        db.files[0].renameTo(File(column.folder, db.files[0].name))
                        success = true
                        contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns)//todo: figure out how to do UI refresh here
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
        val contextMenu = contextMenuFor(column)
        colContainer.onContextMenuRequested = EventHandler {
            contextMenu.show(colContainer, it.screenX, it.screenY)
        }
    }

    private fun contextMenuFor(column:Column):ContextMenu = ContextMenu(
        MenuItem("Rename Column").apply{setOnAction{
            promptForFileName(true, dirFile,
                "Rename column \'${column.name}\' to:", column.name
            )?.let {
                column.folder.renameTo(it)
                contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns)
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
                        contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns)
                    }
                }else {
                    //column has no notes, so just delete it
                    column.folder.delete()
                    contentContainer.content = layoutColumnContents(Board.loadFrom(dirFile).columns)
                }
            }
        }}
    )

    private fun layoutColumnContents(columns:List<Column>):HBox {
        val uiColumns = HBox()
        for(column in columns) {
            uiColumns.children.add(uiOf(column))
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