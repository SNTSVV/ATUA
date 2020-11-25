package org.droidmate.exploration.modelFeatures.autaut.WTG.window

import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager

class FakeWindow(nodeId: String= getNodeId()): Window("",nodeId,true){
    override fun getWindowType(): String {
        return "FakeWindow"
    }

    init {
        allNodes.add(this)
        WindowManager.instance.allWindows.add(this)
        counter++
    }

    override fun toString(): String {
        return "[Window][FakeWindow]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<FakeWindow>()
        fun getNodeId(): String = "FakeWindow-${counter+1}"
        fun getOrCreateNode(nodeId: String= getNodeId()): FakeWindow {
            val node = allNodes.find { it.windowId == nodeId }
            if (node != null)
                return node
            else
                return FakeWindow(nodeId = nodeId)
        }
    }
}