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
        val staticEventMapping: HashMap<AbstractInteraction, StaticEvent> = HashMap(),
        var isFromLaunch: Boolean,
        val isHomeScreen: Boolean = false,
        val isOpeningKeyboard: Boolean = false,
        val isRequestRuntimePermissionDialogBox: Boolean = false,
        val isAppHasStoppedDialogBox: Boolean = false,
        val isOutOfApplication: Boolean = false,
        var hasOptionsMenu: Boolean = true,
        var rotation: Rotation

) {
        val unexercisedWidgetCount: Int
                get() {return widgets.filter { it.exerciseCount==0}.size}
        val actionCount = HashMap<AbstractAction, Int>()

        init {
            val pressBackAction = AbstractAction(
                    actionName = AbstractActionType.PRESS_BACK.actionName
            )
            actionCount.put(pressBackAction,0)
            val pressMenuAction = AbstractAction(
                    actionName = AbstractActionType.PRESS_MENU.actionName
            )
            actionCount.put(pressMenuAction,0)
            val swipeAction = AbstractAction(
                    actionName = AbstractActionType.SWIPE.actionName
            )
            actionCount.put(swipeAction,0)
        }

        fun getWidgetGroup(widget: Widget, guiState: State<*>): WidgetGroup?{
            val tempAttributePath = HashMap<Widget,AttributePath>()
            val tempChildWidgetAttributePaths = HashMap<Widget,AttributePath>()
            return widgets.find {
                       val reducedAttributePath = AbstractionFunction.INSTANCE.reduce(widget,guiState,activity,tempAttributePath,tempChildWidgetAttributePaths)
                        val attributePath = it.attributePath
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
            val unexcerisedActions = ArrayList<AbstractAction>()
            unexcerisedActions.addAll(actionCount.filter { it.value == 0 }.keys)
            unexcerisedActions.addAll(widgets.map { it.actionCount.filter { it.value==0 }}.map { it.keys }.flatten())
            return unexcerisedActions
        }

    override fun toString(): String {
        return "AbstractState[${this.hashCode()}]-${window}-Rotation:$rotation"
    }
}

class VirtualAbstractState(activity: String,
                           staticNode: WTGNode,
                           isHomeScreen: Boolean=false): AbstractState(
        activity = activity,
        window = staticNode,
        isFromLaunch = false,
        rotation = Rotation.PORTRAIT
        )

class AppResetAbstractState():AbstractState(activity = "", window = WTGLauncherNode.getOrCreateNode(),isFromLaunch = false,rotation = Rotation.PORTRAIT)