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
                       val reducedAttributePath = AbstractionFunction.INSTANCE.reduce(widget,guiState,activity,tempAttributePath,tempChildWidgetAttributePaths)
                        it.attributePath.equals(reducedAttributePath)
                }
        }

        fun getAvailableActions(): List<AbstractAction> {
            val allActions = ArrayList<AbstractAction>()
            allActions.addAll(actionCount.keys)
            allActions.addAll(widgets.map { it.actionCount.keys }.flatten())
            return allActions
        }

        fun getUnExercisedActions(): List<AbstractAction> {
            val unexcerisedActions = HashSet<AbstractAction>()
            unexcerisedActions.addAll(actionCount.filter { it.value == 0
                    && it.key.actionName!="fake_action"
                    && it.key.actionName!="LaunchApp"
                    && it.key.actionName!="ResetApp"}.keys)
            unexcerisedActions.addAll(widgets.map { w -> w.actionCount.filter { it.value==0
                    || (it.value < 3 && w.cardinality == Cardinality.MANY)
                    || (it.value < 3 && it.key.actionName == "ItemClick")
                    || (it.value < 10 && it.key.widgetGroup!!.attributePath.getClassName()=="android.webkit.WebView")
            }}.map { it.keys }.flatten())
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