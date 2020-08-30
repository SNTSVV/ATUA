package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticWidget
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

open class AbstractState (
        val activity: String,
        val widgets: ArrayList<WidgetGroup> = ArrayList(),
        val guiStates: ArrayList<State<*>> = ArrayList(),
        var window: WTGNode,
        val staticWidgetMapping: HashMap<WidgetGroup, ArrayList<StaticWidget>> = HashMap(),
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
                get() {return widgets.filter { it.exerciseCount==0}.size}
        val actionCount = HashMap<AbstractAction, Int>()
        val targetActions = HashSet<AbstractAction> ()

        init {
            val pressBackAction = AbstractAction(
                    actionName = AbstractActionType.PRESS_BACK.actionName
            )
            actionCount.put(pressBackAction,0)
            if (window !is WTGOptionsMenuNode && window !is WTGDialogNode ) {
                val pressMenuAction = AbstractAction(
                        actionName = AbstractActionType.PRESS_MENU.actionName
                )
                actionCount.put(pressMenuAction, 0)
                val minmaxAction = AbstractAction(
                        actionName = AbstractActionType.MINIMIZE_MAXIMIZE.actionName
                )
                actionCount.put(minmaxAction,0)

            }
            if (window is WTGActivityNode) {
                val swipeUpAction = AbstractAction(
                        actionName = AbstractActionType.SWIPE.actionName,
                        extra = "SwipeUp"
                )
                val swipeDownAction = AbstractAction(
                        actionName = AbstractActionType.SWIPE.actionName,
                        extra = "SwipeDown"
                )
                val swipeLeftAction = AbstractAction(
                        actionName = AbstractActionType.SWIPE.actionName,
                        extra = "SwipeLeft"
                )
                val swipeRightAction = AbstractAction(
                        actionName = AbstractActionType.SWIPE.actionName,
                        extra = "SwipeRight"
                )

                actionCount.put(swipeUpAction, 0)
                actionCount.put(swipeDownAction, 0)
                actionCount.put(swipeLeftAction, 0)
                actionCount.put(swipeRightAction, 0)


            }
            if (window is WTGActivityNode || rotation == Rotation.LANDSCAPE) {
                val rotationAction = AbstractAction(
                        actionName = AbstractActionType.ROTATE_UI.actionName
                )
                actionCount.put(rotationAction, 0)
            }
            if (isOpeningKeyboard) {
                val closeKeyboardAction = AbstractAction(
                        actionName = AbstractActionType.CLOSE_KEYBOARD.actionName
                )
                actionCount.put(closeKeyboardAction, 0)
            }
            if (!AbstractStateManager.instance.widgetGroupFrequency.containsKey(window)) {
                AbstractStateManager.instance.widgetGroupFrequency.put(window, HashMap())
            }
            val widgetGroupFrequency = AbstractStateManager.instance.widgetGroupFrequency[window]!!
            widgets.forEach {
                if (!widgetGroupFrequency.containsKey(it)) {
                    widgetGroupFrequency.put(it,1)
                } else {
                    widgetGroupFrequency[it] =  widgetGroupFrequency[it]!!+1
                }
            }
        }

        fun addWidgetGroup (widgetGroup: WidgetGroup) {
            if (widgets.contains(widgetGroup)) {
                return
            }
            widgets.add(widgetGroup)

        }

        fun addAction(action: AbstractAction) {
            if (action.widgetGroup == null) {
                if (!actionCount.containsKey(action)) {
                    actionCount[action] = 0
                }
                return
            }
            if (!action.widgetGroup.actionCount.containsKey(action)) {
                action.widgetGroup.actionCount[action] = 0
            }
        }
        fun getWidgetGroup(widget: Widget, guiState: State<*>): WidgetGroup?{
            val tempAttributePath = HashMap<Widget,AttributePath>()
            val tempChildWidgetAttributePaths = HashMap<Widget,AttributePath>()
            return widgets.find {
                       val reducedAttributePath = AbstractionFunction.INSTANCE.reduce(widget,guiState,window.activityClass,tempAttributePath,tempChildWidgetAttributePaths)
                        it.attributePath.equals(reducedAttributePath)
                }
        }

        fun getAvailableActions(): List<AbstractAction> {
            val allActions = ArrayList<AbstractAction>()
            allActions.addAll(actionCount.keys)
            allActions.addAll(widgets.map { it.actionCount.keys }.flatten())
            return allActions
        }

        fun getUnExercisedActions(currentState: State<*>?): List<AbstractAction> {
            val unexcerisedActions = HashSet<AbstractAction>()
            unexcerisedActions.addAll(actionCount.filter {
                it.value == 0
                    && it.key.actionName!="fake_action"
                    && it.key.actionName!="LaunchApp"
                    && it.key.actionName!="ResetApp" }.map { it.key } )
            unexcerisedActions.addAll(widgets.map {
                w -> w.actionCount
                    .filterNot { it.key.isCheckableOrTextInput() }
                    .filter { it.value==0
                    || (it.value < w.count/2+1 && w.cardinality == Cardinality.MANY)
                    }}.map { it.keys }.flatten())
            val itemActions = widgets.map { w -> w.actionCount.filter { it.key.isItemAction() } }.map { it.keys }.flatten()
            if (currentState!=null || guiStates.isNotEmpty()) {
                val state = if(currentState == null) {
                    currentState?:guiStates.random()
                } else
                {
                    currentState
                }

                itemActions.forEach {
                    val widgetGroup = it.widgetGroup!!
                    val runtimeWidgets = widgetGroup.getGUIWidgets(state)
                    var childSize = 0
                    runtimeWidgets.forEach { w ->
                        val childWidgets = Helper.getAllInteractiveChild(state.widgets, w)
                        childSize += childWidgets.size
                    }
                    val numberOfTry = childSize / 2 + 1
                    if (widgetGroup.actionCount[it]!! < numberOfTry) {
                        unexcerisedActions.add(it)
                    }
                }
            }
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
        if (action.widgetGroup == null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = count
            }
            return
        }
        if (action.widgetGroup.actionCount.containsKey(action)) {
            action.widgetGroup.actionCount[action] = count
        }
    }

    fun increaseActionCount(action: AbstractAction) {
        if (action.widgetGroup==null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = actionCount[action]!! + 1
            } else {
                actionCount[action] = 1
            }
            val nonDataAction = AbstractAction(
                    actionName = action.actionName
            )
            if (actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                actionCount[nonDataAction] = actionCount[nonDataAction]!! + 1
            }
        } else if (widgets.contains(action.widgetGroup)){
            val widgetGroup = widgets.find { it.equals(action.widgetGroup) }!!
            if (widgetGroup.actionCount.containsKey(action)) {
                widgetGroup.actionCount[action] = widgetGroup.actionCount[action]!! + 1
            } else {
                widgetGroup.actionCount[action] = 1
            }
            val nonDataAction = AbstractAction(
                    actionName = action.actionName,
                    widgetGroup = widgetGroup
            )
            if (widgetGroup.actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                widgetGroup.actionCount[nonDataAction] = widgetGroup.actionCount[nonDataAction]!! + 1
            }
            if (action.actionName == "Click" || action.actionName == "Click") {
                val itemAction = when(action.actionName) {
                    "Click" -> "ItemClick"
                    "LongClick" -> "ItemLongClick"
                    else -> "ItemClick"
                }
                var currentAttributePath: AttributePath? = action.widgetGroup!!.attributePath
                while (currentAttributePath!=null) {
                    val parentWidgetGroup = widgets.find { it.attributePath == currentAttributePath!!.parentAttributePath }
                    if (parentWidgetGroup != null) {
                        val parentItemAction = parentWidgetGroup.actionCount.entries.find { it.key.actionName == itemAction }
                        if (parentItemAction!=null) {
                            increaseActionCount(parentItemAction.key)
                        }
                        currentAttributePath = parentWidgetGroup.attributePath
                    } else {
                        currentAttributePath = currentAttributePath.parentAttributePath
                    }
                }
            }
        }
    }
    fun getActionCount(action: AbstractAction): Int {
        if (action.widgetGroup == null) {
            return actionCount[action]?:0
        }
        return action.widgetGroup.actionCount[action]?:0
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