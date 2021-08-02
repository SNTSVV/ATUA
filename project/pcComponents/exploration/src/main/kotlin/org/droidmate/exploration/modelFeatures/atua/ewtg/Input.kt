/*
 * ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
 * Copyright (C) 2019 - 2021 University of Luxembourg
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.droidmate.exploration.modelFeatures.atua.ewtg

import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.Widget
import java.lang.Exception


open class Input{
    val eventType: EventType
    val eventHandlers: HashSet<String> = HashSet()
    var widget: EWTGWidget?
    var sourceWindow: Window
    set(value) {
        if (field != null)
            field.inputs.remove(this)
        field = value
        value.inputs.add(this)
    }
    val createdAtRuntime: Boolean
    val verifiedEventHandlers = HashSet<String>() //if an event handler appears in this set, we will not remove it from event's handlers
    val modifiedMethods = HashMap<String,Boolean>() //method id,
    val modifiedMethodStatement = HashMap<String, Boolean>() //statement id,
    val coverage = HashMap<String,Int>()

    var data: Any? = null
    var exerciseCount: Int = 0
    constructor(eventType: EventType, eventHandlers: Set<String>, widget: EWTGWidget?,sourceWindow: Window,createdAtRuntime: Boolean=false) {
       this.eventType = eventType
       this.eventHandlers.addAll(eventHandlers)
       this.widget = widget
       this.sourceWindow = sourceWindow
        this.createdAtRuntime = createdAtRuntime
        allInputs.add(this)
        sourceWindow.inputs.add(this)
        val exisingInput = allInputs.find { it!= this
                && it.eventType == this.eventType
                && it.sourceWindow == this.sourceWindow
                && it.widget == this.widget}
        if (exisingInput != null) {
            throw Exception("Duplicated Input created")
        }
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
            EventType.implicit_lifecycle_event -> AbstractActionType.MINIMIZE_MAXIMIZE
            EventType.scroll -> AbstractActionType.SWIPE
            EventType.swipe -> AbstractActionType.SWIPE
            EventType.closeKeyboard -> AbstractActionType.CLOSE_KEYBOARD
            EventType.implicit_launch_event -> AbstractActionType.LAUNCH_APP
            EventType.resetApp -> AbstractActionType.RESET_APP
            EventType.press_menu -> AbstractActionType.PRESS_MENU
            else -> if (widget != null) {
                AbstractActionType.CLICK
            } else {
                AbstractActionType.FAKE_ACTION
            }
        }
    }


    override fun toString(): String {
        return "$sourceWindow-->$eventType-->[$widget]"
    }
    companion object{
        val allInputs = arrayListOf<Input>()
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
//                    || action == EventType.implicit_lifecycle_event.name
//                    || action == EventType.implicit_rotate_event.name
//                    || action == EventType.implicit_back_event.name
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
                AbstractActionType.MINIMIZE_MAXIMIZE -> EventType.implicit_lifecycle_event
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

        fun getOrCreateInput(eventHandlers: Set<String>,
                             eventTypeString: String,
                             widget: EWTGWidget?,
                             sourceWindow: Window,
                             createdAtRuntime: Boolean=false): Input {
            var event = allInputs.firstOrNull { it.eventType.equals(EventType.valueOf(eventTypeString)) && it.widget == widget && it.sourceWindow == sourceWindow }
            //var event = allTargetStaticEvents.firstOrNull {it.eventTypeString.equals(eventTypeString) && (it.widget!!.equals(widget)) }
            if (event != null) {
                return event
            }
            event = Input(eventHandlers = HashSet(eventHandlers)
                    , eventType = EventType.valueOf(eventTypeString)
                    , widget = widget, sourceWindow = sourceWindow, createdAtRuntime = createdAtRuntime)
            return event
        }

        fun createInputFromAbstractInteraction(prevAbstractState: AbstractState, newAbstractState: AbstractState, abstractTransition: AbstractTransition, interaction: Interaction<Widget>?, wtg: EWTG) {
            val eventType = Input.getEventTypeFromActionName(abstractTransition.abstractAction.actionType)
            if (eventType == EventType.fake_action || eventType == EventType.resetApp || eventType == EventType.implicit_launch_event)
                return
            if (interaction != null && interaction.targetWidget != null && interaction.targetWidget!!.isKeyboard)
                return
            var newInput: Input?
            if (abstractTransition.abstractAction.attributeValuationMap == null) {
                newInput = Input.getOrCreateInput(
                        eventHandlers = emptySet(),
                        eventTypeString = eventType.toString(),
                        widget = null,
                        sourceWindow = prevAbstractState.window,
                        createdAtRuntime = true
                )
                /*newInput = Input(
                        eventType = eventType,
                        widget = null,
                        sourceWindow = prevAbstractState.window,
                        eventHandlers = HashSet(),
                        createdAtRuntime = true
                )*/
                newInput.data = abstractTransition.abstractAction.extra
                newInput.eventHandlers.addAll(abstractTransition.handlers.map { it.key })

                val prevWindows = abstractTransition.dependentAbstractStates.map { it.window }

                if(prevWindows.isNotEmpty()) {
                    prevWindows.forEach { prevWindow->
                        wtg.add(prevAbstractState.window, newAbstractState.window, WindowTransition(
                                prevAbstractState.window,
                                newAbstractState.window,
                                newInput!!,
                                prevWindow
                        ))
                    }
                } else {
                    wtg.add(prevAbstractState.window, newAbstractState.window, WindowTransition(
                            prevAbstractState.window,
                            newAbstractState.window,
                            newInput!!,
                            null
                    ))
                }
                if (!prevAbstractState.inputMappings.containsKey(abstractTransition.abstractAction)) {
                    prevAbstractState.inputMappings.put(abstractTransition.abstractAction, hashSetOf())
                }
                prevAbstractState.inputMappings.get(abstractTransition.abstractAction)!!.add(newInput)
                AbstractStateManager.INSTANCE.ABSTRACT_STATES.filterNot { it == prevAbstractState }.filter { it.window == prevAbstractState.window }.forEach {
                    val similarAbstractAction = it.getAvailableActions().find { it == abstractTransition.abstractAction }
                    if (similarAbstractAction != null) {
                        it.inputMappings.put(similarAbstractAction, hashSetOf(newInput!!))
                    }
                }
            } else {
                val attributeValuationSet = abstractTransition.abstractAction.attributeValuationMap
                if (!prevAbstractState.EWTGWidgetMapping.containsKey(attributeValuationSet)) {
                    val attributeValuationSetId = if (attributeValuationSet.getResourceId().isBlank())
                        ""
                    else
                        attributeValuationSet.avmId
                    // create new static widget and add to the abstract state
                    val staticWidget = EWTGWidget(
                            widgetId = attributeValuationSet.avmId.toString(),
                            resourceIdName = attributeValuationSet.getResourceId(),
                            window = prevAbstractState.window,
                            className = attributeValuationSet.getClassName(),
                            text = attributeValuationSet.getText(),
                            contentDesc = attributeValuationSet.getContentDesc(),
                            createdAtRuntime = true,
                            structure = attributeValuationSetId
                    )
                    prevAbstractState.EWTGWidgetMapping.put(attributeValuationSet, staticWidget)
                    AbstractStateManager.INSTANCE.ABSTRACT_STATES.filterNot { it == prevAbstractState }.filter { it.window == prevAbstractState.window }.forEach {
                        val similarWidget = it.attributeValuationMaps.find { it == attributeValuationSet }
                        if (similarWidget != null) {
                            it.EWTGWidgetMapping.put(similarWidget, staticWidget)
                        }
                    }
                }
                if (prevAbstractState.EWTGWidgetMapping.contains(attributeValuationSet)) {
                    val staticWidget = prevAbstractState.EWTGWidgetMapping[attributeValuationSet]!!
                    newInput = Input.getOrCreateInput(
                            eventHandlers = emptySet(),
                            eventTypeString = eventType.toString(),
                            widget = staticWidget,
                            sourceWindow = prevAbstractState.window,
                            createdAtRuntime = true
                    )
                    /*newInput = Input(
                            eventType = eventType,
                            widget = staticWidget,
                            sourceWindow = prevAbstractState.window,
                            eventHandlers = HashSet(),
                            createdAtRuntime = true
                    )*/
                    newInput.data = abstractTransition.abstractAction.extra
                    newInput.eventHandlers.addAll(abstractTransition.handlers.map { it.key })

                    val prevWindows = abstractTransition.dependentAbstractStates.map { it.window }

                    if(prevWindows.isNotEmpty()) {
                        prevWindows.forEach { prevWindow->
                            wtg.add(prevAbstractState.window, newAbstractState.window, WindowTransition(
                                    prevAbstractState.window,
                                    newAbstractState.window,
                                    newInput!!,
                                    prevWindow
                            ))
                        }
                    } else {
                        wtg.add(prevAbstractState.window, newAbstractState.window, WindowTransition(
                                prevAbstractState.window,
                                newAbstractState.window,
                                newInput!!,
                                null
                        ))
                    }
                    if (!prevAbstractState.inputMappings.containsKey(abstractTransition.abstractAction)) {
                        prevAbstractState.inputMappings.put(abstractTransition.abstractAction, hashSetOf())
                    }
                    prevAbstractState.inputMappings.get(abstractTransition.abstractAction)!!.add(newInput)
                    AbstractStateManager.INSTANCE.ABSTRACT_STATES.filterNot { it == prevAbstractState }.filter { it.window == prevAbstractState.window }.forEach {
                        val similarAbstractAction = it.getAvailableActions().find { it == abstractTransition.abstractAction }
                        if (similarAbstractAction != null) {
                            it.inputMappings.put(similarAbstractAction, hashSetOf(newInput))
                        }
                    }
                }
            }
        }
    }
}

class LaunchAppEvent(launcher: Window): Input(EventType.implicit_launch_event, HashSet(),null,sourceWindow = launcher)
class FakeEvent(sourceWindow: Window): Input(EventType.fake_action, HashSet(), null,sourceWindow)

