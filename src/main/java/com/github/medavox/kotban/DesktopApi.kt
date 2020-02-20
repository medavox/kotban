package com.github.medavox.kotban;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**Based on code from https://stackoverflow.com/a/18004334*/
object DesktopApi {

    fun browse(uri:URI) : Boolean {
        if (openSystemSpecific(uri.toString())) return true;
        return browseDESKTOP(uri);
    }


    fun open(file:File) : Boolean {
        if (openSystemSpecific(file.getPath())) return true;
        return openDESKTOP(file);
    }


    fun edit(file:File) : Boolean {
        // you can try something like
        // runCommand("gimp", "%s", file.getPath())
        // based on user preferences.
        if (openSystemSpecific(file.getPath())) return true;
        return editDESKTOP(file);
    }


    private fun openSystemSpecific(what:String) : Boolean {
        val os:EnumOS = getOs();
        if (os.isLinux()) {
            if (runCommand("kde-open", "%s", what)) return true;
            if (runCommand("gnome-open", "%s", what)) return true;
            if (runCommand("xdg-open", "%s", what)) return true;
        }

        if (os.isMac()) {
            if (runCommand("open", "%s", what)) return true;
        }

        if (os.isWindows()) {
            if (runCommand("explorer", "%s", what)) return true;
        }
        return false;
    }


    private fun browseDESKTOP(uri:URI) : Boolean {
        logOut("Trying to use Desktop.getDesktop().browse() with " + uri.toString());
        try {
            if (!Desktop.isDesktopSupported()) {
                logErr("Platform is not supported.");
                return false;
            }

            if (!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                logErr("BROWSE is not supported.");
                return false;
            }

            Desktop.getDesktop().browse(uri);

            return true;
        } catch (t:Throwable) {
            logErr("Error using desktop browse.", t);
            return false;
        }
    }


    private fun openDESKTOP(file:File) : Boolean {
        logOut("Trying to use Desktop.getDesktop().open() with " + file.toString());
        try {
            if (!Desktop.isDesktopSupported()) {
                logErr("Platform is not supported.");
                return false;
            }

            if (!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                logErr("OPEN is not supported.");
                return false;
            }

            Desktop.getDesktop().open(file);

            return true;
        } catch (t:Throwable) {
            logErr("Error using desktop open.", t);
            return false;
        }
    }


    private fun editDESKTOP(file:File) : Boolean {
        logOut("Trying to use Desktop.getDesktop().edit() with " + file);
        try {
            if (!Desktop.isDesktopSupported()) {
                logErr("Platform is not supported.");
                return false;
            }

            if (!Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                logErr("EDIT is not supported.");
                return false;
            }

            Desktop.getDesktop().edit(file);
            return true;
        } catch (t:Throwable) {
            logErr("Error using desktop edit.", t);
            return false;
        }
    }


    private fun runCommand(command:String, args:String, file:String) : Boolean {
        logOut("Trying to exec:\n   cmd = " + command + "\n   args = " + args + "\n   %s = " + file);
        val parts:Array<String> = prepareCommand(command, args, file)

        try {
            val p:Process? = Runtime.getRuntime().exec(parts)
            if (p == null) return false;

            try {
                val retval:Int = p.exitValue();
                if (retval == 0) {
                    logErr("Process ended immediately.");
                    return false;
                } else {
                    logErr("Process crashed.");
                    return false;
                }
            } catch (itse:IllegalThreadStateException) {
                logErr("Process is running.");
                return true;
            }
        } catch (e:IOException) {
            logErr("Error running command.", e);
            return false;
        }
    }


    private fun prepareCommand(command:String, args:String?, file:String):Array<String> {
        val parts = mutableListOf<String>()
        parts.add(command);

        if (args != null) {
            for (s:String in args.split(" ")) {
                val s2 =  String.format(s, file) // put in the filename thing

                parts.add(s2.trim());
            }
        }

        return parts.toTypedArray()
    }

    private fun logErr(msg:String, t:Throwable) {
        System.err.println(msg);
        t.printStackTrace();
    }

    private fun logErr(msg:String) {
        System.err.println(msg);
    }

    private fun logOut(msg:String) {
        System.out.println(msg);
    }

    enum class EnumOS {
        linux, macos, solaris, unknown, windows;

        fun isLinux():Boolean {
            return this == linux || this == solaris;
        }


        fun isMac():Boolean {
            return this == macos;
        }


        fun isWindows():Boolean {
            return this == windows;
        }
    }


    fun getOs() : EnumOS {
        val s:String = System.getProperty("os.name").toLowerCase()

        if (s.contains("win")) {
            return EnumOS.windows;
        }

        if (s.contains("mac")) {
            return EnumOS.macos;
        }

        if (s.contains("solaris")) {
            return EnumOS.solaris;
        }

        if (s.contains("sunos")) {
            return EnumOS.solaris;
        }

        if (s.contains("linux")) {
            return EnumOS.linux;
        }

        if (s.contains("unix")) {
            return EnumOS.linux;
        } else {
            return EnumOS.unknown;
        }
    }
}