package com.github.medavox.kotban

import java.io.File
import java.awt.Desktop

/**
 * The margin around the control that a user can click in to start resizing
 * the region.
 */
const val RESIZE_MARGIN = 10

fun load(dir: File):Board {
    if(!dir.isDirectory) {
        throw Exception("supplied argument must be a directory")
    }

    if(!dir.canRead()) {
        throw Exception("can't read input path. Probably no permission")
    }

    val dirsInDir:List<File> = dir.listFiles()?.filter { it?.isDirectory ?: false } ?: listOf<File>()

    if(dirsInDir.isEmpty()) {
        throw Exception("no bin subdirs in board dir. Exiting.")
    }
    val subDirsWithTheirNotes:List<Column> = dirsInDir.map { subdir ->
        //println("stuff in '$it': ${Arrays.toString(it.listFiles())}")
        Column(subdir.name, subdir, (subdir.listFiles() ?: arrayOf()).filter {file:File ->
            //println("file in $subdir: $file")
            //filter out non-files, unreadables, nonexistent, >10MB, without the right extensions
            file.isFile && file.canRead() && file.exists() && file.length() < (10240 * 1024) &&
                    (file.name.endsWith(".md") || file.name.endsWith(".txt") )
        }.map { file ->
            Note(file = file, title = file.name, contents = file.readText())
        })
    }
    //println("panes: $subDirsAndTheirItems")
    return Board(name=dir.name, columns=subDirsWithTheirNotes)
}

data class Board(val name:String, val columns:List<Column>)

data class Column(val name:String, val folder:File, val notes:List<Note>)

data class Note(val file:File, val title:String, val contents:String)

fun openInDefaultTextEditor(file:File) {
    val isValid = file.isFile && file.canRead() && file.exists() && file.length() < (10240 * 1024)
    if(!isValid) {
        System.err.println("invalid file for text editing: $file")
        return
    }
    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
        val cmd = "rundll32 url.dll,FileProtocolHandler " + file.canonicalPath
        Runtime.getRuntime().exec(cmd)
    } else {
        Desktop.getDesktop().edit(file)
    }
}