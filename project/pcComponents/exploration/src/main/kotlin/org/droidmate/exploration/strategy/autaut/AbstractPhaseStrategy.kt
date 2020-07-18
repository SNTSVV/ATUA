package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractTransitionGraph
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.WidgetGroup
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
import org.droidmate.exploration.strategy.autaut.task.AbstractStrategyTask
import org.droidmate.exploration.strategy.autaut.task.RandomExplorationTask
import org.droidmate.explorationModel.interaction.State
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

abstract class AbstractPhaseStrategy(
        val regressionTestingStrategy: RegressionTestingStrategy,
        val budgetScale: Double,
        val useVirtualAbstractState: Boolean,
        val delay: Long,
        val useCoordinateClicks: Boolean
) {
    lateinit var phaseState: PhaseState
    lateinit var regressionTestingMF: AutAutMF

    var strategyTask: AbstractStrategyTask? = null
    abstract fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction

    abstract fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath>
    abstract fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath>

    open fun getPathsToWindow(currentState: State<*>, targetWindow: WTGNode): List<TransitionPath> {
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        val targetStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow }.toHashSet()
        while (targetStates.isNotEmpty() && transitionPaths.isEmpty()) {
            val targetState = targetStates.random()
            targetStates.remove(targetState)
            val existingPaths: List<TransitionPath>?
            existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbstractState,targetState)]
            if (existingPaths != null && existingPaths.isNotEmpty())
            {
                transitionPaths.addAll(existingPaths)
            }
            else {
                //check if there is any edge from App State to that node
                //TODO check for abstract state
                val feasibleEdges = regressionTestingMF.abstractTransitionGraph.edges().filter {e ->
                    e.destination?.data!! == targetState
                }
                if (feasibleEdges.isNotEmpty())
                {
                    val childParentMap = HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<WidgetGroup,String>>?>()
                    childParentMap.put(currentAbstractState, null)
                    findPathToTargetComponentByBFS(currentState = currentState
                            , root = currentAbstractState
                            , traversingNodes = listOf(Pair(first = regressionTestingMF.windowStack.clone() as Stack<WTGNode>,second = currentAbstractState))
                            , finalTarget = targetState
                            , allPaths = transitionPaths
                            , includeBackEvent = true
                            , childParentMap = childParentMap
                            , level = 0
                    , useVirtualAbstractState = useVirtualAbstractState)
                }
            }
        }

        return transitionPaths
    }
    abstract fun getCurrentTargetEvents(currentState: State<*>):  List<AbstractAction>
    internal fun tryRandom(eContext: ExplorationContext<*, *, *>): ExplorationAction {

        val extraTask = RandomExplorationTask.getInstance(regressionTestingMF, regressionTestingStrategy,delay, useCoordinateClicks)
        extraTask.initialize(eContext.getCurrentState()).also{
            extraTask.setMaximumAttempt(currentState = eContext.getCurrentState(), attempt = 1)
        }
        val randomWidgets = extraTask.chooseWidgets(currentState = eContext.getCurrentState())
        if (randomWidgets.isEmpty()) {
            return ExplorationAction.pressBack()
        } else {
            val action2 = extraTask.chooseAction(eContext.getCurrentState())
            if (action2 != null)
                return action2
            else
                return ExplorationAction.pressBack()
        }
    }
    abstract fun isEnd(): Boolean

    fun findPathToTargetComponentByBFS(currentState: State<*>, root: AbstractState, traversingNodes: List<Pair<Stack<WTGNode>, AbstractState>>, finalTarget: AbstractState
                                       , allPaths: ArrayList<TransitionPath>, includeBackEvent: Boolean
                                       , childParentMap: HashMap<AbstractState,Triple<AbstractState, AbstractInteraction,HashMap<WidgetGroup,String>>?>, level: Int, useVirtualAbstractState: Boolean
                                       , stopWhenHavingUnexercisedAction: Boolean = false) {
        if (traversingNodes.isEmpty())
            return
        val graph = regressionTestingMF.abstractTransitionGraph
        val nextLevelNodes = ArrayList<Pair<Stack<WTGNode>,AbstractState>>()
        for (traversing in traversingNodes)
        {

            val source = traversing.second
            if (source.window is WTGLauncherNode)
                continue
            if (source is VirtualAbstractState && !useVirtualAbstractState) {
                continue
            }
            val windowStack = traversing.first
            if (windowStack.isEmpty())
                continue
            getNextTraversingNodes(includeBackEvent, windowStack, graph, source, level, childParentMap, finalTarget, root, allPaths, nextLevelNodes,stopWhenHavingUnexercisedAction)
        }
        /*if (nextLevelNodes.isEmpty() && !useVirtualAbstractState) {
            for (traversing in traversingNodes)
            {
                val source = traversing.second
                val windowStack = traversing.first
                if (windowStack.isEmpty())
                    continue
                getNextTraversingNodes(includeBackEvent, windowStack, graph, source, level, childParentMap, finalTarget, root, allPaths, nextLevelNodes,stopWhenHavingUnexercisedAction)
            }
        }*/
        if (allPaths.isEmpty())
            findPathToTargetComponentByBFS(currentState, root, nextLevelNodes, finalTarget, allPaths, includeBackEvent, childParentMap, level+1,useVirtualAbstractState)
    }

    private fun getNextTraversingNodes(includeBackEvent: Boolean
                                       , windowStack: Stack<WTGNode>
                                       , graph: AbstractTransitionGraph
                                       , source: AbstractState, level: Int
                                       , childParentMap: HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<WidgetGroup,String>>?>
                                       , finalTarget: AbstractState
                                       , root: AbstractState
                                       , allPaths: ArrayList<TransitionPath>
                                       , nextLevelNodes: ArrayList<Pair<Stack<WTGNode>, AbstractState>>
    , stopWhenHavingUnexercisedAction: Boolean) {
        if (includeBackEvent && windowStack.isNotEmpty()) {
            val processingBackEdges = ArrayList<Edge<AbstractState, AbstractInteraction>>()
            val backEdges = graph.edges(source).filter {
                it.label.abstractAction.actionName.isPressBack()
                        && it.destination != null
            }

            val backCandiates = backEdges.filter {
                        it.destination!!.data.window !is WTGOutScopeNode
                        && isTheSamePrevWindow(windowStack.peek(),it)
            }.groupBy { it.label.isImplicit }

            if (backCandiates.containsKey(false)) {
                if (!backCandiates[false]!!.any { backToLauncherOrNot(it) }) {
                    backCandiates[false]!!.forEach {
                        if (!childParentMap.containsKey(it.destination!!.data)) {
                            processingBackEdges.add(it)
                        }
                    }
                }
            } else if (backCandiates.containsKey(true)) {
                backCandiates[true]!!.forEach {
                    if (!childParentMap.containsKey(it.destination!!.data)) {
                        processingBackEdges.add(it)
                    }
                }
            }

            processingBackEdges.forEach { edge ->
                val backEvent = edge.label
                val backState = edge.destination!!.data
                val edgeCondition = graph.edgeConditions[edge]?: HashMap()
                childParentMap.put(backState, Triple(source, backEvent,edgeCondition))
                if (backState == finalTarget ||
                        (stopWhenHavingUnexercisedAction &&
                                backState !is VirtualAbstractState &&
                                backState.getUnExercisedActions().isNotEmpty())) {
                    val fullPath = createTransitionPath(backState, root, childParentMap)
                    allPaths.add(fullPath)
                    regressionTestingMF.registerTransitionPath(root, backState, fullPath)
                } else {
                    val nextWindowStack = createWindowStackForNext(windowStack, source, backState)
                    nextLevelNodes.add(Pair(first = nextWindowStack, second = backState))
                }
            }
        }
        val edgesFromSource = graph.edges(source).filter { it.destination!=null && it.source != it.destination }
        val possibleTransitions = edgesFromSource.filter {
            it.destination != null
                    && it.source != it.destination
                    && it.destination!!.data.window !is WTGFakeNode
                    && !(it.label.abstractAction.actionName.equals("MinimizeMaximize") || it.label.abstractAction.actionName.isPressBack())
                    && includingRotateUIOrNot(it)
                    && isTheSamePrevWindow(windowStack.peek(), it)

        }
        val processedTransition = ArrayList<Edge<AbstractState, AbstractInteraction>>()
        possibleTransitions.groupBy({ it.label }, { it }).forEach { _, u ->
            val reliableTransition = u.filter { !it.label.isImplicit }
            val implicitTransitions = u.filter { it.label.isImplicit }
            if (reliableTransition.isEmpty()) {
                processedTransition.addAll(implicitTransitions)
            }
            //processedTransition.addAll(implicitTransitions)
            processedTransition.addAll(reliableTransition)

        }

        processedTransition.forEach {
            val nextState = it.destination!!.data
            //Avoid loop
            if (!childParentMap.containsKey(nextState)) {
                val event = it.label
                var isDisableEdge: Boolean = false
                //It is the init node and the disableEdges contains it
                isDisableEdge = regressionTestingMF.checkIsDisableEdge(it)
                if (!isDisableEdge) {
                    val edgeCondition = graph.edgeConditions[it]?: HashMap()
                    childParentMap.put(nextState, Triple(source, event,edgeCondition))
                    if (nextState == finalTarget ||
                            (stopWhenHavingUnexercisedAction &&
                                    nextState !is VirtualAbstractState &&
                                    nextState.getUnExercisedActions().isNotEmpty())) {
                        val fullGraph = createTransitionPath(nextState, root, childParentMap)
                        allPaths.add(fullGraph)
                        regressionTestingMF.registerTransitionPath(root, nextState, fullGraph)
                    } else {
                        val nextWindowStack = if (event.abstractAction.actionName == "LaunchApp" || event.abstractAction.actionName == "ResetApp") {
                            Stack<WTGNode> ().also { it.push(WTGLauncherNode.getOrCreateNode()) }
                        } else {
                            createWindowStackForNext(windowStack, source, nextState)
                        }
                        nextLevelNodes.add(Pair(first = nextWindowStack, second = nextState))
                    }
                }
            }
        }
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
        if (regressionTestingMF.appRotationSupport)
            return true
        return it.label.abstractAction.actionName != "RotateUI"
    }

    private fun isTheSamePrevWindow(prevWindow: WTGNode?, it: Edge<AbstractState, AbstractInteraction>): Boolean {
        return (
                (prevWindow != null && it.label.prevWindow == prevWindow)
                        || prevWindow == null || it.label.prevWindow == null)
    }

    private fun createTransitionPath(finalTarget: AbstractState, startingNode: AbstractState, childParentMap: HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<WidgetGroup,String>>?>): TransitionPath {
        val fullPath = TransitionPath(startingNode)
        var backwardNode = finalTarget
        while (backwardNode!=startingNode)
        {
            val source = childParentMap[backwardNode]!!.first
            val event = childParentMap[backwardNode]!!.second
            val edge = fullPath.add(source,backwardNode,event)
            //fullPath.edgeConditions[edge] = childParentMap[backwardNode]!!.third
            val graphEdge = regressionTestingMF.abstractTransitionGraph.edge(source,backwardNode,event)
            if (graphEdge!=null && regressionTestingMF.abstractTransitionGraph.edgeConditions.containsKey(graphEdge))
            {
                fullPath.edgeConditions.put(edge,regressionTestingMF.abstractTransitionGraph.edgeConditions[graphEdge]!!)
            }
            backwardNode = source
        }
        return fullPath
    }

    abstract fun registerTriggeredEvents(chosenAbstractAction: AbstractAction, currentState: State<*>)

    fun getPathToUnexploredState(currentState: State<*>): List<TransitionPath> {
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState == null)
            return emptyList()
        val stateGraphMF: StateGraphMF = regressionTestingStrategy.eContext.getOrCreateWatcher()
        val allUnexploredStates = HashMap<State<*>, Int>()
        stateGraphMF.getVertices().filter {!it.data.isHomeScreen}.forEach {
            val unexploreEdges = stateGraphMF.edges(it).filter { it.destination == null }
            if (unexploreEdges.size > 0) {
                allUnexploredStates.put(it.data, unexploreEdges.size)
            }
        }
        val toExploreAbstractStates = HashMap<AbstractState, Int> ()
        allUnexploredStates.forEach {
            val abstractState = AbstractStateManager.instance.getAbstractState(it.key)
            if (abstractState != null && !abstractState.isHomeScreen && !abstractState.isOutOfApplication) {
                if (!toExploreAbstractStates.containsKey(abstractState)) {
                    toExploreAbstractStates.put(abstractState,0)
                }
                toExploreAbstractStates[abstractState] = toExploreAbstractStates[abstractState]!! + it.value
            }
        }

        val transitionPaths = ArrayList<TransitionPath>()

        getPathToStates(transitionPaths,toExploreAbstractStates,currentAbstractState,currentState,false)
        return transitionPaths

    }

    fun getPathToStates(transitionPaths: ArrayList<TransitionPath>, stateByScore: HashMap<AbstractState, Int>, currentAbstractState: AbstractState, currentState: State<*>, stopWhenHavingUnexercisedAction: Boolean) {
        while (transitionPaths.isEmpty() && stateByScore.isNotEmpty()) {
            /*val leastVisitedFoundNode = candidateVisitedFoundNodes.minBy { it.value }!!
            val leastVisitedCount = leastVisitedFoundNode.value
            var biasedCandidate =candidateVisitedFoundNodes.filter { it.value == leastVisitedCount }.map { it.key }.random()
//            val topTargetWindow = biasedCandidates.sortedByDescending { it.unexercisedWidgetCount }.first()
//            val targetWindows = biasedCandidates.filter { it.unexercisedWidgetCount == topTargetWindow.unexercisedWidgetCount && it!=currentAbstractState }
            candidateVisitedFoundNodes.remove(biasedCandidate)
            val abstractStates = runtimeAbstractStates.filter { it.window == biasedCandidate }*/
            val maxActionCount = stateByScore.maxBy { it.value }!!.value
            val abstractStates = stateByScore.filter { it.value == maxActionCount }.keys
            abstractStates.forEach {
                val existingPaths: List<TransitionPath>?
                existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbstractState, it)]
                if (existingPaths != null && existingPaths.isNotEmpty()) {
                    transitionPaths.addAll(existingPaths)
                } else {
                    val childParentMap = HashMap<AbstractState, Pair<AbstractState, AbstractInteraction>?>()
                    childParentMap.put(currentAbstractState, null)
                    findPathToTargetComponentByBFS(currentState = currentState
                            , root = currentAbstractState
                            , traversingNodes = listOf(Pair(regressionTestingMF.windowStack.clone() as Stack<WTGNode>, currentAbstractState))
                            , finalTarget = it
                            , allPaths = transitionPaths
                            , includeBackEvent = true
                            , childParentMap = HashMap()
                            , level = 0,
                            useVirtualAbstractState = useVirtualAbstractState
                            , stopWhenHavingUnexercisedAction = stopWhenHavingUnexercisedAction)
                }
                stateByScore.remove(it)
            }

        }
    }
}