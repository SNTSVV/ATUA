package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

data class AbstractAction (
        val actionType: AbstractActionType,
        val attributeValuationMap: AttributeValuationMap?=null,
        var extra: Any?=null
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
            AbstractActionType.CLICK,AbstractActionType.ITEM_CLICK -> 2.0
            else -> 1.0
        }
        if (attributeValuationMap == null)
            return actionScore
        val cardinalityScore = when (attributeValuationMap!!.cardinality) {
            Cardinality.ONE -> 1
            Cardinality.MANY -> 2
            else -> 1
        }
        return actionScore*cardinalityScore.toDouble()
    }

    companion object {
        fun computeAbstractActionExtraData(actionType: AbstractActionType, interaction: Interaction<Widget>, guiState: State<Widget>, abstractState: AbstractState): Any? {
            if (actionType == AbstractActionType.RANDOM_KEYBOARD) {
                return interaction.targetWidget
            }
            if (actionType == AbstractActionType.TEXT_INSERT) {
                val avm = abstractState.getAttributeValuationSet(interaction.targetWidget!!,guiState)
                if (avm!=null && avm.localAttributes.containsKey(AttributeType.text)) {
                    return interaction.data
                }
                return null
            }
            if (actionType != AbstractActionType.SWIPE) {
                return null
            }
            val swipeData = Helper.parseSwipeData(interaction.data)
            val begin = swipeData[0]!!
            val end = swipeData[1]!!
            val swipeAction = if (begin.first == end.first) {
                if (begin.second < end.second) {
                    //swipe down
                    "SwipeDown"
                } else {
                    //swipe up
                    "SwipeUp"
                }
            } else if (begin.first < end.first) {
                //siwpe right
                "SwipeRight"
            } else {
                "SwipeLeft"
            }
            return swipeAction
        }

        fun getLaunchAction(): AbstractAction? {
            return AbstractAction(
                    actionType = AbstractActionType.LAUNCH_APP
            )
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
    FAKE_ACTION("FakeAction"),
    TERMINATE("Terminate"),
    WAIT("FetchGUI")
}