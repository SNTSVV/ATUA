package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

data class AbstractAction (
        val actionType: AbstractActionType,
        val attributeValuationMap: AttributeValuationMap?=null,
        val extra: Any?=null
    ) {
    override fun hashCode(): Int {
        var hash = 31
        hash += actionType.hashCode()
        if (attributeValuationMap!=null)
            hash+=attributeValuationMap!!.hashCode
        if (extra!=null)
            hash+=extra!!.hashCode()
        return hash
    }
    override fun equals(other: Any?): Boolean {
        if (other !is AbstractAction)
            return false
        return this.hashCode() == other.hashCode()
    }
    fun isItemAction(): Boolean {
        return when(actionType) {
            AbstractActionType.ITEM_CLICK,AbstractActionType.ITEM_LONGCLICK,AbstractActionType.ITEM_SELECTED -> true
            else -> false
        }
    }
    fun isWidgetAction(): Boolean {
        return attributeValuationMap!=null
    }
    fun isLaunchOrReset(): Boolean {
        return actionType == AbstractActionType.LAUNCH_APP || actionType == AbstractActionType.RESET_APP
    }

    fun isCheckableOrTextInput(): Boolean {
        if (attributeValuationMap == null)
            return false
        if (attributeValuationMap.isInputField()) {
            return true
        }
        val className = attributeValuationMap.getClassName()
        return when(className) {
            "android.widget.RadioButton", "android.widget.CheckBox", "android.widget.Switch", "android.widget.ToggleButton" -> true
            else -> false
        }
    }

    fun isActionQueue(): Boolean {
        return actionType == AbstractActionType.ACTION_QUEUE
    }

    fun getScore(): Double {
        var actionScore = when(actionType) {
            AbstractActionType.PRESS_BACK -> 0.5
            AbstractActionType.LONGCLICK,AbstractActionType.ITEM_LONGCLICK,AbstractActionType.SWIPE -> 2.0
            AbstractActionType.CLICK,AbstractActionType.ITEM_CLICK -> 4.0
            else -> 1.0
        }
        if (attributeValuationMap == null)
            return actionScore
        return actionScore
    }

    companion object {
        fun normalizeActionType(interaction: Interaction<Widget>, prevState: State<*>): AbstractActionType {
            val actionType = interaction.actionType
            var abstractActionType = when (actionType) {
                "Tick" -> AbstractActionType.CLICK
                "ClickEvent" -> AbstractActionType.CLICK
                "LongClickEvent" -> AbstractActionType.LONGCLICK
                else -> AbstractActionType.values().find { it.actionName.equals(actionType) }
            }
            if (abstractActionType == AbstractActionType.CLICK && interaction.targetWidget == null) {
                if (interaction.data.isBlank()) {
                    abstractActionType = AbstractActionType.CLICK_OUTBOUND
                } else {
                    val guiDimension = Helper.computeGuiTreeDimension(prevState)
                    val clickCoordination = Helper.parseCoordinationData(interaction.data)
                    if (clickCoordination.first < guiDimension.leftX || clickCoordination.second < guiDimension.topY) {
                        abstractActionType = AbstractActionType.CLICK_OUTBOUND
                    }
                }
            }
            if (abstractActionType == AbstractActionType.CLICK && interaction.targetWidget != null && interaction.targetWidget!!.isKeyboard) {
                abstractActionType = AbstractActionType.RANDOM_KEYBOARD
            }
            if (abstractActionType == null) {
                throw Exception("No abstractActionType for $actionType")
            }
            return abstractActionType
        }

        fun computeAbstractActionExtraData(actionType: AbstractActionType, interaction: Interaction<Widget>, guiState: State<Widget>, abstractState: AbstractState, atuaMF: ATUAMF): Any? {
            if (actionType != AbstractActionType.SWIPE) {
                return null
            }
            val swipeData = Helper.parseSwipeData(interaction.data)
            val begin = swipeData[0]!!
            val end = swipeData[1]!!
            val swipeAction = Helper.getSwipeDirection(begin, end)
            return swipeAction
        }

        fun getLaunchAction(): AbstractAction? {
            return AbstractAction(
                    actionType = AbstractActionType.LAUNCH_APP
            )
        }

        fun getOrCreateAbstractAction(actionType: AbstractActionType,
                                      interaction: Interaction<Widget>,
                                      guiState: State<Widget>,
                                      abstractState: AbstractState,
                                      attributeValuationMap: AttributeValuationMap?,
                                      atuaMF: ATUAMF): AbstractAction {
            val actionData = computeAbstractActionExtraData(actionType, interaction, guiState, abstractState, atuaMF)

            val abstractAction: AbstractAction
            val availableAction = abstractState.getAvailableActions().find {
                it.actionType == actionType
                        && it.attributeValuationMap == attributeValuationMap
                        && it.extra == actionData
            }

            if (availableAction == null) {
                abstractAction = AbstractAction(
                        actionType = actionType,
                        attributeValuationMap = attributeValuationMap,
                        extra = actionData
                )
                abstractState.addAction(abstractAction)
            } else {
                abstractAction = availableAction
            }
            return abstractAction
        }
    }

}

enum class AbstractActionType(val actionName: String) {
    CLICK("Click"),
    LONGCLICK("LongClick"),
    ITEM_CLICK("ItemClick"),
    ITEM_LONGCLICK("ItemLongClick"),
    ITEM_SELECTED("ItemSelected"),
    SWIPE("Swipe"),
    PRESS_BACK("PressBack"),
    PRESS_MENU("PressMenu"),
    ROTATE_UI("RotateUI"),
    CLOSE_KEYBOARD("CloseKeyboard"),
    TEXT_INSERT("TextInsert"),
    MINIMIZE_MAXIMIZE("MinimizeMaximize"),
    SEND_INTENT("CallIntent"),
    RANDOM_CLICK("RandomClick"),
    CLICK_OUTBOUND("ClickOutbound"),
    ENABLE_DATA("EnableData"),
    DISABLE_DATA("DisableData"),
    LAUNCH_APP("LaunchApp"),
    RESET_APP("ResetApp"),
    ACTION_QUEUE("ActionQueue"),
    PRESS_HOME("PressHome"),
    RANDOM_KEYBOARD("RandomKeyboard"),
    UNDERIVED("Underived"),
    FAKE_ACTION("FakeAction"),
    TERMINATE("Terminate"),
    WAIT("FetchGUI")
}