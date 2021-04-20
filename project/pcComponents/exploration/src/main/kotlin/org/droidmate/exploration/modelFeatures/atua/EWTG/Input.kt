package org.droidmate.exploration.modelFeatures.atua.EWTG

import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window


open class Input (
        val eventType: EventType,
        val eventHandlers: HashSet<String>,
        var widget: EWTGWidget?,
        var sourceWindow: Window,
        val createdAtRuntime: Boolean = false
)
{
    val modifiedMethods = HashMap<String,Boolean>() //method id,
    val modifiedMethodStatement = HashMap<String, Boolean>() //statement id,
    val verifiedEventHandlers = HashSet<String>() //if an event handler appears in this set, we will not remove it from event's handlers
    val coverage = HashMap<String,Int>()

    var data: Any? = null
    var exerciseCount: Int = 0


    init {
        allStaticEvents.add(this)
        sourceWindow.inputs.add(this)
    }

    fun convertToExplorationActionName(): AbstractActionType{
        return when (eventType){
            EventType.click, EventType.touch -> AbstractActionType.CLICK
            EventType.select -> AbstractActionType.CLICK
            EventType.drag -> AbstractActionType.SWIPE
            EventType.long_click -> AbstractActionType.LONGCLICK
            EventType.item_click -> AbstractActionType.ITEM_CLICK
            EventType.item_long_click -> AbstractActionType.ITEM_LONGCLICK
            EventType.item_selected -> AbstractActionType.ITEM_SELECTED
            EventType.enter_text -> AbstractActionType.TEXT_INSERT
            EventType.editor_action -> AbstractActionType.CLICK
            EventType.implicit_menu -> AbstractActionType.PRESS_MENU
            EventType.implicit_rotate_event -> AbstractActionType.ROTATE_UI
            EventType.implicit_back_event -> AbstractActionType.PRESS_BACK
            EventType.press_back -> AbstractActionType.PRESS_BACK
            EventType.callIntent -> AbstractActionType.SEND_INTENT
            EventType.implicit_home_event -> AbstractActionType.MINIMIZE_MAXIMIZE
            EventType.scroll -> AbstractActionType.SWIPE
            EventType.swipe -> AbstractActionType.SWIPE
            EventType.closeKeyboard -> AbstractActionType.CLOSE_KEYBOARD
            EventType.implicit_launch_event -> AbstractActionType.LAUNCH_APP
            EventType.resetApp -> AbstractActionType.RESET_APP
            else -> if (widget != null) {
                AbstractActionType.CLICK
            } else {
                AbstractActionType.FAKE_ACTION
            }
        }
    }
    fun fullyCovered(): Boolean {
        val uncoveredMethods = this.modifiedMethods.filter { it.value==false }.size
        if (uncoveredMethods>0)
            return false
        val uncoveredStatements = this.modifiedMethodStatement.filter { it.value==false }.size
        if (uncoveredStatements>0)
            return false
        return true
    }

    override fun toString(): String {
        return "$sourceWindow-->$eventType-->[$widget]"
    }
    companion object{
        val allStaticEvents = arrayListOf<Input>()
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
                    || action == EventType.press_key.name
                    || action == EventType.implicit_launch_event.name
                    || action == EventType.press_back.name
                    || action == EventType.press_menu.name)
        }

        fun isIgnoreEvent(action: String): Boolean {
            return (action == EventType.implicit_on_activity_result.name
                    || action == EventType.implicit_on_activity_newIntent.name
                    || action == EventType.implicit_power_event.name
                    || action == EventType.implicit_lifecycle_event.name
                    || action == EventType.implicit_rotate_event.name
                    || action == EventType.implicit_back_event.name
                    || action == EventType.implicit_home_event.name
                    || action == EventType.press_key.name
                    || action == EventType.dialog_press_key.name
                    || action == EventType.dialog_dismiss.name
                    || action == EventType.dialog_cancel.name)
        }

        fun getEventTypeFromActionName(actionName: AbstractActionType): EventType{
            return when (actionName)
            {
                AbstractActionType.CLICK -> EventType.click
                AbstractActionType.LONGCLICK-> EventType.long_click
                AbstractActionType.SWIPE -> EventType.swipe
                AbstractActionType.TEXT_INSERT -> EventType.enter_text
                AbstractActionType.PRESS_MENU -> EventType.implicit_menu
                AbstractActionType.PRESS_BACK -> EventType.press_back
                AbstractActionType.MINIMIZE_MAXIMIZE -> EventType.implicit_home_event
                AbstractActionType.ROTATE_UI -> EventType.implicit_rotate_event
                AbstractActionType.SEND_INTENT -> EventType.callIntent
                AbstractActionType.LAUNCH_APP -> EventType.implicit_launch_event
                AbstractActionType.CLOSE_KEYBOARD -> EventType.closeKeyboard
                AbstractActionType.RESET_APP -> EventType.resetApp
                AbstractActionType.ITEM_CLICK -> EventType.item_click
                AbstractActionType.ITEM_LONGCLICK -> EventType.item_long_click
                AbstractActionType.ITEM_SELECTED -> EventType.item_selected
                AbstractActionType.CLICK_OUTBOUND -> EventType.click
                else -> EventType.fake_action
            }
        }

        fun getOrCreateEvent(eventHandlers: Set<String>,
                             eventTypeString: String,
                             widget: EWTGWidget?,
                             @Suppress activity: String,
                             sourceWindow: Window): Input {
            var event = Input.allStaticEvents.firstOrNull { it.eventType.equals(EventType.valueOf(eventTypeString)) && it.widget == widget && it.sourceWindow == sourceWindow }
            //var event = allTargetStaticEvents.firstOrNull {it.eventTypeString.equals(eventTypeString) && (it.widget!!.equals(widget)) }
            if (event != null) {
                return event
            }
            event = Input(eventHandlers = HashSet(eventHandlers)
                    , eventType = EventType.valueOf(eventTypeString)
                    , widget = widget, sourceWindow = sourceWindow)
            return event
        }
    }
}

class LaunchAppEvent(launcher: Window): Input(EventType.implicit_launch_event, HashSet(),null,sourceWindow = launcher)
class FakeEvent(sourceWindow: Window): Input(EventType.fake_action, HashSet(), null,sourceWindow)

