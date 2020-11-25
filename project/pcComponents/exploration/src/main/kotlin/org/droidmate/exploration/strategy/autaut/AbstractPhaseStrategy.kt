package org.droidmate.exploration.strategy.autaut

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.autaut.WTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.strategy.autaut.task.AbstractStrategyTask
import org.droidmate.explorationModel.interaction.State
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

abstract class AbstractPhaseStrategy(
        val autAutTestingStrategy: AutAutTestingStrategy,
        val budgetScale: Double,
        val useVirtualAbstractState: Boolean,
        val delay: Long,
        val useCoordinateClicks: Boolean
) {
    lateinit var phaseState: PhaseState
    lateinit var autautMF: AutAutMF

    var strategyTask: AbstractStrategyTask? = null
    abstract fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction
    abstract fun isTargetState(currentState: State<*>): Boolean
    abstract fun isTargetWindow(window: Window): Boolean

    abstract fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath>
    abstract fun getPathsToTargetWindows(currentState: State<*>, includePressbackEvent: Boolean): List<TransitionPath>

    fun hasUnexploreWidgets(currentState: State<*>): Boolean {
        return Helper.getVisibleInteractableWidgets(currentState)
                .filterNot { it.isInputField || it.checked.isEnabled()  }.any {
                    runBlocking { autAutTestingStrategy.getActionCounter().widgetCntForState(it.uid,currentState.uid) == 0 }
                }
    }
    open fun getPathsToWindowToExplore(currentState: State<*>, targetWindow: Window, usingPressback: Boolean, includeReset: Boolean): List<TransitionPath> {
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        val targetStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow && it != currentAbstractState }.toHashSet()
        val stateByActionCount = HashMap<AbstractState,Double>()
        targetStates.forEach {
//            val unExercisedActionsSize = it.getUnExercisedActions(null).filter { it.widgetGroup!=null }.size
//            if (unExercisedActionsSize > 0 )
//                stateByActionCount.put(it,it.getUnExercisedActions(null).size.toDouble())
            stateByActionCount.put(it, it.computeScore(autautMF))
        }
        if (stateByActionCount.isEmpty()) {
            targetStates.forEach {
                stateByActionCount.put(it,1.0)
            }
        }
        getPathToStates(
                transitionPaths = transitionPaths,
                stateByScore = stateByActionCount,
                currentAbstractState =  currentAbstractState,
                currentState =  currentState,
                stopWhenHavingUnexercisedAction =  false,
                includeWTG =  useVirtualAbstractState,
                includeReset =  includeReset,
                includeBackEvent =  usingPressback,
                shortest =  true,
                pathCountLimitation = 10)
        return transitionPaths
    }
    abstract fun getCurrentTargetEvents(currentState: State<*>):  Set<AbstractAction>

    abstract fun hasNextAction(currentState: State<*>): Boolean


    abstract fun registerTriggeredEvents(chosenAbstractAction: AbstractAction, currentState: State<*>)

    fun getPathToStates(transitionPaths: ArrayList<TransitionPath>, stateByScore: Map<AbstractState, Double>
                        , currentAbstractState: AbstractState, currentState: State<*>
                        , stopWhenHavingUnexercisedAction: Boolean
                        , includeWTG: Boolean, includeBackEvent: Boolean, includeReset:Boolean
                        , shortest: Boolean
                        , pathCountLimitation: Int
                        , forcingExplicit: Boolean = false) {
        val candidateStates = HashMap(stateByScore)
        while (transitionPaths.isEmpty() && candidateStates.isNotEmpty()) {
            while (transitionPaths.isEmpty() && candidateStates.isNotEmpty()) {
                while (transitionPaths.isEmpty() && candidateStates.isNotEmpty()) {
                    val abstractState = candidateStates.maxBy { it.value }!!.key
                    PathFindingHelper.findPathToTargetComponent(currentState = currentState
                            , root = currentAbstractState
                            , traversingNodes = listOf(Pair(autautMF.windowStack.clone() as Stack<Window>, currentAbstractState))
                            , finalTarget = abstractState
                            , allPaths = transitionPaths
                            , includeBackEvent = includeBackEvent
                            , includingWTG = includeWTG
                            , stopWhenHavingUnexercisedAction = stopWhenHavingUnexercisedAction
                            , includeReset = includeReset
                            , shortest = shortest
                            , pathCountLimitation = pathCountLimitation,
                            autautMF = autautMF
                            , forcingExplicit = forcingExplicit)
                    //windowStates.remove(abstractState)
                    candidateStates.remove(abstractState)
                }
            }

        }
    }

}