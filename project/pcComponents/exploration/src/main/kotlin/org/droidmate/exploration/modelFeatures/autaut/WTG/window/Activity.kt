package org.droidmate.exploration.modelFeatures.autaut.WTG.window

import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager

class Activity(classType: String,
               nodeId: String = getNodeId(),
               fromModel: Boolean): Window(classType,nodeId,fromModel){
    override fun getWindowType(): String {
        return "Activity"
    }

    init {
        allNodes.add(this)
        WindowManager.instance.allWindows.add(this)
        activityClass = classType
        counter++
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][Activity]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<Activity>()
        fun getNodeId(): String = "Activity-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String,fromModel: Boolean=true): Activity {
            val node = allNodes.find { it.windowId == nodeId }
            if (node != null)
                return node!!
            else
                return Activity(nodeId = nodeId, classType = classType, fromModel = fromModel)
        }
    }
}