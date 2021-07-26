package org.droidmate.exploration.modelFeatures.atua.ewtg.window

import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager

class Dialog(classType: String,
             nodeId: String= getNodeId(),
             var dialogType: DialogType,
             val allocMethod: String,// can only acquired by ewtg
             val isGrantedRuntimeDialog: Boolean,
             runtimeCreated: Boolean,
             isBaseModel:Boolean): Window(classType,nodeId,runtimeCreated,isBaseModel){

    override fun copyToRunningModel(): Window {
        val newDialog = Dialog.getOrCreateNode(
                nodeId =  Dialog.getNodeId(),
                classType = this.classType,
                allocMethod = this.allocMethod,
                runtimeCreated = this.isRuntimeCreated,
                isBaseModel = false
        )
        newDialog.isInputDialog = this.isInputDialog
        return newDialog
    }

    var ownerActivitys: HashSet<Window> = HashSet();
    var isInputDialog: Boolean = false
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
        fun getOrCreateNode(nodeId: String, classType: String, allocMethod: String, runtimeCreated: Boolean ,isBaseModel: Boolean,isGrantedRuntimeDialog: Boolean = false): Dialog {
            val node = if (isBaseModel) {
                WindowManager.instance.baseModelWindows.find { it.classType == classType
                        && it is Dialog}
            } else {
                WindowManager.instance.updatedModelWindows.find { it.classType == classType
                        && it is Dialog}
            }
            val dialogType = if (runtimeCreated)
                    DialogType.LIBRARY_DIALOG
            else
                    WindowManager.instance.dialogClasses.filter { it.value.contains(classType) }.entries.firstOrNull()?.key?:DialogType.UNKNOWN
            if (node != null)
                return node!! as Dialog
            else
                return Dialog(nodeId = nodeId
                        , classType = classType
                        , dialogType = dialogType
                        , allocMethod = allocMethod
                        , runtimeCreated = runtimeCreated
                        , isGrantedRuntimeDialog = isGrantedRuntimeDialog
                        ,isBaseModel = isBaseModel)
        }
    }
}

enum class DialogType {
    LIBRARY_DIALOG,
    APPLICATION_DIALOG,
    DIALOG_FRAGMENT,
    UNKNOWN
}
