package org.droidmate.exploration.modelFeatures.atua.EWTG.window

import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager

class OutOfApp(nodeId: String= getNodeId(), activity: String,
               isBaseModel: Boolean): Window(activity,nodeId,true,isBaseModel){
    override fun getWindowType(): String {
        return "OutOfApp"
    }

    init {
        this.activityClass = activity
        counter++
    }

    override fun toString(): String {
        return "[Window][OutOfApp]${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "OutOfApp-${counter+1}"
        fun getOrCreateNode(nodeId:String, activity: String, isBaseModel: Boolean): OutOfApp {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is OutOfApp}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is OutOfApp}
            }
            if (node != null)
                return node!! as OutOfApp
            else
                return OutOfApp(nodeId = nodeId, activity = activity,isBaseModel = isBaseModel)
        }
    }
}