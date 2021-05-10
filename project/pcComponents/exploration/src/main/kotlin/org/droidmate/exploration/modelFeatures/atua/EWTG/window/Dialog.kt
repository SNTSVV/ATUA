package org.droidmate.exploration.modelFeatures.atua.EWTG.window

import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager

class Dialog(classType: String,
             nodeId: String= getNodeId(),
             allocMethod: String,// can only acquired by ewtg
             runtimeCreated: Boolean,
             isBaseModel:Boolean): Window(classType,nodeId,runtimeCreated,isBaseModel){
    var ownerActivity: Window? = null;
    var dialogType: DialogType = DialogType.NORMAL
    init {
        counter++
    }
    override fun getWindowType(): String {
        return "Dialog"
    }
    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][Dialog]${super.toString()}"
    }
    companion object{
        var counter = 0
        fun getNodeId(): String = "Dialog-${counter+1}"
        fun getOrCreateNode(nodeId: String, classType: String, allocMethod: String, runtimeCreated: Boolean, isBaseModel: Boolean): Dialog {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.windowId == nodeId
                        && it is Dialog}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.windowId == nodeId
                        && it is Dialog}
            }
            if (node != null)
                return node!! as Dialog
            else
                return Dialog(nodeId = nodeId, classType = classType, allocMethod = allocMethod, runtimeCreated = runtimeCreated,isBaseModel = isBaseModel)
        }
    }
}

enum class DialogType {
    NORMAL,
    ITEM_SELECTOR,
    DATA_INPUT
}
