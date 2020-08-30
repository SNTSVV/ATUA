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
import org.droidmate.exploration.modelFeatures.autaut.helper.ProbabilityDistribution
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
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

    abstract fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath>
    abstract fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath>

    open fun getPathsToWindow(currentState: State<*>, targetWindow: WTGNode): List<TransitionPath> {
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        val targetStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow && it != currentAbstractState }.toHashSet()
        val stateByActionCount = HashMap<AbstractState,Double>()
        targetStates.forEach {
            val unExercisedActionsSize = it.getUnExercisedActions(null).filter { it.widgetGroup!=null }.size
            if (unExercisedActionsSize > 0 )
                stateByActionCount.put(it,it.getUnExercisedActions(null).size.toDouble())
        }
        if (stateByActionCount.isEmpty()) {
            targetStates.forEach {
                stateByActionCount.put(it,1.0)
            }
        }
        getPathToStates(transitionPaths,stateByActionCount,currentAbstractState,currentState,false,true)
        /*while (targetStates.isNotEmpty() && transitionPaths.isEmpty()) {
            val targetState = targetStates.random()
            targetStates.remove(targetState)
            val existingPaths: List<TransitionPath>?
            existingPaths = autautMF.allAvailableTransitionPaths[Pair(currentAbstractState,targetState)]
            if (existingPaths != null && existingPaths.isNotEmpty())
            {
                transitionPaths.addAll(existingPaths)
            }
            else {
                //check if there is any edge from App State to that node
                //TODO check for abstract state
                val feasibleEdges = autautMF.abstractTransitionGraph.edges().filter { e ->
                    e.destination?.data!! == targetState
                }
                if (feasibleEdges.isNotEmpty())
                {
                    val childParentMap = HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<WidgetGroup,String>>?>()
                    childParentMap.put(currentAbstractState, null)
                    findPathToTargetComponentByBFS(currentState = currentState
                            , root = currentAbstractState
                            , traversingNodes = listOf(Pair(first = autautMF.windowStack.clone() as Stack<WTGNode>,second = currentAbstractState))
                            , finalTarget = targetState
                            , allPaths = transitionPaths
                            , includeBackEvent = true
                            , childParentMap = childParentMap
                            , level = 0
                    , useVirtualAbstractState = false)
                }
            }
        }*/

        /*if (transitionPaths.isEmpty() && useVirtualAbstractState ) {
            val targetStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow }.toHashSet()
            while (targetStates.isNotEmpty() && transitionPaths.isEmpty()) {
                val targetState = targetStates.random()
                targetStates.remove(targetState)
                val existingPaths: List<TransitionPath>?
                existingPaths = autautMF.allAvailableTransitionPaths[Pair(currentAbstractState,targetState)]
                if (existingPaths != null && existingPaths.isNotEmpty())
                {
                    transitionPaths.addAll(existingPaths)
                }
                else {
                    //check if there is any edge from App State to that node
                    //TODO check for abstract state
                    val feasibleEdges = autautMF.abstractTransitionGraph.edges().filter { e ->
                        e.destination?.data!! == targetState
                    }
                    if (feasibleEdges.isNotEmpty())
                    {
                        val childParentMap = HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<WidgetGroup,String>>?>()
                        childParentMap.put(currentAbstractState, null)
                        findPathToTargetComponentByBFS(currentState = currentState
                                , root = currentAbstractState
                                , traversingNodes = listOf(Pair(first = autautMF.windowStack.clone() as Stack<WTGNode>,second = currentAbstractState))
                                , finalTarget = targetState
                                , allPaths = transitionPaths
                                , includeBackEvent = true
                                , childParentMap = childParentMap
                                , level = 0
                                , useVirtualAbstractState = true)
                    }
                }
            }
        }*/
        return transitionPaths
    }
    abstract fun getCurrentTargetEvents(currentState: State<*>):  List<AbstractAction>
    internal fun tryRandom(eContext: ExplorationContext<*, *, *>): ExplorationAction {

        val extraTask = RandomExplorationTask.getInstance(autautMF, autAutTestingStrategy,delay, useCoordinateClicks)
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
                                       , childParentMap: HashMap<AbstractState,Triple<AbstractState, AbstractInteraction,HashMap<Widget,String>>?>, level: Int, useVirtualAbstractState: Boolean
                                       , stopWhenHavingUnexercisedAction: Boolean = false) {
        if (traversingNodes.isEmpty())
            return
        val graph = autautMF.abstractTransitionGraph
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
            findPathToTargetComponentByBFS(currentState, root, nextLevelNodes, finalTarget, allPaths, includeBackEvent, childParentMap, level+1,useVirtualAbstractState,stopWhenHavingUnexercisedAction)
    }

    private fun getNextTraversingNodes(includeBackEvent: Boolean
                                       , windowStack: Stack<WTGNode>
                                       , graph: AbstractTransitionGraph
                                       , source: AbstractState, level: Int
                                       , childParentMap: HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<Widget,String>>?>
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
            } else if (backCandiates.containsKey(true) && level==0) {
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
                var foundPath = false
                if (backState == finalTarget ||
                        (stopWhenHavingUnexercisedAction &&
                                backState !is VirtualAbstractState &&
                                backState.getUnExercisedActions(null).isNotEmpty())) {
                    val fullPath = createTransitionPath(backState, root, childParentMap)
                    if (!autautMF.isDisablePath(fullPath)) {
                        allPaths.add(fullPath)
                        autautMF.registerTransitionPath(root, backState, fullPath)
                        foundPath = true
                    }
                }
                val nextWindowStack = createWindowStackForNext(windowStack, source, backState)
                nextLevelNodes.add(Pair(first = nextWindowStack, second = backState))

            }
        }
        val edgesFromSource = graph.edges(source).filter { it.destination!=null && it.source != it.destination }
        val possibleTransitions = edgesFromSource.filter {
            it.destination != null
                    && it.source != it.destination
                    && it.destination!!.data.window !is WTGFakeNode
                    && it.destination!!.data.window !is WTGOutScopeNode
                    && !(it.label.abstractAction.actionName.equals("MinimizeMaximize")
                    || it.label.abstractAction.actionName.isPressBack()
                    || it.label.abstractAction.actionName == "CloseKeyboard")
                    && includingRotateUIOrNot(it)
                    && isTheSamePrevWindow(windowStack.peek(), it)

        }
        val processedTransition = ArrayList<Edge<AbstractState, AbstractInteraction>>()
        possibleTransitions.groupBy({ it.label }, { it })
                .filterNot { level>0
                        && (it.key.abstractAction.actionName == "LaunchApp"
                        || it.key.abstractAction.actionName == "ResetApp") }
                .forEach { interaction, u ->
            val reliableTransition = u.filter { !it.label.isImplicit }
            val implicitTransitions = u.filter { it.label.isImplicit }
            if (reliableTransition.isEmpty()&&level==0) {
            //        if (reliableTransition.isEmpty()) {
                processedTransition.addAll(implicitTransitions)
            }
            processedTransition.addAll(reliableTransition)

        }

        processedTransition.forEach {
            val nextState = it.destination!!.data
            //Avoid loop
            if (!childParentMap.containsKey(nextState)) {
                val event = it.label
                val edgeCondition = graph.edgeConditions[it]?: HashMap()
                childParentMap.put(nextState, Triple(source, event,edgeCondition))
                var foundPath = false
                if (nextState == finalTarget ||
                        (stopWhenHavingUnexercisedAction &&
                                nextState !is VirtualAbstractState &&
                                nextState.getUnExercisedActions(null).isNotEmpty())) {
                    val fullGraph = createTransitionPath(nextState, root, childParentMap)
                    if (!autautMF.isDisablePath(fullGraph)) {
                        allPaths.add(fullGraph)
                        autautMF.registerTransitionPath(root, nextState, fullGraph)
                        foundPath = true
                    }
                }
                val nextWindowStack = if (event.abstractAction.actionName == "LaunchApp" || event.abstractAction.actionName == "ResetApp") {
                    Stack<WTGNode> ().also { it.push(WTGLauncherNode.getOrCreateNode()) }
                } else {
                    createWindowStackForNext(windowStack, source, nextState)
                }
                nextLevelNodes.add(Pair(first = nextWindowStack, second = nextState))
                if (!foundPath){
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
        if (autautMF.appRotationSupport)
            return true
        return it.label.abstractAction.actionName != "RotateUI"
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

    fun getPathToUnexploredState(currentState: State<*>): List<TransitionPath> {
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState == null)
            return emptyList()
        val stateGraphMF: StateGraphMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        val allUnexploredStates = HashMap<State<*>, Int>()
        stateGraphMF.getVertices().filter {!it.data.isHomeScreen}.forEach {
            val unexploreEdges = stateGraphMF.edges(it).filter { it.destination == null }
            if (unexploreEdges.size > 0) {
                allUnexploredStates.put(it.data, unexploreEdges.size)
            }
        }
        val toExploreAbstractStates = HashMap<AbstractState, Double> ()
        allUnexploredStates.forEach {
            val abstractState = AbstractStateManager.instance.getAbstractState(it.key)
            if (abstractState != null && !abstractState.isHomeScreen && !abstractState.isOutOfApplication) {
                if (!toExploreAbstractStates.containsKey(abstractState)) {
                    toExploreAbstractStates.put(abstractState,0.0)
                }
                toExploreAbstractStates[abstractState] = toExploreAbstractStates[abstractState]!! + it.value
            }
        }

        val transitionPaths = ArrayList<TransitionPath>()

        getPathToStates(transitionPaths,toExploreAbstractStates,currentAbstractState,currentState,false,false)
        return transitionPaths

    }

    fun getPathToStates(transitionPaths: ArrayList<TransitionPath>, stateByScore: Map<AbstractState, Double>, currentAbstractState: AbstractState, currentState: State<*>, stopWhenHavingUnexercisedAction: Boolean
    ,useVABS: Boolean) {
        val candidateStates = HashMap(stateByScore)
        while (transitionPaths.isEmpty() && candidateStates.isNotEmpty()) {
            /*val leastVisitedFoundNode = candidateVisitedFoundNodes.minBy { it.value }!!
            val leastVisitedCount = leastVisitedFoundNode.value
            var biasedCandidate =candidateVisitedFoundNodes.filter { it.value == leastVisitedCount }.map { it.key }.random()
//            val topTargetWindow = biasedCandidates.sortedByDescending { it.unexercisedWidgetCount }.first()
//            val targetWindows = biasedCandidates.filter { it.unexercisedWidgetCount == topTargetWindow.unexercisedWidgetCount && it!=currentAbstractState }
            candidateVisitedFoundNodes.remove(biasedCandidate)
            val abstractStates = runtimeAbstractStates.filter { it.window == biasedCandidate }*/
            while (transitionPaths.isEmpty() && candidateStates.isNotEmpty()) {
                val windowByScore = HashMap<WTGNode, Double>()
                val selectedWindow: WTGNode
                /*if (candidateStates.entries.groupBy { it.key.window }.any { it.key == currentAbstractState.window }) {
                    selectedWindow = currentAbstractState.window
                } else {
                    candidateStates.entries.groupBy { it.key.window }.forEach { w, states ->
                        val windowScore = states.sumByDouble { it.value } / states.size
                        windowByScore.put(w, windowScore)
                    }
                    val pbWindow = ProbabilityDistribution<WTGNode>(windowByScore)
                    selectedWindow = pbWindow.getRandomVariable()
                }*/
                candidateStates.entries.groupBy { it.key.window }.forEach { w, states ->
                    val windowScore = states.sumByDouble { it.value } / states.size
                    windowByScore.put(w, windowScore)
                }
                val pbWindow = ProbabilityDistribution<WTGNode>(windowByScore)
                selectedWindow = pbWindow.getRandomVariable()
                val windowStates = HashMap(candidateStates.filter { it.key.window == selectedWindow })

                //val maxActionCount = stateByScore.maxBy { it.value }!!.value
                //val abstractStates = stateByScore.filter { it.value == maxActionCount }.keys
                while (transitionPaths.isEmpty() && windowStates.isNotEmpty()) {
                    val pbState = ProbabilityDistribution<AbstractState>(windowStates)
                    val abstractState = pbState.getRandomVariable()
                    val existingPaths: List<TransitionPath>?
                    existingPaths = autautMF.allAvailableTransitionPaths[Pair(currentAbstractState, abstractState)]
                    if (existingPaths != null && existingPaths.isNotEmpty()) {
                        transitionPaths.addAll(existingPaths)
                    } else {
                        val childParentMap = HashMap<AbstractState, Pair<AbstractState, AbstractInteraction>?>()
                        childParentMap.put(currentAbstractState, null)
                        findPathToTargetComponentByBFS(currentState = currentState
                                , root = currentAbstractState
                                , traversingNodes = listOf(Pair(autautMF.windowStack.clone() as Stack<WTGNode>, currentAbstractState))
                                , finalTarget = abstractState
                                , allPaths = transitionPaths
                                , includeBackEvent = true
                                , childParentMap = HashMap()
                                , level = 0,
                                useVirtualAbstractState = useVABS
                                , stopWhenHavingUnexercisedAction = stopWhenHavingUnexercisedAction)
                    }
                    windowStates.remove(abstractState)
                    candidateStates.remove(abstractState)
                }
            }

        }
    }
}