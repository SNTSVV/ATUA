package org.droidmate.exploration.modelFeatures.autaut.WTG.window

import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager

class OptionsMenu (classType: String,
                   nodeId: String = getNodeId(),
                   fromModel: Boolean): Window(classType,nodeId,fromModel){
    override fun getWindowType(): String {
        return "OptionsMenu"
    }

    init {
        activityClass = classType
        allNodes.add(this)
        WindowManager.instance.allWindows.add(this)
        counter++
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][OptionsMenu]-${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<OptionsMenu>()
        fun getNodeId(): String = "OptionsMenu-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String,fromModel: Boolean=true): OptionsMenu {
            val node = allNodes.find { it.windowId == nodeId }
            if (node != null)
                return node!!
            else
                return OptionsMenu(nodeId = nodeId, classType = classType, fromModel = fromModel)
        }
    }
}