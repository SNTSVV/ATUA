package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.strategy.autaut.task.AbstractStrategyTask
import org.droidmate.exploration.strategy.autaut.task.RandomExplorationTask
import org.droidmate.explorationModel.interaction.State

abstract class AbstractPhaseStrategy(
        val regressionTestingStrategy: RegressionTestingStrategy,
        val budgetScale: Double,
        val delay: Long,
        val useCoordinateClicks: Boolean
) {
    lateinit var phaseState: PhaseState
    lateinit var regressionTestingMF: RegressionTestingMF

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
                val feasibleEdges = regressionTestingMF.abstractTransitionGraph.edges().filter {e ->
                    e.destination?.data!!.window == targetWindow
                }
                if (feasibleEdges.isNotEmpty())
                {
                    val childParentMap = HashMap<AbstractState, Pair<AbstractState, AbstractInteraction>?>()
                    childParentMap.put(currentAbstractState, null)
                    findPathToTargetComponentByBFS(currentState = currentState
                            , root = currentAbstractState
                            , traversingNodes = listOf(Pair(first = regressionTestingMF.windowStack.peek(),second = currentAbstractState))
                            , finalTarget = targetState
                            , allPaths = transitionPaths
                            , includeBackEvent = true
                            , childParentMap = childParentMap
                            , level = 0)
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
            return ExplorationAction.closeAndReturn()
        } else {
            val action2 = extraTask.chooseAction(eContext.getCurrentState())
            if (action2 != null)
                return action2
            else
                return ExplorationAction.closeAndReturn()
        }
    }
    abstract fun isEnd(): Boolean

    fun findPathToTargetComponentByBFS(currentState: State<*>, root: AbstractState, traversingNodes: List<Pair<WTGNode, AbstractState>>, finalTarget: AbstractState
                                       , allPaths: ArrayList<TransitionPath>, includeBackEvent: Boolean
                                       , childParentMap: HashMap<AbstractState,Pair<AbstractState, AbstractInteraction>?>, level: Int) {
        if (traversingNodes.isEmpty())
            return
        val graph = regressionTestingMF.abstractTransitionGraph
        val nextLevelNodes = ArrayList<Pair<WTGNode,AbstractState>>()
        for (traversing in traversingNodes)
        {
            val source = traversing.second
            val prevWindow = traversing.first
            if (includeBackEvent)
            {
                val backNodes = ArrayList<AbstractState>()
                val processingBackEdges = ArrayList<Edge<AbstractState, AbstractInteraction>>()
                val backCandiates = graph.edges(source).filter {
                    it.label.abstractAction.actionName.isPressBack()
                            && it.destination!=null
                            && backToLauncherOrNot(it,level)
                            && it.destination!!.data.window !is WTGOutScopeNode
                            && it.label.prevWindow != null
                            && isTheSamePrevWindow(prevWindow, it)
                }.groupBy { it.label.isImplicit }

                if (backCandiates.containsKey(true)) {
                    backCandiates[true]!!.forEach {
                        if (!childParentMap.containsKey(it.destination!!.data)) {
                            processingBackEdges.add(it)
                        }
                    }
                } else if (backCandiates.containsKey(false)){
                    backCandiates[false]!!.forEach {
                        if (!childParentMap.containsKey(it.destination!!.data)) {
                            processingBackEdges.add(it)
                        }
                    }
                }

                processingBackEdges.forEach { edge ->
                    val backEvent = edge.label
                    val backState = edge.destination!!.data
                    childParentMap.put(backState,Pair(source,backEvent))
                    if (backState == finalTarget) {
                        val fullPath = createTransitionPath(backState, root,childParentMap)
                        allPaths.add(fullPath)
                        regressionTestingMF.registerTransitionPath(root, finalTarget, fullPath)
                        return
                    } else {
                        if(source.window != backState.window) {
                            nextLevelNodes.add(Pair(first = source.window, second = backState))
                        } else {
                            // keep current prevWindow
                            nextLevelNodes.add(Pair(first = prevWindow, second = backState ))
                        }
                    }
                }
            }
            val possibleTransitions = graph.edges(source).filter{
                it.destination!=null
                        && it.source != it.destination
                        && it.destination!!.data.window !is WTGOutScopeNode
                        && it.destination!!.data.window !is WTGFakeNode
                        && it.destination!!.data.window !is WTGOpeningKeyboardNode
                        && !it.label.abstractAction.actionName.equals("MinimizeMaximize")
                        && isTheSamePrevWindow(prevWindow,it)
            }
            val processedTransition = ArrayList<Edge<AbstractState, AbstractInteraction>>()
            possibleTransitions.filter {
                !it.label.abstractAction.actionName.isPressBack()
                        && includingRotateUIOrNot(it) && isTheSamePrevWindow(prevWindow,it)
            }.groupBy({it.label.abstractAction},{it}).forEach { _, u ->
                val reliableTransition =  u.filter { !it.label.isImplicit || it.label.prevWindow == null }
                val implicitTransitions = u.filter { it.label.isImplicit }
                if (reliableTransition.isEmpty())
                {
                    processedTransition.addAll(implicitTransitions)
                }
                processedTransition.addAll(reliableTransition)

            }
            // if (proc)
            processedTransition.forEach {
                val nextNode = it.destination!!.data
                //Avoid loop
                if (!childParentMap.containsKey(nextNode))
                {
                    val event = it.label
                    var isDisableEdge: Boolean = false
                    //It is the init node and the disableEdges contains it
                    //isDisableEdge = checkIsDisableEdge(nextNode, event, source)
                    if (!isDisableEdge)
                    {
                        childParentMap.put(nextNode,Pair(source,event))
                        if (nextNode == finalTarget)
                        {
                            val fullGraph = createTransitionPath(finalTarget,root,childParentMap)
                            allPaths.add(fullGraph)
                            regressionTestingMF.registerTransitionPath(root,finalTarget,fullGraph)
                            return
                        }
                        if (source.window != nextNode.window) {
                            nextLevelNodes.add(Pair(first = source.window,second=nextNode))
                        } else {
                            // keep current prevWindow
                            nextLevelNodes.add(Pair(first = prevWindow,second=nextNode))
                        }

                    }
                }
            }
        }

        findPathToTargetComponentByBFS(currentState, root, nextLevelNodes, finalTarget, allPaths, includeBackEvent, childParentMap, level+1)
    }

    private fun backToLauncherOrNot(it: Edge<AbstractState, AbstractInteraction>, depth: Int) =
            it.destination!!.data.window !is WTGLauncherNode || depth == 0

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

    private fun createTransitionPath(finalTarget: AbstractState, startingNode: AbstractState, childParentMap: HashMap<AbstractState, Pair<AbstractState, AbstractInteraction>?>): TransitionPath {
        val fullPath = TransitionPath(startingNode)
        var backwardNode = finalTarget
        while (backwardNode!=startingNode)
        {
            val source = childParentMap[backwardNode]!!.first
            val event = childParentMap[backwardNode]!!.second
            val edge = fullPath.add(source,backwardNode,event)
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

}