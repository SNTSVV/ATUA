package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGLauncherNode
    private constructor():WTGNode("",""){

    init {
        instance = this
        WTGNode.allNodes.add(this)
    }

    override fun toString(): String {
        return "[Launcher]${super.toString()}"
    }
    companion object{
        var counter = 0
        var instance: WTGLauncherNode? = null
        fun getOrCreateNode(): WTGLauncherNode{
            if (instance != null)
                return instance!!
            else
                return WTGLauncherNode()
        }
    }
}