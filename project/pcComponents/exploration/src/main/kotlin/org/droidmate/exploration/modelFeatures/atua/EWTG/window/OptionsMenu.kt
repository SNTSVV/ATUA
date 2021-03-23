package org.droidmate.exploration.modelFeatures.atua.EWTG.window

import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager

class OptionsMenu (classType: String,
                   nodeId: String = getNodeId(),
                   runtimeCreated: Boolean,
                   isBaseModel: Boolean): Window(classType,nodeId,runtimeCreated,isBaseModel){

    var ownerActivity: Window? = null
    init {
        activityClass = classType
        counter++
    }
    override fun getWindowType(): String {
        return "OptionsMenu"
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][OptionsMenu]-${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "OptionsMenu-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String, runtimeCreated: Boolean, isBaseModel: Boolean): OptionsMenu {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is OptionsMenu}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is OptionsMenu}
            }
            if (node != null)
                return node!! as OptionsMenu
            return OptionsMenu(nodeId = nodeId, classType = classType, runtimeCreated = runtimeCreated,
                        isBaseModel = isBaseModel)
        }
    }
}