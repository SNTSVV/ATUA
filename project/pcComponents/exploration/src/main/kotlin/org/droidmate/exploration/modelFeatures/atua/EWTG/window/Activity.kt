package org.droidmate.exploration.modelFeatures.atua.EWTG.window

import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager

class Activity(classType: String,
               nodeId: String = getNodeId(),
               runtimeCreated: Boolean,
               baseModel: Boolean): Window(classType,nodeId,runtimeCreated, baseModel){
    override fun getWindowType(): String {
        return "Activity"
    }

    init {

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
        fun getNodeId(): String = "Activity-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String, runtimeCreated: Boolean, isBaseMode:Boolean): Activity {
            val node = if (isBaseMode) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is Activity}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is Activity}
            }
            if (node != null)
                return node!! as Activity
            else
                return Activity(nodeId = nodeId, classType = classType, runtimeCreated = runtimeCreated,baseModel = isBaseMode)
        }
    }
}