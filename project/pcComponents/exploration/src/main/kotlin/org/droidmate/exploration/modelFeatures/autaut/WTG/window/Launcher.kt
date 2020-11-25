package org.droidmate.exploration.modelFeatures.autaut.WTG.window

import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager

class Launcher
    private constructor(): Window("","Launcher-0",true){
    override fun getWindowType(): String {
        return "Launcher"
    }

    init {
        instance = this
        WindowManager.instance.allWindows.add(this)
    }

    override fun toString(): String {
        return "[Window][Launcher]${super.toString()}"
    }
    companion object{
        var counter = 0
        var instance: Launcher? = null
        fun getOrCreateNode(): Launcher {
            if (instance != null)
                return instance!!
            else
                return Launcher()
        }
    }
}