package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.EWTG.Input
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Activity
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.DialogType
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class DialogBehaviorMonitor {
    var checkingDialog: Dialog? = null
    var initialGUIState: State<Widget>? = null
    var checkingUserInput: EWTGWidget? = null

    fun detectDialogType (abstractTransition: AbstractTransition, prevGuiState: State<Widget>, resGuiState: State<Widget>) {
        val prevAbstractState = abstractTransition.source
        val currentAbstractState = abstractTransition.dest
        if (checkingDialog!=null) {
            if (abstractTransition.abstractAction.isLaunchOrReset()) {
                reset()
                return
            }
            if (currentAbstractState.window is Activity) {
                if (currentAbstractState.window.activityClass == checkingDialog!!.activityClass) {
                    if (initialGUIState == resGuiState) {
                        // the dialog's behavior has no effect on the initialGUIState
                        // stop the checking
                        reset()
                    } else {
                        checkingDialog!!.dialogType = DialogType.DATA_INPUT
                        checkingUserInput!!.isUserLikeInput = true
                        reset()
                    }
                } else {
                    reset()
                }
            }
            return
        }
        if (!abstractTransition!!.abstractAction.isWidgetAction())
            return
        if (currentAbstractState.window is Dialog && (currentAbstractState.window as Dialog).dialogType == DialogType.NORMAL) {
            if (prevAbstractState.window is Activity && prevAbstractState.window.activityClass == currentAbstractState.window.activityClass) {
                if (abstractTransition!!.abstractAction.attributeValuationMap!!.getClassName() == "android.widget.Spinner") {
                    (currentAbstractState.window!! as Dialog).dialogType == DialogType.ITEM_SELECTOR
                    abstractTransition.source.EWTGWidgetMapping.get(abstractTransition.abstractAction.attributeValuationMap!!)!!.isUserLikeInput = true
                } else {
                    // we don't know which type of the dialog is yet.
                    // tracking the dialog behavior
                    checkingDialog = currentAbstractState.window as Dialog
                    checkingUserInput = abstractTransition.source.EWTGWidgetMapping.get(abstractTransition.abstractAction.attributeValuationMap!!)!!
                    initialGUIState = prevGuiState
                }
            }
        }

    }

    private fun reset() {
        checkingDialog = null
        checkingUserInput = null
        initialGUIState = null
    }

    companion object{
        val instance: DialogBehaviorMonitor by lazy {
            DialogBehaviorMonitor()
        }
    }
}