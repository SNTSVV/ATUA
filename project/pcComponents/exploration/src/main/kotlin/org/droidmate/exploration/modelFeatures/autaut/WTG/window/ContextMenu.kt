package org.droidmate.exploration.modelFeatures.autaut.WTG.window

import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager

class ContextMenu (classType: String,
                   nodeId: String= getNodeId(),
                   fromModel:Boolean): Window(classType,nodeId,fromModel){
    override fun getWindowType(): String {
        return "ContextMenu"
    }

    init {
        allNodes.add(this)
        WindowManager.instance.allWindows.add(this)
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
        val allNodes = ArrayList<ContextMenu>()
        fun getNodeId(): String = "ContextMenu-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String, fromModel: Boolean=true): ContextMenu {
            val node = allNodes.find { it.windowId == nodeId }
            if (node != null)
                return node!!
            else
                return ContextMenu(nodeId = nodeId, classType = classType, fromModel = fromModel)
        }
    }
}