package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Activity
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Dialog
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
            if (abstractTransition.abstractAction.isLaunchOrReset() || abstractTransition.source.isOpeningMenus) {
                reset()
                return
            }
            if (currentAbstractState.window is Activity) {
                if (checkingDialog!!.ownerActivitys.contains(currentAbstractState.window)) {
                    if (initialGUIState == resGuiState) {
                        // the dialog's behavior has no effect on the initialGUIState
                        // stop the checking
                        reset()
                    } else {
                        checkingDialog!!.isInputDialog = true
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
        if (abstractTransition!!.source.isOpeningMenus)
            return
        if (currentAbstractState.window is Dialog && (currentAbstractState.window as Dialog).isInputDialog == false) {
            if (prevAbstractState.window is Activity && (currentAbstractState.window as Dialog).ownerActivitys.contains(prevAbstractState.window)) {
                if (abstractTransition!!.abstractAction.attributeValuationMap!!.getClassName() == "android.widget.Spinner") {
                    (currentAbstractState.window!! as Dialog).isInputDialog = true
                    abstractTransition.source.EWTGWidgetMapping.get(abstractTransition.abstractAction.attributeValuationMap!!)!!.isUserLikeInput = true
                } /*else {
                    // we don't know which type of the dialog is yet.
                    // tracking the dialog behavior
                    checkingDialog = currentAbstractState.window as Dialog
                    checkingUserInput = abstractTransition.source.EWTGWidgetMapping.get(abstractTransition.abstractAction.attributeValuationMap!!)!!
                    initialGUIState = prevGuiState
                }*/
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