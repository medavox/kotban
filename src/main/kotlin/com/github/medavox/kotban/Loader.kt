package com.github.medavox.kotban

import java.io.File
import java.awt.Desktop

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
    val subDirsAndTheirItems:Map<String, List<Note>> = dirsInDir.associate { subdir ->
        //println("stuff in '$it': ${Arrays.toString(it.listFiles())}")
        subdir.name to (subdir.listFiles() ?: arrayOf()).filter {file:File ->
            //println("file in $subdir: $file")
            //filter out non-files, unreadables, nonexistent, >10MB, without the right extensions
            file.isFile && file.canRead() && file.exists() && file.length() < (10240 * 1024) &&
                    (file.name.endsWith(".md") || file.name.endsWith(".txt") )
        }.map { file ->
            Note(file = file, title = file.name, contents = file.readText())
        }
    }.filter { it.value.isNotEmpty() }
    //println("panes: $subDirsAndTheirItems")
    return Board(name=dir.name, columns=subDirsAndTheirItems)
}

data class Board(val name:String, val columns:Map<String,List<Note>>)

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