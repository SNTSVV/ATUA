package org.droidmate.exploration.modelFeatures.atua.ewtg.window

import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager

class ContextMenu (classType: String,
                   nodeId: String= getNodeId(),
                   runtimeCreated:Boolean,
                   isBaseModel: Boolean): Window(classType,nodeId,runtimeCreated,isBaseModel){
    override fun copyToRunningModel(): Window {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getWindowType(): String {
        return "ContextMenu"
    }

    init {
        counter++
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][ContextMenu]${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "ContextMenu-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String, runtimeCreated: Boolean, isBaseModel: Boolean): ContextMenu {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is ContextMenu}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is ContextMenu}
            }
            if (node != null)
                return node!! as ContextMenu
            return ContextMenu(nodeId = nodeId, classType = classType, runtimeCreated = runtimeCreated,isBaseModel = isBaseModel)
        }
    }
}