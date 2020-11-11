package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.Widget

data class AbstractAction (
        val actionType: AbstractActionType,
        val attributeValuationSet: AttributeValuationSet?=null,
        var extra: Any?=null
    ) {

    init {
    }

    fun isItemAction(): Boolean {
        return when(actionType) {
            AbstractActionType.ITEM_CLICK,AbstractActionType.ITEM_LONGCLICK,AbstractActionType.ITEM_SELECTED -> true
            else -> false
        }
    }
    fun isWidgetAction(): Boolean {
        return attributeValuationSet!=null
    }
    fun isLaunchOrReset(): Boolean {
        return actionType == AbstractActionType.LAUNCH_APP || actionType == AbstractActionType.RESET_APP
    }

    fun isCheckableOrTextInput(): Boolean {
        if (attributeValuationSet == null)
            return false
        if (attributeValuationSet.attributePath.isInputField() || attributeValuationSet.attributePath.isCheckable()) {
            return true
        }
        return false
    }

    fun isActionQueue(): Boolean {
        return actionType == AbstractActionType.ACTION_QUEUE
    }

    fun getScore(): Double {
        var actionScore = when(actionType) {
            AbstractActionType.PRESS_BACK -> 0.5
            AbstractActionType.SWIPE, AbstractActionType.LONGCLICK -> 1.0
            AbstractActionType.CLICK,AbstractActionType.ITEM_LONGCLICK -> 2.0
            AbstractActionType.ITEM_CLICK -> 4.0
            else -> 1.0
        }
        if (attributeValuationSet == null)
            return actionScore
        val cardinalityScore = when (attributeValuationSet!!.cardinality) {
            Cardinality.ONE -> 1
            Cardinality.MANY -> 2
            else -> 1
        }
        return actionScore*cardinalityScore.toDouble()
    }

    companion object {
        fun computeAbstractActionExtraData(actionType: AbstractActionType, interaction: Interaction<Widget>): Any? {
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