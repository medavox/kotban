package com.github.medavox.kotban

import org.mozilla.universalchardet.ReaderFactory
import org.mozilla.universalchardet.UniversalDetector
import java.io.File

object Loader {
    private val detector:UniversalDetector = UniversalDetector()
    fun load(dir: File):Board {
        if(!dir.isDirectory) {
            throw Exception("supplied argument must be a directory")
        }

        val dirsInDir:List<File> = dir.listFiles()?.filter { it?.isDirectory ?: false } ?: listOf<File>()


        if(dirsInDir.isEmpty()) {
            throw Exception("no bin subdirs in board dir. Exiting.")
        }
        val subDirsAndTheirItems:Map<String, List<Item>> = dirsInDir.associate {
            it.name to it.listFiles().filter {file:File ->
                //filter out non-files, unreadables, nonexistent, and files >10MB
                it.isFile && it.canRead() && it.exists() && it.length() < (10240 * 1024)
            }.filter { file ->
                val encoding:String? = UniversalDetector.detectCharset(file)
                /*detector.reset()
                detector.handleData(file.readBytes())
                detector.dataEnd()

                detector.detectedCharset != null*/
                encoding != null
                //fixme: the UniversalCharsetDetector, can actually detect a wider range of charsets
                // than Kotlin/Java can decode.
                // so we need to limit 'valid' charset outputs to the following:
                //US-ASCII 	Seven-bit ASCII, a.k.a. ISO646-US, a.k.a. the Basic Latin block of the Unicode character set
                //ISO-8859-1   	ISO Latin Alphabet No. 1, a.k.a. ISO-LATIN-1
                //UTF-8 	Eight-bit UCS Transformation Format
                //UTF-16BE 	Sixteen-bit UCS Transformation Format, big-endian byte order
                //UTF-16LE 	Sixteen-bit UCS Transformation Format, little-endian byte order
                //UTF-16 	Sixteen-bit UCS Transformation Format, byte order identified by an optional byte-order mark
            }.map { fle ->  Item(title = fle.name, contents = ReaderFactory.createBufferedReader(fle).readText())}
        }
        return Board(name=dir.name, panes=subDirsAndTheirItems)
    }
}

data class Board(val name:String, val panes:Map<String,List<Item>>)

data class Item(val title:String, val contents:String)