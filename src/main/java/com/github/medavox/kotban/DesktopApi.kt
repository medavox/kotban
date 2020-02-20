package com.github.medavox.kotban

import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI

/**Based on code from https://stackoverflow.com/a/18004334*/
object DesktopApi {

    private val e = System.err

    fun browse(uri:URI) : Boolean = if (openSystemSpecific(uri.toString())) true else browseDESKTOP(uri)

    fun open(file:File) : Boolean = if (openSystemSpecific(file.path)) true else openDESKTOP(file)

    /**you can try something like
    `runCommand("gimp", "%s", file.getPath())`
    based on user preferences.*/
    fun edit(file:File) : Boolean = if (openSystemSpecific(file.getPath())) true else editDESKTOP(file)


    private fun openSystemSpecific(what:String) : Boolean {
        val os:EnumOS = getOs()
        if (os.isLinux()) {
            if (runCommand("kde-open", "%s", what)) return true
            if (runCommand("gnome-open", "%s", what)) return true
            if (runCommand("xdg-open", "%s", what)) return true
        }

        if (os == EnumOS.MACOS) {
            if (runCommand("open", "%s", what)) return true
        }

        if (os == EnumOS.WINDOWS) {
            if (runCommand("explorer", "%s", what)) return true
        }
        return false
    }


    private fun browseDESKTOP(uri:URI) : Boolean {
        println("Trying to use Desktop.getDesktop().browse() with $uri")
        try {
            if (!Desktop.isDesktopSupported()) {
                e.println("Platform is not supported.")
                return false
            }

            if (!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                e.println("BROWSE is not supported.")
                return false
            }

            Desktop.getDesktop().browse(uri)

            return true
        } catch (t:Throwable) {
            logErr("Error using desktop browse.", t)
            return false
        }
    }


    private fun openDESKTOP(file:File) : Boolean {
        println("Trying to use Desktop.getDesktop().open() with $file")
        try {
            if (!Desktop.isDesktopSupported()) {
                e.println("Platform is not supported.")
                return false
            }

            if (!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                e.println("OPEN is not supported.")
                return false
            }

            Desktop.getDesktop().open(file)

            return true
        } catch (t:Throwable) {
            logErr("Error using desktop open.", t)
            return false
        }
    }


    private fun editDESKTOP(file:File) : Boolean {
        println("Trying to use Desktop.getDesktop().edit() with $file")
        try {
            if (!Desktop.isDesktopSupported()) {
                e.println("Platform is not supported.")
                return false
            }

            if (!Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                e.println("EDIT is not supported.")
                return false
            }

            Desktop.getDesktop().edit(file)
            return true
        } catch (t:Throwable) {
            logErr("Error using desktop edit.", t)
            return false
        }
    }


    private fun runCommand(command:String, args:String, file:String) : Boolean {
        println("Trying to exec:\n   cmd = $command\n   args = $args\n   %s = $file")
        val parts:Array<String> = prepareCommand(command, args, file)

        try {
            val p: Process = Runtime.getRuntime().exec(parts) ?: return false

            try {
                val retval:Int = p.exitValue()
                if (retval == 0) {
                    e.println("Process ended immediately.")
                    return false
                } else {
                    e.println("Process crashed.")
                    return false
                }
            } catch (itse:IllegalThreadStateException) {
                e.println("Process is running.")
                return true
            }
        } catch (e:IOException) {
            logErr("Error running command.", e)
            return false
        }
    }


    private fun prepareCommand(command:String, args:String?, file:String):Array<String> {
        val parts = mutableListOf<String>()
        parts.add(command)

        if (args != null) {
            for (s:String in args.split(" ")) {
                val s2 =  String.format(s, file) // put in the filename thing

                parts.add(s2.trim())
            }
        }

        return parts.toTypedArray()
    }

    private fun logErr(msg:String, t:Throwable) {
        System.err.println(msg)
        t.printStackTrace()
    }

    enum class EnumOS {
        LINUX, MACOS, SOLARIS, UNKNOWN, WINDOWS;

        fun isLinux():Boolean = (this == LINUX || this == SOLARIS)
    }

    private fun getOs():EnumOS = with(System.getProperty("os.name").toLowerCase()) {
        return when {
            contains("win") -> EnumOS.WINDOWS
            contains("mac") -> EnumOS.MACOS
            contains("solaris") -> EnumOS.SOLARIS
            contains("sunos") -> EnumOS.SOLARIS
            contains("linux") -> EnumOS.LINUX
            contains("unix") -> EnumOS.LINUX
            else -> EnumOS.UNKNOWN
        }
    }
}