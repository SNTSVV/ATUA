package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticWidget
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

open class AbstractState (
        val activity: String,
        val widgets: ArrayList<WidgetGroup> = ArrayList(),
        val guiStates: ArrayList<State<*>> = ArrayList(),
        val staticNode: WTGNode,
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
        var rotation:Int = 0

) {
        val unexercisedWidgetCount: Int
                get() {return widgets.filter { it.exerciseCount==0}.size}

        fun getWidgetGroup(widget: Widget, guiState: State<*>): WidgetGroup?{
            val tempAttributePath = HashMap<Widget,AttributePath>()
            val tempChildWidgetAttributePaths = HashMap<Widget,AttributePath>()
            return widgets.find {
                       val reducedAttributePath = AbstractionFunction.INSTANCE.reduce(widget,guiState,activity,tempAttributePath,tempChildWidgetAttributePaths)
                        val attributePath = it.attributePath
                        it.attributePath.equals(reducedAttributePath)
                }
        }
}

class VirtualAbstractState(activity: String,
                           staticNode: WTGNode,
                           isHomeScreen: Boolean=false): AbstractState(
        activity = activity,
        staticNode = staticNode,
        isFromLaunch = false
        )

class AppResetAbstractState():AbstractState(activity = "", staticNode = WTGLauncherNode.getOrCreateNode(),isFromLaunch = false)