package org.droidmate.exploration.modelFeatures.regression.staticModel


open class StaticEvent (
            val eventType: EventType,
            val eventHandlers: ArrayList<String>,
            val widget: StaticWidget?,
            var activity: String
       // var sourceWindow: WTGNode?
)
{
    val modifiedMethods = HashMap<String,Boolean>() //method id,
    val modifiedMethodStatement = HashMap<String, Boolean>() //statement id,
    val verifiedEventHandlers = HashSet<String>() //if an event handler appears in this set, we will not remove it from event's handlers
    var data: Any? = null
    var exerciseCount: Int = 0

    init {
        allStaticEvents.add(this)
    }

    fun convertToExplorationActionName(): String{
        return when (eventType){
            EventType.click, EventType.touch -> "Click"
            EventType.long_click -> "LongClick"
            EventType.item_click -> "ItemClick"
            EventType.item_long_click -> "ItemLongClick"
            EventType.item_selected -> "ItemSelected"
            EventType.enter_text -> "TextInput"
            EventType.editor_action -> "EditorAction"
            EventType.implicit_menu -> "PressMenu"
            EventType.implicit_rotate_event -> "RotateUI"
            EventType.implicit_back_event -> "PressBack"
            EventType.press_back -> "PressBack"
            EventType.callIntent -> "CallIntent"
            EventType.implicit_home_event -> "MinimizeMaximize"
            else -> eventType.name
        }
    }

    companion object{
        val allStaticEvents = arrayListOf<StaticEvent>()
        fun isNoWidgetEvent(action: String): Boolean {
            return (action == EventType.implicit_back_event.name
                    || action == EventType.implicit_rotate_event.name
                    || action == EventType.implicit_power_event.name
                    || action == EventType.implicit_home_event.name
                    || action == EventType.implicit_on_activity_newIntent.name
                    || action == EventType.implicit_on_activity_result.name
                    || action == EventType.dialog_dismiss.name
                    || action == EventType.dialog_cancel.name
                    || action == EventType.dialog_press_key.name
                    || action == EventType.press_key.name)
        }

        fun isIgnoreEvent(action: String): Boolean {
            return (action == EventType.implicit_on_activity_result.name
                    || action == EventType.implicit_on_activity_newIntent.name
                    || action == EventType.implicit_power_event.name
                    || action == EventType.press_key.name
                    || action == EventType.dialog_press_key.name
                    || action == EventType.dialog_dismiss.name
                    || action == EventType.dialog_cancel.name)
        }

        fun getEventTypeFromActionName(actionName: String): EventType{
            return when (actionName)
            {
                "Click" -> EventType.click
                "LongClick" -> EventType.long_click
                "Swipe" -> EventType.scroll
                "TextInput" -> EventType.enter_text
                "PressMenu" -> EventType.implicit_menu
                "PressBack" -> EventType.press_back
                "MinimizeMaximize" -> EventType.implicit_home_event
                "RotateUI" -> EventType.implicit_rotate_event
                "CallIntent" -> EventType.callIntent
                else -> EventType.fake_action
            }
        }
    }
}

class FakeEvent(): StaticEvent(EventType.fake_action, arrayListOf(),null,"")

