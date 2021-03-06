package org.droidmate.exploration.strategy.atua

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.atua.modelFeatures.dstg.AbstractAction
import org.atua.modelFeatures.dstg.AbstractState
import org.atua.modelFeatures.dstg.AbstractStateManager
import org.atua.modelFeatures.dstg.VirtualAbstractState
import org.atua.modelFeatures.helper.PathFindingHelper
import org.atua.modelFeatures.ewtg.*
import org.atua.modelFeatures.ewtg.window.Dialog
import org.atua.modelFeatures.ewtg.window.Launcher
import org.atua.modelFeatures.ewtg.window.OutOfApp
import org.atua.modelFeatures.ewtg.window.Window
import org.droidmate.exploration.strategy.atua.task.AbstractStrategyTask
import org.droidmate.explorationModel.interaction.State
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

abstract class AbstractPhaseStrategy(
        val atuaTestingStrategy: ATUATestingStrategy,
        val scaleFactor: Double,
        val useVirtualAbstractState: Boolean,
        val delay: Long,
        val useCoordinateClicks: Boolean
) {
    lateinit var phaseState: PhaseState
    lateinit var atuaMF: org.atua.modelFeatures.ATUAMF

    var strategyTask: AbstractStrategyTask? = null
    var fullControl: Boolean = false
    abstract fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction
    abstract fun isTargetState(currentState: State<*>): Boolean
    abstract fun isTargetWindow(window: Window): Boolean

    abstract fun getPathsToExploreStates(currentState: State<*>, pathType: PathFindingHelper.PathType): List<TransitionPath>
    abstract fun getPathsToTargetWindows(currentState: State<*> ,pathType: PathFindingHelper.PathType): List<TransitionPath>

    fun needReset(currentState: State<*>): Boolean {
        val interval = 100 * scaleFactor
        val lastReset = runBlocking {
            atuaTestingStrategy.eContext.explorationTrace.P_getActions()
                    .indexOfLast { it.actionType == "ResetApp" }
        }
        val currAction = atuaTestingStrategy.eContext.explorationTrace.size
        val diff = currAction - lastReset
        return diff > interval
    }

    fun getUnexhaustedExploredAbstractState(currentState: State<*>): List<AbstractState> {
        val currentAbstractState = AbstractStateManager.INSTANCE.getAbstractState(currentState)
        if (currentAbstractState==null)
            return emptyList()
        val runtimeAbstractStates = AbstractStateManager.INSTANCE.ABSTRACT_STATES
                .filterNot { it is VirtualAbstractState
                        || it == currentAbstractState
                        || it.window is Launcher
                        || it.window is OutOfApp
                        || (it.window is Dialog && (it.window as Dialog).ownerActivitys.all { it is OutOfApp })
                        || it.isRequestRuntimePermissionDialogBox
                        || it.isAppHasStoppedDialogBox
                        || it.attributeValuationMaps.isEmpty()
                        || it.guiStates.isEmpty()
                        || it.guiStates.all { atuaMF.actionCount.getUnexploredWidget(it).isEmpty() }
                }
        return runtimeAbstractStates
    }
    fun hasUnexploreWidgets(currentState: State<*>): Boolean {
        return atuaMF.actionCount.getUnexploredWidget(currentState).isNotEmpty()
    }

    open fun getPathsToWindowToExplore(currentState: State<*>, targetWindow: Window, pathType: PathFindingHelper.PathType, explore: Boolean): List<TransitionPath> {
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.INSTANCE.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        var targetStates = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
            it.window == targetWindow
                    && it != currentAbstractState
                    && (it is VirtualAbstractState || it.attributeValuationMaps.isNotEmpty())
        }.toHashSet()
        if (explore) {
            targetStates.removeIf {
                it is VirtualAbstractState || it.getUnExercisedActions(null,atuaMF).isEmpty() }
            if (targetStates.isEmpty()) {
                targetStates = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
                    it.window == targetWindow
                            && it != currentAbstractState
                            && it.guiStates.any { atuaMF.actionCount.getUnexploredWidget(it).isNotEmpty() }
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

     fun getPathToStatesBasedOnPathType(pathType: PathFindingHelper.PathType,
                                        transitionPaths: ArrayList<TransitionPath>,
                                        stateByActionCount: HashMap<AbstractState, Double>,
                                        currentAbstractState: AbstractState,
                                        currentState: State<*>,
                                        shortest: Boolean=true) {
        if (pathType != PathFindingHelper.PathType.ANY)
            getPathToStates(
                    transitionPaths = transitionPaths,
                    stateByScore = stateByActionCount,
                    currentAbstractState = currentAbstractState,
                    currentState = currentState,
                    pathType = pathType,
                    shortest = shortest)
        else {
            getPathToStates(
                    transitionPaths = transitionPaths,
                    stateByScore = stateByActionCount,
                    currentAbstractState = currentAbstractState,
                    currentState = currentState,
                    pathType = PathFindingHelper.PathType.NORMAL,
                    shortest = shortest)
            if (transitionPaths.isEmpty()) {
                getPathToStates(
                        transitionPaths = transitionPaths,
                        stateByScore = stateByActionCount,
                        currentAbstractState = currentAbstractState,
                        currentState = currentState,
                        pathType = PathFindingHelper.PathType.WTG,
                        shortest = shortest)
            }
            if (transitionPaths.isEmpty()) {
                getPathToStates(
                        transitionPaths = transitionPaths,
                        stateByScore = stateByActionCount,
                        currentAbstractState = currentAbstractState,
                        currentState = currentState,
                        pathType = PathFindingHelper.PathType.PARTIAL_TRACE,
                        shortest = shortest)
            }
            if (transitionPaths.isEmpty()) {
                getPathToStates(
                        transitionPaths = transitionPaths,
                        stateByScore = stateByActionCount,
                        currentAbstractState = currentAbstractState,
                        currentState = currentState,
                        pathType = PathFindingHelper.PathType.FULLTRACE,
                        shortest = shortest)
            }
        }
    }

    abstract fun getCurrentTargetEvents(currentState: State<*>):  Set<AbstractAction>

    abstract fun hasNextAction(currentState: State<*>): Boolean


    abstract fun registerTriggeredEvents(chosenAbstractAction: AbstractAction, currentState: State<*>)

    fun getPathToStates(transitionPaths: ArrayList<TransitionPath>, stateByScore: Map<AbstractState, Double>
                        , currentAbstractState: AbstractState, currentState: State<*>
                        , shortest: Boolean
                        , pathCountLimitation: Int = 1
                        , pathType: PathFindingHelper.PathType) {
        val candidateStates = HashMap(stateByScore)
        while (candidateStates.isNotEmpty()) {
            if (transitionPaths.isNotEmpty() && shortest)
                break
            val maxValue = candidateStates.maxBy { it.value }!!.value
            val abstractStates = candidateStates.filter { it.value == maxValue }
            abstractStates.keys.forEach { abstractState->
                PathFindingHelper.findPathToTargetComponent(currentState = currentState
                    , root = currentAbstractState
                    , finalTarget = abstractState
                    , allPaths = transitionPaths
                    , shortest = shortest
                    , pathCountLimitation = pathCountLimitation
                    , autautMF = atuaMF
                    , pathType = pathType)
                //windowStates.remove(abstractState)
                candidateStates.remove(abstractState)
            }
            if (shortest && transitionPaths.isNotEmpty()) {
                val minSequenceLength = transitionPaths.map { it.path.size }.min()!!
                transitionPaths.removeIf { it.path.size > minSequenceLength }
            }
        }

    }

}