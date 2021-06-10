package org.droidmate.exploration.modelFeatures.atua.EWTG.window

import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager

class Launcher
    private constructor(): Window("","Launcher-0",true,false){
    override fun copyToRunningModel(): Window {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getWindowType(): String {
        return "Launcher"
    }

    init {
        instance = this

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