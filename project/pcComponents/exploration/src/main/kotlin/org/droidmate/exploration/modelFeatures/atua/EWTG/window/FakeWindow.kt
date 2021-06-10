package org.droidmate.exploration.modelFeatures.atua.EWTG.window

import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager

class FakeWindow(nodeId: String= getNodeId(),isBaseModel: Boolean): Window("",nodeId,true,isBaseModel){
    override fun copyToRunningModel(): Window {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getWindowType(): String {
        return "FakeWindow"
    }

    init {
        counter++
    }

    override fun toString(): String {
        return "[Window][FakeWindow]${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "FakeWindow-${counter+1}"
        fun getOrCreateNode(nodeId: String= getNodeId(),isBaseModel: Boolean): FakeWindow {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is FakeWindow}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is FakeWindow}
            }
            if (node != null)
                return node!! as FakeWindow
            else
                return FakeWindow(nodeId = nodeId,isBaseModel = isBaseModel)
        }
    }
}