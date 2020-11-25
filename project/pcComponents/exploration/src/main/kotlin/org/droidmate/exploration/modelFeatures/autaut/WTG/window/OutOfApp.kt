package org.droidmate.exploration.modelFeatures.autaut.WTG.window

import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager

class OutOfApp(nodeId: String= getNodeId(), activity: String): Window(activity,nodeId,false){
    override fun getWindowType(): String {
        return "OutOfApp"
    }

    init {
        allNodes.add(this)
        WindowManager.instance.allWindows.add(this)
        this.activityClass = activity
        counter++
    }

    override fun toString(): String {
        return "[Window][OutOfApp]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<OutOfApp>()
        fun getNodeId(): String = "OutOfApp-${counter+1}"
        fun getOrCreateNode(nodeId:String, activity: String): OutOfApp {
            val node = allNodes.find { it.activityClass == activity }
            if (node != null)
                return node!!
            else
                return OutOfApp(nodeId = nodeId, activity = activity)
        }
    }
}