package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

data class AbstractAction (
    val actionName: String,
    val widgetGroup: WidgetGroup?=null,
    val extra: Any?=""
    )

enum class AbstractActionType(val actionName: String) {
    CLICK("Click"),
    LONGCLICK("LongClick"),
    SWIPE("Swipe"),
    PRESS_BACK("PressBack"),
    PRESS_MENU("PressMenu"),

}