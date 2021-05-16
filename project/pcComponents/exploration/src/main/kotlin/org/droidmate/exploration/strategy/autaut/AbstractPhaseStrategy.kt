package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.atua.EWTG.*
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.strategy.autaut.task.AbstractStrategyTask
import org.droidmate.explorationModel.interaction.State
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.exp

abstract class AbstractPhaseStrategy(
        val atuaTestingStrategy: ATUATestingStrategy,
        val scaleFactor: Double,
        val useVirtualAbstractState: Boolean,
        val delay: Long,
        val useCoordinateClicks: Boolean
) {
    lateinit var phaseState: PhaseState
    lateinit var autautMF: ATUAMF

    var strategyTask: AbstractStrategyTask? = null
    abstract fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction
    abstract fun isTargetState(currentState: State<*>): Boolean
    abstract fun isTargetWindow(window: Window): Boolean

    abstract fun getPathsToExploreStates(currentState: State<*>, pathType: PathFindingHelper.PathType): List<TransitionPath>
    abstract fun getPathsToTargetWindows(currentState: State<*> ,pathType: PathFindingHelper.PathType): List<TransitionPath>

    fun getUnexhaustedExploredAbstractState(currentState: State<*>): List<AbstractState> {
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return emptyList()
        val runtimeAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
                .filterNot { it is VirtualAbstractState
                        || it == currentAbstractState
                        || it.window is Launcher
                        || it.window is OutOfApp
                        || it.isRequestRuntimePermissionDialogBox
                        || it.isAppHasStoppedDialogBox
                        || it.guiStates.isEmpty()
                        || it.guiStates.all { autautMF.getUnexploredWidget(it).isEmpty() }
                }
        return runtimeAbstractStates
    }
    fun hasUnexploreWidgets(currentState: State<*>): Boolean {
        return autautMF.getUnexploredWidget(currentState).isNotEmpty()
    }
    open fun getPathsToWindowToExplore(currentState: State<*>, targetWindow: Window, pathType: PathFindingHelper.PathType, explore: Boolean): List<TransitionPath> {
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        var targetStates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
            it.window == targetWindow
                    && it != currentAbstractState
        }.toHashSet()
        if (explore) {
            targetStates.removeIf { it is VirtualAbstractState && it.getUnExercisedActions(null,autautMF).isEmpty() }
            if (targetStates.isEmpty()) {
                targetStates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
                    it.window == targetWindow
                            && it != currentAbstractState
                            && it.guiStates.all { autautMF.getUnexploredWidget(it).isNotEmpty() }
                            && it !is VirtualAbstractState
                }.toHashSet()
            }
        }

        val stateByActionCount = HashMap<AbstractState,Double>()
        targetStates.forEach {
//            val unExercisedActionsSize = it.getUnExercisedActions(null).filter { it.widgetGroup!=null }.size
//            if (unExercisedActionsSize > 0 )
//                stateByActionCount.put(it,it.getUnExercisedActions(null).size.toDouble())
            stateByActionCount.put(it, 1.0)
        }
        if (stateByActionCount.isEmpty()) {
            targetStates.forEach {
                if (stateByActionCount.get(it)!!>0)
                    stateByActionCount.put(it,1.0)
            }
        }
        getPathToStatesBasedOnPathType(pathType, transitionPaths, stateByActionCount, currentAbstractState, currentState)
        return transitionPaths
    }

     fun getPathToStatesBasedOnPathType(pathType: PathFindingHelper.PathType, transitionPaths: ArrayList<TransitionPath>, stateByActionCount: HashMap<AbstractState, Double>, currentAbstractState: AbstractState, currentState: State<*>) {
        if (pathType != PathFindingHelper.PathType.ANY)
            getPathToStates(
                    transitionPaths = transitionPaths,
                    stateByScore = stateByActionCount,
                    currentAbstractState = currentAbstractState,
                    currentState = currentState,
                    stopWhenHavingUnexercisedAction = false,
                    pathType = pathType,
                    shortest = false,
                    pathCountLimitation = 5)
        else {
            getPathToStates(
                    transitionPaths = transitionPaths,
                    stateByScore = stateByActionCount,
                    currentAbstractState = currentAbstractState,
                    currentState = currentState,
                    stopWhenHavingUnexercisedAction = false,
                    pathType = PathFindingHelper.PathType.INCLUDE_INFERED,
                    shortest = false,
                    pathCountLimitation = 5)
            if (transitionPaths.isEmpty()) {
                getPathToStates(
                        transitionPaths = transitionPaths,
                        stateByScore = stateByActionCount,
                        currentAbstractState = currentAbstractState,
                        currentState = currentState,
                        stopWhenHavingUnexercisedAction = false,
                        pathType = PathFindingHelper.PathType.RESET,
                        shortest = false,
                        pathCountLimitation = 5)
            }
            if (transitionPaths.isEmpty()) {
                getPathToStates(
                        transitionPaths = transitionPaths,
                        stateByScore = stateByActionCount,
                        currentAbstractState = currentAbstractState,
                        currentState = currentState,
                        stopWhenHavingUnexercisedAction = false,
                        pathType = PathFindingHelper.PathType.NORMAL,
                        shortest = false,
                        pathCountLimitation = 5)
            }
            if (transitionPaths.isEmpty()) {
                getPathToStates(
                        transitionPaths = transitionPaths,
                        stateByScore = stateByActionCount,
                        currentAbstractState = currentAbstractState,
                        currentState = currentState,
                        stopWhenHavingUnexercisedAction = false,
                        pathType = PathFindingHelper.PathType.TRACE,
                        shortest = false,
                        pathCountLimitation = 5)
            }
            if (transitionPaths.isEmpty()) {
                getPathToStates(
                        transitionPaths = transitionPaths,
                        stateByScore = stateByActionCount,
                        currentAbstractState = currentAbstractState,
                        currentState = currentState,
                        stopWhenHavingUnexercisedAction = false,
                        pathType = PathFindingHelper.PathType.WTG,
                        shortest = false,
                        pathCountLimitation = 5)
            }
        }
    }

    abstract fun getCurrentTargetEvents(currentState: State<*>):  Set<AbstractAction>

    abstract fun hasNextAction(currentState: State<*>): Boolean


    abstract fun registerTriggeredEvents(chosenAbstractAction: AbstractAction, currentState: State<*>)

    fun getPathToStates(transitionPaths: ArrayList<TransitionPath>, stateByScore: Map<AbstractState, Double>
                        , currentAbstractState: AbstractState, currentState: State<*>
                        , stopWhenHavingUnexercisedAction: Boolean
                        , shortest: Boolean
                        , pathCountLimitation: Int
                        , pathType: PathFindingHelper.PathType) {
        val candidateStates = HashMap(stateByScore)
        while (candidateStates.isNotEmpty()) {
            if (transitionPaths.isNotEmpty())
                break
            val abstractState = candidateStates.maxBy { it.value }!!.key
            PathFindingHelper.findPathToTargetComponent(currentState = currentState
                    , root = currentAbstractState
                    , finalTarget = abstractState
                    , allPaths = transitionPaths
                    , shortest = shortest
                    , pathCountLimitation = pathCountLimitation
                    , autautMF = autautMF
                    , pathType = pathType)
            //windowStates.remove(abstractState)
            candidateStates.remove(abstractState)
        }
    }

}