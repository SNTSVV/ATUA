package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper

data class AbstractAction (
    val actionName: String,
    val widgetGroup: WidgetGroup?=null,
    var extra: Any?=null
    ) {
    fun isItemAction(): Boolean {
        return when(actionName) {
            "ItemClick","ItemLongClick","ItemSelected" -> true
            else -> false
        }
    }
    fun isLaunchOrReset(): Boolean {
        return actionName == "LaunchApp" || actionName == "ResetApp"
    }

    fun isCheckableOrTextInput(): Boolean {
        if (widgetGroup == null)
            return false
        if (widgetGroup.attributePath.isInputField() || widgetGroup.attributePath.isCheckable()) {
            return true
        }
        return false
    }

    fun isActionQueue(): Boolean {
        return actionName == "ActionQueue"
    }

    companion object {
        fun computeAbstractActionExtraData(actionType: String, interactionData: String): Any? {
            if (actionType != AbstractActionType.SWIPE.actionName) {
                return null
            }
            val swipeData = Helper.parseSwipeData(interactionData as String)
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
    }
}

enum class AbstractActionType(val actionName: String) {
    CLICK("Click"),
    LONGCLICK("LongClick"),
    SWIPE("Swipe"),
    PRESS_BACK("PressBack"),
    PRESS_MENU("PressMenu"),
    ROTATE_UI("RotateUI"),
    CLOSE_KEYBOARD("CloseKeyboard"),
    TEXT_INSERT("TextInsert"),
    MINIMIZE_MAXIMIZE("MinimizeMaximize"),
    SEND_INTENT("CallIntent")

}