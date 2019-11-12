package com.github.medavox.kotban

import tornadofx.*

class MyApp: App(Gui::class)

fun main(args: Array<String>) {
    launch<MyApp>(args)
}