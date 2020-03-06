package com.github.medavox.kotban

import java.io.File

data class Board(val name:String, val columns:List<Column>) {
    companion object {
        fun loadFrom(dir: File):Board {
            if(!dir.isDirectory) {
                throw Exception("supplied argument must be a directory")
            }

            if(!dir.canRead()) {
                throw Exception("can't read input path. Probably no permission")
            }

            val dirsInDir:List<File> = dir.listFiles()?.filter { it?.isDirectory ?: false } ?: listOf<File>()

            if(dirsInDir.isEmpty()) {
                throw Exception("no column subdirs in board dir. Exiting.")
            }
            val subDirsWithTheirNotes:List<Column> = dirsInDir.map { subdir ->
                //println("stuff in '$it': ${Arrays.toString(it.listFiles())}")
                Column(subdir.name, subdir, (subdir.listFiles() ?: arrayOf()).filter {file: File ->
                    //println("file in $subdir: $file")
                    //filter out non-files, unreadables, nonexistent, >10MB, without the right extensions
                    file.isValidFile()
                }.map { file ->
                    Note(file = file, title = file.name, contents = file.readText())
                }.sortedBy { it.title })
            }
            //println("panes: $subDirsAndTheirItems")
            return Board(name=dir.name, columns=subDirsWithTheirNotes.sortedBy { it.name })
        }
    }
}

data class Column(val name:String, val folder: File, val notes:List<Note>)

data class Note(val file: File, val title:String, val contents:String)