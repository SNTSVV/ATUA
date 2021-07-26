package org.droidmate.exploration.modelFeatures.atua.ewtg.window

import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager

class OutOfApp(nodeId: String= getNodeId(), activity: String,
               isBaseModel: Boolean): Window(activity,nodeId,true,isBaseModel){
    override fun copyToRunningModel(): Window {
        val newWindow = OutOfApp.getOrCreateNode(
                nodeId = getNodeId(),
                activity = this.classType,
                isBaseModel = false
        )
        return newWindow
    }

    override fun getWindowType(): String {
        return "OutOfApp"
    }

    init {
        this.classType = activity
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