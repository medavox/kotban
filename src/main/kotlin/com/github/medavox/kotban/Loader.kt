package com.github.medavox.kotban

import java.io.File

object Loader {
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
        val subDirsAndTheirItems:Map<String, List<Item>> = dirsInDir.associate {subdir ->
            //println("stuff in '$it': ${Arrays.toString(it.listFiles())}")
            subdir.name to (subdir.listFiles() ?: arrayOf()).filter {file:File ->
                //println("file in $subdir: $file")
                //filter out non-files, without the right extensions, unreadables, nonexistent, and files >10MB
                file.isFile && file.canRead() && file.exists() && file.length() < (10240 * 1024) &&
                        (file.name.endsWith(".md") || file.name.endsWith(".txt") )
            }.associateWith { foil ->
                //detect charsets
                CharsetDetector.detectCharset(foil)
            }.filter { (file, encoding) ->
                //filter out files with undetected charsets, ie non-text files
                println("encoding of $file: $encoding")
                encoding != null
            }.map { (file, charset) ->
                Item(title = file.name, contents = file.readText())
            }
        }
        println("panes: $subDirsAndTheirItems")
        return Board(name=dir.name, panes=subDirsAndTheirItems)
    }
}

data class Board(val name:String, val panes:Map<String,List<Item>>)

data class Item(val title:String, val contents:String)