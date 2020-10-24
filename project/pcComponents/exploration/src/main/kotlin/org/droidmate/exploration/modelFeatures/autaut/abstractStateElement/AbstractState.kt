package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticWidget
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

open class AbstractState (
        val activity: String,
        val attributeValuationSets: ArrayList<AttributeValuationSet> = ArrayList(),
        val guiStates: ArrayList<State<*>> = ArrayList(),
        var window: WTGNode,
        val staticWidgetMapping: HashMap<AttributeValuationSet, ArrayList<StaticWidget>> = HashMap(),
        val abstractInteractions: ArrayList<AbstractInteraction> = ArrayList(),
        val staticEventMapping: HashMap<AbstractAction, ArrayList<StaticEvent>> = HashMap(),
        val isHomeScreen: Boolean = false,
        val isOpeningKeyboard: Boolean = false,
        val isRequestRuntimePermissionDialogBox: Boolean = false,
        val isAppHasStoppedDialogBox: Boolean = false,
        val isOutOfApplication: Boolean = false,
        var hasOptionsMenu: Boolean = true,
        var rotation: Rotation,
        var internet: InternetStatus
) {
        val unexercisedWidgetCount: Int
                get() {return attributeValuationSets.filter { it.exerciseCount==0}.size}
        val actionCount = HashMap<AbstractAction, Int>()
        val targetActions = HashSet<AbstractAction> ()

        init {
            window.mappedStates.add(this)
            val pressBackAction = AbstractAction(
                    actionType = AbstractActionType.PRESS_BACK
            )
            actionCount.put(pressBackAction,0)
            if (window !is WTGOptionsMenuNode && window !is WTGDialogNode ) {
                val pressMenuAction = AbstractAction(
                        actionType = AbstractActionType.PRESS_MENU
                )
                actionCount.put(pressMenuAction, 0)
                val minmaxAction = AbstractAction(
                        actionType = AbstractActionType.MINIMIZE_MAXIMIZE
                )
                actionCount.put(minmaxAction,0)

            }
            if (window is WTGActivityNode) {
               /* val swipeUpAction = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        extra = "SwipeUp"
                )
                val swipeDownAction = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        extra = "SwipeDown"
                )
                val swipeLeftAction = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        extra = "SwipeLeft"
                )
                val swipeRightAction = AbstractAction(
                        actionType = AbstractActionType.SWIPE,
                        extra = "SwipeRight"
                )

                actionCount.put(swipeUpAction, 0)
                actionCount.put(swipeDownAction, 0)
                actionCount.put(swipeLeftAction, 0)
                actionCount.put(swipeRightAction, 0)*/


            }
            if (window is WTGActivityNode || rotation == Rotation.LANDSCAPE) {
                val rotationAction = AbstractAction(
                        actionType = AbstractActionType.ROTATE_UI
                )
                actionCount.put(rotationAction, 0)
            }

            if (window is WTGDialogNode) {
                val clickOutDialog = AbstractAction(
                        actionType = AbstractActionType.CLICK_OUTBOUND
                )
                actionCount.put(clickOutDialog,0)
            }
            if (isOpeningKeyboard) {
                val closeKeyboardAction = AbstractAction(
                        actionType = AbstractActionType.CLOSE_KEYBOARD
                )
                actionCount.put(closeKeyboardAction, 0)
            }
            if (!AbstractStateManager.instance.widgetGroupFrequency.containsKey(window)) {
                AbstractStateManager.instance.widgetGroupFrequency.put(window, HashMap())
            }
            val widgetGroupFrequency = AbstractStateManager.instance.widgetGroupFrequency[window]!!
            attributeValuationSets.forEach {
                if (!widgetGroupFrequency.containsKey(it)) {
                    widgetGroupFrequency.put(it,1)
                } else {
                    widgetGroupFrequency[it] =  widgetGroupFrequency[it]!!+1
                }
            }
        }

        fun addWidgetGroup (attributeValuationSet: AttributeValuationSet) {
            if (attributeValuationSets.contains(attributeValuationSet)) {
                return
            }
            attributeValuationSets.add(attributeValuationSet)
            val widgetGroupFrequency = AbstractStateManager.instance.widgetGroupFrequency[window]!!
            widgetGroupFrequency[attributeValuationSet] = 1

        }

        fun addAction(action: AbstractAction) {
            if (action.actionType == AbstractActionType.PRESS_HOME) {
                return
            }
            if (action.attributeValuationSet == null) {
                if (!actionCount.containsKey(action)) {
                    actionCount[action] = 0
                }
                return
            }
            if (!action.attributeValuationSet.actionCount.containsKey(action)) {
                action.attributeValuationSet.actionCount[action] = 0
            }
        }

        fun getWidgetGroup(widget: Widget, guiState: State<*>): AttributeValuationSet{
            val tempAttributePath = HashMap<Widget,AttributePath>()
            val tempChildWidgetAttributePaths = HashMap<Widget,AttributePath>()
            val reducedAttributePath = AbstractionFunction.INSTANCE.reduce(widget,guiState,window.activityClass,tempAttributePath,tempChildWidgetAttributePaths)
            return attributeValuationSets.find {
                it.attributePath.equals(reducedAttributePath)
            }?: AttributeValuationSet(reducedAttributePath,Cardinality.ONE)
        }

        fun getAvailableActions(): List<AbstractAction> {
            val allActions = ArrayList<AbstractAction>()
            allActions.addAll(actionCount.keys)
            allActions.addAll(attributeValuationSets.map { it.actionCount.keys }.flatten())
            //
            return allActions
        }

        fun getUnExercisedActions(currentState: State<*>?): List<AbstractAction> {
            val unexcerisedActions = HashSet<AbstractAction>()
            //use hashmap to optimize the performance of finding widget
            val widget_WidgetGroupMap = HashMap<Widget, AttributeValuationSet>()
            if (currentState != null) {
                Helper.getVisibleWidgetsForAbstraction(currentState, AbstractStateManager.instance.autautMF.packageName).forEach { w ->
                    val wg = this.getWidgetGroup(w,currentState)
                    widget_WidgetGroupMap.put(w,wg)
                }
            }
            unexcerisedActions.addAll(actionCount.filter {
                it.value == 0
                    && it.key.actionType!=AbstractActionType.FAKE_ACTION
                    && it.key.actionType!=AbstractActionType.LAUNCH_APP
                    && it.key.actionType!=AbstractActionType.RESET_APP
                        && it.key.actionType!=AbstractActionType.ENABLE_DATA
                        && it.key.actionType!=AbstractActionType.DISABLE_DATA}
                    .map { it.key } )
            val widgetActionCounts = if (currentState!=null) {
                widget_WidgetGroupMap.values.filter { !it.attributePath.isInputField()}
                        .map { w -> w.actionCount }
            } else {
                attributeValuationSets
                        .filter { !it.attributePath.isInputField()}. map { w -> w.actionCount }
            }
            widgetActionCounts.forEach {
                val actions = it.filter { it.value == 0 }.map { it.key }
                unexcerisedActions.addAll(actions)
               /* val exercised = it.filter { it.value > 0}
                exercised.forEach { action, count ->
                    if (action.widgetGroup!!.cardinality == Cardinality.MANY && action.actionName!="Swipe") {
                        val guiWidgetCounts = widget_WidgetGroupMap.filter { p -> p.value == action.widgetGroup }.size
                        if (count < guiWidgetCounts / 2 + 1) {
                            unexcerisedActions.add(action)
                        }
                    } *//*else {
                        val widgetFrequency = AbstractStateManager.instance.widgetGroupFrequency[this.window]!!
                        val widgetGroupCount = widgetFrequency[action.widgetGroup!!]
                        if (widgetGroupCount != null) {
                            if (count <= widgetGroupCount) {
                                unexcerisedActions.add(action)
                            }
                        }
                    }*//*
                    *//*if (currentState!=null && action.widgetGroup!!.attributePath.hasParentWithClassName("DrawerLayout")) {
                        val guiWidgetCounts = widget_WidgetGroupMap.filter {p-> p.value == action.widgetGroup } .size
                        if (count < guiWidgetCounts) {
                            unexcerisedActions.add(action)
                        }
                    } else {

                    }*//*
                }*/
                /*val exercised = it.filter { it.value > 0 && it.key.widgetGroup!!.cardinality== Cardinality.MANY }
                exercised.forEach {
                    if (currentState!=null) {
                        val guiWidgetCounts = widget_WidgetGroupMap.filter {p-> p.value == it.key.widgetGroup } .size
                        if (it.value < guiWidgetCounts/2+1) {
                            unexcerisedActions.add(it.key)
                        }
                    } else {
                        if (it.value < 4) {
                            unexcerisedActions.add(it.key)
                        }
                        *//*if (guiStates.isNotEmpty()) {
                            val guiState = guiStates.maxBy { it.actionableWidgets.size }!!
                            val guiWidgetCount = widget_WidgetGroupMap.filter {p-> p.value == it.key.widgetGroup }.size
                            if (it.value < guiWidgetCount/2+1) {
                                unexcerisedActions.add(it.key)
                            }
                        }
                        else *//*
                    }
                }*/

            }

          val itemActions = attributeValuationSets.map { w -> w.actionCount.filter { it.key.isItemAction() } }.map { it.keys }.flatten()
          /*  if (currentState!=null || guiStates.isNotEmpty()) {
                val state = if(currentState == null) {
                    currentState?:guiStates.maxBy { it.actionableWidgets.size }!!
                } else
                {
                    currentState
                }
                itemActions.forEach {
                    val widgetGroup = it.widgetGroup!!
                    val runtimeWidgets = widget_WidgetGroupMap.filter { it.value == widgetGroup }.keys
                    var childSize = 0
                    val numberOfTry: Int
                    if (widgetGroup.attributePath.getClassName().contains("WebView")) {
                        numberOfTry = 5
                    } else {
                        runtimeWidgets.forEach { w ->
                            val childWidgets = Helper.getAllInteractiveChild(state.widgets, w)
                            childSize += childWidgets.size
                        }
                        numberOfTry = childSize
                    }

                    if (widgetGroup.actionCount[it]!! < numberOfTry) {
                        unexcerisedActions.add(it)
                    }
                }
            }*/
  /*          if (this.window.activityClass == "org.wikipedia.main.MainActivity") {
                unexcerisedActions.addAll(widgets.map {
                    w -> w.actionCount.filter {
                    it.value < 4 && it.key.actionName == "Swipe" && it.key.extra == "SwipeUp"}}.map { it.keys }.flatten())
                }*/
            return unexcerisedActions.toList()
        }


    override fun toString(): String {
        return "AbstractState[${this.hashCode()}]-${window}-Rotation:$rotation"
    }
    fun setActionCount(action: AbstractAction, count: Int) {
        if (action.attributeValuationSet == null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = count
            }
            return
        }
        if (action.attributeValuationSet.actionCount.containsKey(action)) {
            action.attributeValuationSet.actionCount[action] = count
        }
    }

    fun increaseActionCount(action: AbstractAction, updateSimilarAbstractState: Boolean = false) {
        if (action.attributeValuationSet==null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = actionCount[action]!! + 1
            } else {
                actionCount[action] = 1
            }
            val nonDataAction = AbstractAction(
                    actionType = action.actionType
            )
            if (actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                actionCount[nonDataAction] = actionCount[nonDataAction]!! + 1
            }
        } else if (attributeValuationSets.contains(action.attributeValuationSet)){
            val widgetGroup = attributeValuationSets.find { it.equals(action.attributeValuationSet) }!!
            if (widgetGroup.actionCount.containsKey(action)) {
                widgetGroup.actionCount[action] = widgetGroup.actionCount[action]!! + 1
                widgetGroup.exerciseCount++
            } /*else {
                widgetGroup.actionCount[action] = 1
            }*/
            val nonDataAction = AbstractAction(
                    actionType = action.actionType,
                    attributeValuationSet = widgetGroup
            )
            if (widgetGroup.actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                widgetGroup.actionCount[nonDataAction] = widgetGroup.actionCount[nonDataAction]!! + 1
            }
        }
        if (updateSimilarAbstractState) {
            increaseSimilarActionCount(action)
        }
    }

    fun increaseSimilarActionCount (action: AbstractAction) {
        AbstractStateManager.instance.ABSTRACT_STATES
                .filterNot { it is VirtualAbstractState }
                .filter {it.window == this.window}
                .forEach {

                    if (it.getAvailableActions().contains(action)) {
                        it.increaseActionCount(action)
                    }
                }
    }
    fun getActionCount(action: AbstractAction): Int {
        if (action.attributeValuationSet == null) {
            return actionCount[action]?:0
        }
        return action.attributeValuationSet.actionCount[action]?:0
    }

    fun getActionCountMap(): Map<AbstractAction, Int> {
        val result = HashMap<AbstractAction,Int>()
        result.putAll(actionCount)
        attributeValuationSets.map { it.actionCount }.forEach {
            result.putAll(it)
        }
        return result
    }

    fun computeScore(autautMF: AutAutMF): Double {
        var localScore = 0.0
        val unexploredActions = getUnExercisedActions(null).filterNot { it.attributeValuationSet == null }
        val windowWidgetFrequency = AbstractStateManager.instance.widgetGroupFrequency[this.window]!!
        unexploredActions. forEach {
            val actionScore = it.getScore()
            if (windowWidgetFrequency.containsKey(it.attributeValuationSet!!))
                localScore += (actionScore/windowWidgetFrequency[it.attributeValuationSet!!]!!.toDouble())
            else
                localScore += actionScore
        }

        val actions = getAvailableActions()
        var potentialActionCount = 0.0
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
        return potentialActionCount+localScore
    }

    fun getLeastExerciseWidgets(currentState: State<*>): List<AttributeValuationSet> {
        val interactiveWidgetGroups = this.attributeValuationSets
                .filterNot { it.attributePath.isCheckable() || it.attributePath.isInputField() }
        if (interactiveWidgetGroups.isNotEmpty()) {
            val leastExerciseCount = interactiveWidgetGroups.minBy { it.exerciseCount }!!.exerciseCount
            val leastExerciseAttributeValuationSets = interactiveWidgetGroups.filter {
                it.exerciseCount == leastExerciseCount
                        && !it.attributePath.isInputField()
            }
            return leastExerciseAttributeValuationSets
        }
        return emptyList()
    }
}

enum class InternetStatus {
    Enable,
    Disable,
    Undefined
}

class VirtualAbstractState(activity: String,
                           staticNode: WTGNode,
                           isHomeScreen: Boolean=false): AbstractState(
        activity = activity,
        window = staticNode,
        rotation = Rotation.PORTRAIT,
        internet = InternetStatus.Undefined,
        isHomeScreen = isHomeScreen
        )

class AppResetAbstractState():AbstractState(activity = "", window = WTGLauncherNode.getOrCreateNode(),rotation = Rotation.PORTRAIT,internet = InternetStatus.Undefined)