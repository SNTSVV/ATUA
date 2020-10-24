package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractTransitionGraph
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.strategy.autaut.task.AbstractStrategyTask
import org.droidmate.exploration.strategy.autaut.task.RandomExplorationTask
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
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
    abstract fun isTargetWindow(window: WTGNode): Boolean

    abstract fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath>
    abstract fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath>

    open fun getPathsToWindow(currentState: State<*>, targetWindow: WTGNode, usingPressback: Boolean): List<TransitionPath> {
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
        getPathToStates(transitionPaths,stateByActionCount,currentAbstractState,currentState,false,useVirtualAbstractState,usingPressback,usingPressback, false,25)
        return transitionPaths
    }
    abstract fun getCurrentTargetEvents(currentState: State<*>):  Set<AbstractAction>
    internal fun tryRandom(eContext: ExplorationContext<*, *, *>): ExplorationAction {

        val extraTask = RandomExplorationTask.getInstance(autautMF, autAutTestingStrategy,delay, useCoordinateClicks)
        extraTask.initialize(eContext.getCurrentState()).also{
            extraTask.setMaxiumAttempt(currentState = eContext.getCurrentState(), attempt = 1)
        }
        val randomWidgets = extraTask.chooseWidgets(currentState = eContext.getCurrentState())
        if (randomWidgets.isEmpty()) {
            return ExplorationAction.pressBack()
        } else {
            val action2 = extraTask.chooseAction(eContext.getCurrentState())
                return action2
        }
    }
    abstract fun hasNextAction(currentState: State<*>): Boolean



    private fun includingLaunchOrNot(it: Edge<AbstractState, AbstractInteraction>, includeBackEvent: Boolean, depth: Int): Boolean {
        if (depth > 0)
            return false
        if (includeBackEvent)
            return true
        if (it.label.abstractAction.isLaunchOrReset()) {
            return false
        }
        return true
    }

    private fun includingWTGOrNot(edge: Edge<AbstractState, AbstractInteraction>, includeWTG: Boolean): Boolean {
        if (includeWTG)
            return true
        if (edge.label.fromWTG)
            return false
        return true
    }

    private fun isArrived(nextState: AbstractState, finalTarget: AbstractState, stopWhenHavingUnexercisedAction: Boolean, useVirtualAbstractState: Boolean): Boolean {
        if (nextState == finalTarget)
            return true
        if (stopWhenHavingUnexercisedAction &&
                nextState !is VirtualAbstractState &&
                nextState.getUnExercisedActions(null).isNotEmpty())
            return true
        if (useVirtualAbstractState && finalTarget is VirtualAbstractState && nextState.window == finalTarget.window)
            return true
        return false
    }

    private fun includingResetOrNot(it: Edge<AbstractState, AbstractInteraction>, includeReset: Boolean,depth: Int): Boolean {
        if (depth > 0)
            return false
        if (includeReset)
            return true
        else
            return it.label.abstractAction.actionType!=AbstractActionType.RESET_APP
    }

    private fun createWindowStackForNext(windowStack: Stack<WTGNode>, prevState: AbstractState, nextState: AbstractState): Stack<WTGNode> {
        val nextWindowStack = windowStack.clone() as Stack<WTGNode>
        if (nextWindowStack.contains(nextState.window) && nextWindowStack.size > 1) {
            // Return to the prev window
            // Pop the window
            while (nextWindowStack.pop() != nextState.window) {

            }
        } else {
            if (nextState.window != prevState.window) {
                if (prevState.window !is WTGDialogNode && prevState.window !is WTGOptionsMenuNode) {
                    nextWindowStack.push(prevState.window)
                }
            } else if (nextState.isOpeningKeyboard) {
                nextWindowStack.push(nextState.window)
            }
        }
        return nextWindowStack
    }

    private fun backToLauncherOrNot(it: Edge<AbstractState, AbstractInteraction>) =
            it.destination!!.data.window !is WTGLauncherNode

    private fun includingRotateUIOrNot(it: Edge<AbstractState, AbstractInteraction>): Boolean {
        if (autautMF.appRotationSupport)
            return true
        return it.label.abstractAction.actionType != AbstractActionType.ROTATE_UI
    }

    private fun isTheSamePrevWindow(prevWindow: WTGNode?, it: Edge<AbstractState, AbstractInteraction>): Boolean {
        return (
                (prevWindow != null && it.label.prevWindow == prevWindow)
                        || prevWindow == null || it.label.prevWindow == null)
    }

    private fun createTransitionPath(finalTarget: AbstractState, startingNode: AbstractState, childParentMap: HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<Widget,String>>?>): TransitionPath {
        val fullPath = TransitionPath(startingNode)
        var backwardNode = finalTarget
        while (backwardNode!=startingNode)
        {
            val source = childParentMap[backwardNode]!!.first
            val event = childParentMap[backwardNode]!!.second
            val edge = fullPath.add(source,backwardNode,event)
            //fullPath.edgeConditions[edge] = childParentMap[backwardNode]!!.third
            val graphEdge = autautMF.abstractTransitionGraph.edge(source,backwardNode,event)
            if (graphEdge!=null && autautMF.abstractTransitionGraph.edgeConditions.containsKey(graphEdge))
            {
                fullPath.edgeConditions.put(edge,autautMF.abstractTransitionGraph.edgeConditions[graphEdge]!!)
            }
            backwardNode = source
        }
        return fullPath
    }

    abstract fun registerTriggeredEvents(chosenAbstractAction: AbstractAction, currentState: State<*>)

    fun getPathToStates(transitionPaths: ArrayList<TransitionPath>, stateByScore: Map<AbstractState, Double>
                        , currentAbstractState: AbstractState, currentState: State<*>
                        , stopWhenHavingUnexercisedAction: Boolean
                        , useVABS: Boolean, includeBackEvent: Boolean, includeReset:Boolean
                        , shortest: Boolean
                        , pathCountLimitation: Int) {
        val candidateStates = HashMap(stateByScore)
        while (transitionPaths.isEmpty() && candidateStates.isNotEmpty()) {
            while (transitionPaths.isEmpty() && candidateStates.isNotEmpty()) {
                while (transitionPaths.isEmpty() && candidateStates.isNotEmpty()) {
                    val abstractState = candidateStates.maxBy { it.value }!!.key
                    val existingPaths: List<TransitionPath>?
                    existingPaths = autautMF.allAvailableTransitionPaths[Pair(currentAbstractState, abstractState)]
                    if (existingPaths != null && existingPaths.isNotEmpty()) {
                        transitionPaths.addAll(existingPaths)
                    } else {

                        PathFindingHelper.findPathToTargetComponent(currentState = currentState
                                , root = currentAbstractState
                                , traversingNodes = listOf(Pair(autautMF.windowStack.clone() as Stack<WTGNode>, currentAbstractState))
                                , finalTarget = abstractState
                                , allPaths = transitionPaths
                                , includeBackEvent = includeBackEvent
                                , useVirtualAbstractState = useVABS
                                , stopWhenHavingUnexercisedAction = stopWhenHavingUnexercisedAction
                                , includeReset = includeReset
                        , shortest = shortest
                        , pathCountLimitation = pathCountLimitation,
                                autautMF = autautMF)
                    }
                    //windowStates.remove(abstractState)
                    candidateStates.remove(abstractState)
                }
            }

        }
    }
}