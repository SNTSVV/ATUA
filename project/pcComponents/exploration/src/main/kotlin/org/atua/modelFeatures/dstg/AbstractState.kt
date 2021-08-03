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

package org.atua.modelFeatures.dstg

import org.atua.modelFeatures.dstg.reducer.WidgetReducer
import org.atua.modelFeatures.ewtg.EWTGWidget
import org.atua.modelFeatures.ewtg.*
import org.atua.modelFeatures.ewtg.window.Activity
import org.atua.modelFeatures.ewtg.window.Dialog
import org.atua.modelFeatures.ewtg.window.Launcher
import org.atua.modelFeatures.ewtg.window.OutOfApp
import org.atua.modelFeatures.ewtg.window.Window
import org.atua.calm.modelReuse.ModelVersion
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.toUUID
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

open class AbstractState(
    val activity: String,
    val attributeValuationMaps: ArrayList<AttributeValuationMap> = arrayListOf(),
    val avmCardinalities: HashMap<AttributeValuationMap,Cardinality>,
    val guiStates: ArrayList<State<*>> = ArrayList(),
    var window: Window,
    val EWTGWidgetMapping: HashMap<AttributeValuationMap, EWTGWidget> = HashMap(),
    val abstractTransitions: HashSet<AbstractTransition> = HashSet(),
    val inputMappings: HashMap<AbstractAction, HashSet<Input>> = HashMap(),
    val isHomeScreen: Boolean = false,
    val isOpeningKeyboard: Boolean = false,
    val isRequestRuntimePermissionDialogBox: Boolean = false,
    val isAppHasStoppedDialogBox: Boolean = false,
    val isOutOfApplication: Boolean = false,
    var isOpeningMenus: Boolean = false,
    var rotation: org.atua.modelFeatures.Rotation,
    var loadedFromModel: Boolean = false,
    var modelVersion: ModelVersion = ModelVersion.RUNNING,
    reuseAbstractStateId: UUID? = null
) {
    val actionCount = HashMap<AbstractAction, Int>()
    val targetActions = HashSet<AbstractAction>()
    val abstractStateId: String
    var hashCode: Int = 0
    var isInitalState = false

    var hasOptionsMenu: Boolean = true
    var shouldNotCloseKeyboard: Boolean = false
    init {
        window.mappedStates.add(this)
        attributeValuationMaps.forEach {
            it.captured = true
        }

        countAVMFrequency()
        hashCode = computeAbstractStateHashCode(attributeValuationMaps,avmCardinalities, window, rotation)
        abstractStateIdByWindow.putIfAbsent(window,HashSet())
        if (reuseAbstractStateId!=null
                && abstractStateIdByWindow[window]!!.contains(reuseAbstractStateId)) {
            abstractStateId = reuseAbstractStateId.toString()
            abstractStateIdByWindow.get(window)!!.add(reuseAbstractStateId)
        } else {
            var id: Int = hashCode
            while (abstractStateIdByWindow[window]!!.contains(id.toUUID())) {
                id = id + 1
            }
            abstractStateId = "${id.toUUID()}"
            abstractStateIdByWindow.get(window)!!.add(id.toUUID())
        }
    }

    fun updateHashCode() {
        hashCode = computeAbstractStateHashCode(attributeValuationMaps,avmCardinalities, window, rotation)
    }

     fun countAVMFrequency() {
        if (!AbstractStateManager.INSTANCE.attrValSetsFrequency.containsKey(window)) {
            AbstractStateManager.INSTANCE.attrValSetsFrequency.put(window, HashMap())
        }
        val widgetGroupFrequency = AbstractStateManager.INSTANCE.attrValSetsFrequency[window]!!
        attributeValuationMaps.forEach {
            if (!widgetGroupFrequency.containsKey(it)) {
                widgetGroupFrequency.put(it, 1)
            } else {
                widgetGroupFrequency[it] = widgetGroupFrequency[it]!! + 1
            }
        }
    }

    fun isStructuralEqual(other: AbstractState): Boolean {
        return hashCode == other.hashCode
    }

    fun initAction(){
        val resetAction = AbstractAction(
                actionType = AbstractActionType.RESET_APP
        )
        actionCount.put(resetAction,0)
        if(!window.inputs.any { it.eventType == EventType.resetApp }) {
            val newInput = Input(
                    eventType = EventType.resetApp,
                    eventHandlers = HashSet(),
                    widget = null,
                    sourceWindow = window,
                    createdAtRuntime = true
            )
        }
        val launchAction = AbstractAction(
                actionType = AbstractActionType.LAUNCH_APP
        )
        actionCount.put(launchAction,0)
        if (!window.inputs.any { it.eventType == EventType.implicit_launch_event }) {
            val newInput = Input(
                    eventType = EventType.implicit_launch_event,
                    eventHandlers = HashSet(),
                    widget = null,
                    sourceWindow = window,
                    createdAtRuntime = true
            )

        }
        if (isOpeningKeyboard) {
            val closeKeyboardAction = AbstractAction(
                    actionType = AbstractActionType.CLOSE_KEYBOARD
            )
            actionCount.put(closeKeyboardAction, 0)

            if (!window.inputs.any { it.eventType == EventType.closeKeyboard }) {
                val newInput = Input(
                        eventType = EventType.closeKeyboard,
                        eventHandlers = HashSet(),
                        widget = null,
                        sourceWindow = window,
                        createdAtRuntime = true
                )

            }
        } else {
            val pressBackAction = AbstractAction(
                    actionType = AbstractActionType.PRESS_BACK
            )
            actionCount.put(pressBackAction, 0)
            if (!window.inputs.any { it.eventType == EventType.press_back }) {
                val newInput = Input(
                        eventType = EventType.press_back,
                        eventHandlers = HashSet(),
                        widget = null,
                        sourceWindow = window,
                        createdAtRuntime = true
                )

            }
            /*if (!this.isMenusOpened && this.hasOptionsMenu && this.window !is Dialog) {
                val pressMenuAction = AbstractAction(
                        actionType = AbstractActionType.PRESS_MENU
                )
                actionCount.put(pressMenuAction, 0)
            }*/
            if (!this.isOpeningMenus) {
                val minmaxAction = AbstractAction(
                        actionType = AbstractActionType.MINIMIZE_MAXIMIZE
                )
                actionCount.put(minmaxAction, 0)
                if (window is Activity || rotation == org.atua.modelFeatures.Rotation.LANDSCAPE) {
                    val rotationAction = AbstractAction(
                            actionType = AbstractActionType.ROTATE_UI
                    )
                    actionCount.put(rotationAction, 0)
                    if (!window.inputs.any { it.eventType == EventType.implicit_rotate_event }) {
                        val newInput = Input(
                                eventType = EventType.implicit_rotate_event,
                                eventHandlers = HashSet(),
                                widget = null,
                                sourceWindow = window,
                                createdAtRuntime = true
                        )

                    }

                }
            }
            if (window is Dialog) {
                val clickOutDialog = AbstractAction(
                        actionType = AbstractActionType.CLICK_OUTBOUND
                )
                actionCount.put(clickOutDialog, 0)
            }
        }


        attributeValuationMaps.forEach {
            //it.actionCount.clear()
            it.initActions()
        }
        EWTGWidgetMapping.forEach { avm, ewgtwidget ->
            avm.actionCount.keys.forEach { abstractAction->
                if (abstractAction.actionType==AbstractActionType.CLICK) {
                    if (!window.inputs.any { it.widget == ewgtwidget
                                    && it.eventType == EventType.click
                            }) {
                        val newInput = Input(
                                eventType = EventType.click,
                                widget = ewgtwidget,
                                eventHandlers = HashSet(),
                                createdAtRuntime = true,
                                sourceWindow = window
                        )

                    }
                }
                if (abstractAction.actionType==AbstractActionType.LONGCLICK) {
                    if (!window.inputs.any { it.widget == ewgtwidget
                                    && it.eventType == EventType.long_click
                            }) {
                        val newInput = Input(
                                eventType = EventType.long_click,
                                widget = ewgtwidget,
                                eventHandlers = HashSet(),
                                createdAtRuntime = true,
                                sourceWindow = window
                        )

                    }
                }
                if (abstractAction.actionType==AbstractActionType.ITEM_CLICK) {
                    if (!window.inputs.any { it.widget == ewgtwidget
                                    && it.eventType == EventType.item_click
                            }) {
                        val newInput = Input(
                                eventType = EventType.item_click,
                                widget = ewgtwidget,
                                eventHandlers = HashSet(),
                                createdAtRuntime = true,
                                sourceWindow = window
                        )

                    }
                }
                if (abstractAction.actionType==AbstractActionType.ITEM_LONGCLICK) {
                    if (!window.inputs.any { it.widget == ewgtwidget
                                    && it.eventType == EventType.item_long_click
                            }) {
                        val newInput = Input(
                                eventType = EventType.item_long_click,
                                widget = ewgtwidget,
                                eventHandlers = HashSet(),
                                createdAtRuntime = true,
                                sourceWindow = window
                        )

                    }
                }
                if (abstractAction.actionType==AbstractActionType.TEXT_INSERT) {
                    if (!window.inputs.any { it.widget == ewgtwidget
                                    && it.eventType == EventType.enter_text
                            }) {
                        val newInput = Input(
                                eventType = EventType.enter_text,
                                widget = ewgtwidget,
                                eventHandlers = HashSet(),
                                createdAtRuntime = true,
                                sourceWindow = window
                        )

                    }
                }
                if (abstractAction.actionType==AbstractActionType.SWIPE) {
                    if (!window.inputs.any { it.widget == ewgtwidget
                                    && it.eventType == EventType.swipe
                            }) {
                        val newInput = Input(
                                eventType = EventType.swipe,
                                widget = ewgtwidget,
                                eventHandlers = HashSet(),
                                createdAtRuntime = true,
                                sourceWindow = window
                        )

                    }
                }
            }
        }

    }

    fun addAction(action: AbstractAction) {
        if (action.actionType == AbstractActionType.PRESS_HOME) {
            return
        }
        if (action.attributeValuationMap == null) {
            if (action.actionType == AbstractActionType.CLICK)
                return
            if (!actionCount.containsKey(action)) {
                actionCount[action] = 0
            }
            return
        }
        if (!action.attributeValuationMap.actionCount.containsKey(action)) {
            action.attributeValuationMap.actionCount[action] = 0
        }
    }

    fun getAttributeValuationSet(widget: Widget, guiState: State<*>, atuaMF: org.atua.modelFeatures.ATUAMF): AttributeValuationMap? {
        if (!guiStates.contains(guiState))
            return null
        val mappedWidget_AttributeValuationSet = AttributeValuationMap.allWidgetAVMHashMap[window]
        if (mappedWidget_AttributeValuationSet == null)
            return null
        val mappedAttributeValuationSet = mappedWidget_AttributeValuationSet.get(widget)
        if (mappedAttributeValuationSet == null)
        {
            if (mappedWidget_AttributeValuationSet.any { it.key.uid == widget.uid }) {
                val guiTreeRectangle = Helper.computeGuiTreeDimension(guiState)
                var isOptionsMenu = if (!Helper.isDialog(this.rotation,guiTreeRectangle, guiState, atuaMF))
                    Helper.isOptionsMenuLayout(guiState)
                else
                    false
                val reducedAttributePath = WidgetReducer.reduce(widget,guiState,isOptionsMenu,guiTreeRectangle,window,rotation,atuaMF,HashMap(),HashMap())
                val ewtgWidget = WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.get(window)!!.get(widget)
                val attributeValuationSet = attributeValuationMaps.find {
                    it.haveTheSameAttributePath(reducedAttributePath)
                }
                return attributeValuationSet
            }
            if (Helper.hasParentWithType(widget, guiState, "WebView")) {
                val webViewWidget = Helper.tryGetParentHavingClassName(widget, guiState, "WebView")
                if (webViewWidget != null) {
                    return getAttributeValuationSet(webViewWidget, guiState, atuaMF)
                }
            }
            return null
        } else {

        }
        val attributeValuationSet = attributeValuationMaps.find {
            it.haveTheSameAttributePath(mappedAttributeValuationSet)
        }
        return attributeValuationSet
    }

    fun getAvailableActions(): List<AbstractAction> {
        val allActions = ArrayList<AbstractAction>()
        allActions.addAll(actionCount.keys)
        allActions.addAll(attributeValuationMaps.map { it.actionCount.keys }.flatten())
        //
        return allActions
    }

    fun getUnExercisedActions(currentState: State<*>?,atuaMF: org.atua.modelFeatures.ATUAMF): List<AbstractAction> {
        val unexcerisedActions = HashSet<AbstractAction>()
        //use hashmap to optimize the performance of finding widget
        val widget_WidgetGroupMap = HashMap<Widget, AttributeValuationMap>()
        if (currentState != null) {
            Helper.getVisibleWidgetsForAbstraction(currentState).forEach { w ->
                val wg = this.getAttributeValuationSet(w, currentState,atuaMF)
                if (wg != null)
                    widget_WidgetGroupMap.put(w, wg)
            }
        }
        unexcerisedActions.addAll(actionCount.filter {
            ( it.value == 0 )
                    && it.key.actionType != AbstractActionType.FAKE_ACTION
                    && it.key.actionType != AbstractActionType.LAUNCH_APP
                    && it.key.actionType != AbstractActionType.RESET_APP
                    && it.key.actionType != AbstractActionType.ENABLE_DATA
                    && it.key.actionType != AbstractActionType.DISABLE_DATA
                    && it.key.actionType != AbstractActionType.WAIT
                    && !it.key.isWidgetAction()
                    && it.key.actionType != AbstractActionType.CLICK
                    && it.key.actionType != AbstractActionType.LONGCLICK
                    && it.key.actionType != AbstractActionType.SEND_INTENT
                    && it.key.actionType != AbstractActionType.PRESS_MENU
        }.map { it.key })
        val widgetActionCounts = if (currentState != null) {
            widget_WidgetGroupMap.values.filter { !it.isUserLikeInput() }.distinct()
                    .map { w -> w.actionCount }
        } else {
            attributeValuationMaps
                    .filter { !it.isUserLikeInput() }.map { w -> w.actionCount }
        }
        widgetActionCounts.forEach {
            val actions = it.filterNot {
                it.key.attributeValuationMap!!.getClassName().contains("WebView")
                        && (
                        (it.key.actionType == AbstractActionType.ITEM_CLICK && hasClickableSubItems(it.key.attributeValuationMap!!, currentState))
                                || (it.key.actionType == AbstractActionType.ITEM_LONGCLICK && hasLongClickableSubItems(it.key.attributeValuationMap!!, currentState))
                        )
            }.filter { it.value == 0 || (avmCardinalities.get(it.key.attributeValuationMap)==Cardinality.MANY && it.value < 2) }.map { it.key }
            unexcerisedActions.addAll(actions)
        }

        val itemActions = attributeValuationMaps.map { w -> w.actionCount.filter { it.key.isItemAction() } }.map { it.keys }.flatten()

        return unexcerisedActions.toList()
    }

    private fun hasLongClickableSubItems(attributeValuationMap: AttributeValuationMap, currentState: State<*>?): Boolean {
        if (currentState == null)
            return false
        val guiWidgets = attributeValuationMap.getGUIWidgets(currentState)
        if (guiWidgets.isNotEmpty()) {
            guiWidgets.forEach {
                if (Helper.haveLongClickableChild(currentState.visibleTargets, it))
                    return true
            }
        }
        return false
    }

    private fun hasClickableSubItems(attributeValuationMap: AttributeValuationMap, currentState: State<*>?): Boolean {
        if (currentState == null)
            return false
        val guiWidgets = attributeValuationMap.getGUIWidgets(currentState)
        if (guiWidgets.isNotEmpty()) {
            guiWidgets.forEach {
                if (Helper.haveClickableChild(currentState.visibleTargets, it))
                    return true
            }
        }
        return false
    }


    override fun toString(): String {
        return "AbstractState[${this.abstractStateId}]-${window}"
    }


    fun setActionCount(action: AbstractAction, count: Int) {
        if (action.attributeValuationMap == null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = count
            }
            return
        }
        if (action.attributeValuationMap.actionCount.containsKey(action)) {
            action.attributeValuationMap.actionCount[action] = count
        }
    }
    fun increaseActionCount2(abstractAction: AbstractAction, updateSimilarAbstractStates: Boolean) {
        this.increaseActionCount(abstractAction)
        if (updateSimilarAbstractStates) {
            if (!abstractAction.isWidgetAction()) {
                AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
                    it != this
                            && it.window == this.window
                }.forEach {
                    it.increaseActionCount(abstractAction)
                }
            }
        }
    }

    private fun increaseActionCount(action: AbstractAction) {
        if (action.attributeValuationMap == null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = actionCount[action]!! + 1
            } else {
                if (validateActionType(action.actionType,false))
                    actionCount[action] = 1
                else {
                    val a = 1
                }
            }
            val nonDataAction = AbstractAction(
                    actionType = action.actionType
            )
            if (actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                actionCount[nonDataAction] = actionCount[nonDataAction]!! + 1
            }

        } else if (attributeValuationMaps.contains(action.attributeValuationMap)) {
            val widgetGroup = attributeValuationMaps.find { it.equals(action.attributeValuationMap) }!!
            if (widgetGroup.actionCount.containsKey(action)) {
                widgetGroup.actionCount[action] = widgetGroup.actionCount[action]!! + 1
                widgetGroup.exerciseCount++
            } /*else {
                widgetGroup.actionCount[action] = 1
            }*/
            val nonDataAction = AbstractAction(
                    actionType = action.actionType,
                    attributeValuationMap = widgetGroup
            )
            if (widgetGroup.actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                widgetGroup.actionCount[nonDataAction] = widgetGroup.actionCount[nonDataAction]!! + 1
            }
        }
        if (action.isWidgetAction()) {
            val virtualAbstractState = AbstractStateManager.INSTANCE.ABSTRACT_STATES
                    .find { it.window == this.window && it is VirtualAbstractState }
            if (virtualAbstractState!=null) {
                if (!virtualAbstractState.attributeValuationMaps.contains(action.attributeValuationMap!!)) {
                    virtualAbstractState.attributeValuationMaps.add(action.attributeValuationMap!!)
                    virtualAbstractState.addAction(action)
                }
            }
        }
      /*  if (updateSimilarAbstractState && !action.isWidgetAction()) {
            increaseSimilarActionCount(action)
        }*/
    }

    fun increaseSimilarActionCount(action: AbstractAction) {
        AbstractStateManager.INSTANCE.ABSTRACT_STATES
                .filter { it.window == this.window && it!=this}
                .forEach {
                    if (it.getAvailableActions().contains(action)) {
                        it.increaseActionCount(action)
                    }
                }
    }

    fun getActionCount(action: AbstractAction): Int {
        if (action.attributeValuationMap == null) {
            return actionCount[action] ?: 0
        }
        return action.attributeValuationMap.actionCount[action] ?: 0
    }

    fun getActionCountMap(): Map<AbstractAction, Int> {
        val result = HashMap<AbstractAction, Int>()
        result.putAll(actionCount)
        attributeValuationMaps.map { it.actionCount }.forEach {
            result.putAll(it)
        }
        return result
    }

    fun isRequireRandomExploration():Boolean {
        if ((this.window is Dialog
                        && !WindowManager.instance.updatedModelWindows.filter { it is OutOfApp }.map { it.classType }.contains(this.activity))
                || this.isOpeningMenus
                || this.window.classType.contains("ResolverActivity"))
            return true
        return false
    }
    fun computeScore(autautMF: org.atua.modelFeatures.ATUAMF): Double {
        var localScore = 0.0
        val unexploredActions = getUnExercisedActions(null,autautMF).filterNot { it.attributeValuationMap == null }
        val windowWidgetFrequency = AbstractStateManager.INSTANCE.attrValSetsFrequency[this.window]!!
        unexploredActions.forEach {
            val actionScore = it.getScore()
            if (windowWidgetFrequency.containsKey(it.attributeValuationMap!!))
                localScore += (actionScore / windowWidgetFrequency[it.attributeValuationMap!!]!!.toDouble())
            else
                localScore += actionScore
        }
        localScore += this.getActionCountMap().map { it.value }.sum()
        this.guiStates.forEach {
            localScore += autautMF.actionCount.getUnexploredWidget(it).size
        }
        /* actions.forEach {action ->
             autautMF.abstractTransitionGraph.edges(this).filter { edge->
                     edge.label.abstractAction == action
                             && edge.source != edge.destination
                             && edge.destination?.data !is VirtualAbstractState
                 }.forEach { edge ->
                     val dest = edge.destination?.data
                     if (dest != null) {
                         val widgetGroupFrequences = AbstractStateManager.instance.widgetGroupFrequency[dest.window]!!
                         val potentialActions = dest.getUnExercisedActions(null).filterNot { it.widgetGroup == null }
                         potentialActions.forEach {
                             if (widgetGroupFrequences.containsKey(it.widgetGroup))
                                 potentialActionCount += (1/widgetGroupFrequences[it.widgetGroup]!!.toDouble())
                             else
                                 potentialActionCount += 1
                         }
                         potentialActionCount += potentialActions.size
                     }
                 }
         }*/
        return  localScore
    }

    /**
     * write csv
     * uuid -> AbstractState_[uuid]
     */
    open fun dump(parentDirectory: Path) {
        val dumpedAttributeValuationSet = ArrayList<String>()
        File(parentDirectory.resolve("AbstractState_" + abstractStateId.toString() + ".csv").toUri()).bufferedWriter().use { all ->
            val header = header()
            all.write(header)
            attributeValuationMaps.forEach {
                if (!dumpedAttributeValuationSet.contains(it.avmId)) {
                    all.newLine()
                    it.dump(all, dumpedAttributeValuationSet,this)
                }
            }
        }
    }

    fun header(): String {
        return "AttributeValuationSetID;parentAVMID;${localAttributesHeader()};cardinality;captured;wtgWidgetMapping;hashcode"
    }

    private fun localAttributesHeader(): String {
        var result = ""
        AttributeType.values().toSortedSet().forEach {
            result+=it.toString()
            result+=";"
        }
        result = result.substring(0,result.length-1)
        return result
    }

    fun belongToAUT(): Boolean {
        return !this.isHomeScreen && !this.isOutOfApplication && !this.isRequestRuntimePermissionDialogBox && !this.isAppHasStoppedDialogBox
    }

    fun validateInput(input: Input):Boolean {
        val actionType = input.convertToExplorationActionName()
        return validateActionType(actionType, input.widget != null)
    }

    private fun validateActionType(actionType: AbstractActionType, isWigetAction: Boolean): Boolean {
        if (actionType == AbstractActionType.FAKE_ACTION) {
            return false
        }
        if (!isWigetAction) {
            if (actionType == AbstractActionType.CLOSE_KEYBOARD) {
                return this.isOpeningKeyboard
            }
            if (actionType == AbstractActionType.RANDOM_KEYBOARD) {
                return this.isOpeningKeyboard
            }
        }
        return true
    }

    companion object {
        val abstractStateIdByWindow = HashMap<Window, HashSet<UUID>>()
        fun computeAbstractStateHashCode(attributeValuationMaps: List<AttributeValuationMap>, avmCardinality: Map<AttributeValuationMap, Cardinality>, window: Window, rotation: org.atua.modelFeatures.Rotation): Int {
            return attributeValuationMaps.sortedBy { it.hashCode }.fold(emptyUUID) { id, avs ->
                /*// e.g. keyboard elements are ignored for uid computation within [addRelevantId]
                // however different selectable auto-completion proposes are only 'rendered'
                // such that we have to include the img id (part of configId) to ensure different state configuration id's if these are different*/

                //ConcreteId(addRelevantId(id, widget), configId + widget.uid + widget.id.configId)
                id + avs.hashCode.toUUID() + avmCardinality.get(avs)!!.ordinal.toUUID()
            }.plus(listOf<String>(rotation.toString(),window.classType).joinToString("<;>").toUUID()).hashCode()
        }

        internal operator fun UUID.plus(uuid: UUID?): UUID {
            return if (uuid == null) this
            else UUID(this.mostSignificantBits + uuid.mostSignificantBits, this.leastSignificantBits + uuid.mostSignificantBits)
        }

    }
}

enum class InternetStatus {
    Enable,
    Disable,
    Undefined
}

class VirtualAbstractState(activity: String,
                           staticNode: Window,
                           isHomeScreen: Boolean = false) : AbstractState(
        activity = activity,
        window = staticNode,
        avmCardinalities = HashMap(),
        rotation = org.atua.modelFeatures.Rotation.PORTRAIT,
        isHomeScreen = isHomeScreen,
        isOutOfApplication = (staticNode is OutOfApp),
        isOpeningMenus = false
)

class LauncherAbstractState() : AbstractState(activity = "", window = Launcher.getOrCreateNode(), rotation = org.atua.modelFeatures.Rotation.PORTRAIT,avmCardinalities = HashMap())