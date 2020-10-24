package org.droidmate.exploration.modelFeatures.autaut.helper

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractTransitionGraph
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.staticModel.TransitionPath
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGDialogNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGFakeNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGLauncherNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGOptionsMenuNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGOutScopeNode
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PathFindingHelper {
    companion object {
        fun findPathToTargetComponent(autautMF: AutAutMF, currentState: State<*>, root: AbstractState, traversingNodes: List<Pair<Stack<WTGNode>, AbstractState>>, finalTarget: AbstractState
                                      , allPaths: ArrayList<TransitionPath>, includeBackEvent: Boolean
                                      , useVirtualAbstractState: Boolean
                                      , stopWhenHavingUnexercisedAction: Boolean = false
                                      , includeReset: Boolean = true
                                      , shortest: Boolean = true
                                      , pathCountLimitation: Int = 1) {
            val childParentMap = HashMap<AbstractState,
                    Triple<AbstractState,
                            AbstractInteraction,
                            ArrayList<HashMap<Widget,String>>
                            >?
                    >()
            childParentMap.put(root, null)
            findPathToTargetComponentByBFS(
                    autautMF = autautMF,
                    currentState = currentState,
                    root = root,
                    traversingNodes =  traversingNodes,
                    finalTarget =  finalTarget,
                    allPaths =  allPaths,
                    includeBackEvent =  includeBackEvent,
                    childParentMap =  childParentMap,
                    depth = 0,
                    useVirtualAbstractState =  false,
                    stopWhenHavingUnexercisedAction = stopWhenHavingUnexercisedAction,
                    shortest = shortest,
                    pathCountLimitation = pathCountLimitation,
                    considerResetOrLaunchAction = false,
                    includeImplicitInteraction = true)
            if (allPaths.isEmpty() && includeReset) {
                childParentMap.clear()
                childParentMap.put(root, null)
                findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        traversingNodes =  traversingNodes,
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  includeBackEvent,
                        childParentMap =  childParentMap,
                        depth = 0,
                        useVirtualAbstractState =  false,
                        stopWhenHavingUnexercisedAction = stopWhenHavingUnexercisedAction,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        considerResetOrLaunchAction = true,
                        includeImplicitInteraction = true)
            }
            if (allPaths.isEmpty() && useVirtualAbstractState) {
                childParentMap.clear()
                childParentMap.put(root, null)
                findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        traversingNodes =  traversingNodes,
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  includeBackEvent,
                        childParentMap =  childParentMap,
                        depth = 0,
                        useVirtualAbstractState =  true,
                        stopWhenHavingUnexercisedAction = stopWhenHavingUnexercisedAction,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        considerResetOrLaunchAction = includeReset,
                        includeImplicitInteraction = true)
            }
        }

        fun findPathToTargetComponentByBFS(autautMF: AutAutMF,currentState: State<*>, root: AbstractState,
                                           traversingNodes: List<Pair<Stack<WTGNode>, AbstractState>>,
                                           finalTarget: AbstractState, allPaths: ArrayList<TransitionPath>,
                                           childParentMap: HashMap<AbstractState,Triple<AbstractState, AbstractInteraction,ArrayList<HashMap<Widget,String>>>?>,
                                           depth: Int,
                                           useVirtualAbstractState: Boolean,
                                           stopWhenHavingUnexercisedAction: Boolean = false,
                                           shortest: Boolean = true,
                                           pathCountLimitation: Int = 1,
                                           considerResetOrLaunchAction: Boolean,
                                           includeBackEvent: Boolean,
                                           includeImplicitInteraction: Boolean
        ) {
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
                getNextTraversingNodes(
                        autautMF = autautMF,
                        includeBackEvent =  includeBackEvent,
                        windowStack =  windowStack,
                        graph =  graph,
                        source =  source,
                        depth =  depth,
                        childParentMap =  childParentMap,
                        finalTarget =  finalTarget,
                        root =  root,
                        allPaths =  allPaths,
                        nextLevelNodes =  nextLevelNodes,
                        stopWhenHavingUnexercisedAction =  stopWhenHavingUnexercisedAction,
                        includeWTG =  useVirtualAbstractState,
                        includeResetAction =  considerResetOrLaunchAction,
                        includeImplicitInteraction = includeImplicitInteraction)
            }
            /*if (nextLevelNodes.isEmpty() && !useVirtualAbstractState) {
                for (traversing in traversingNodes)
                {
                    val source = traversing.second
                    val windowStack = traversing.first
                    if (windowStack.isEmpty())
                        continue
                    getNextTraversingNodes(includeBackEvent, windowStack, graph, source, depth, childParentMap, finalTarget, root, allPaths, nextLevelNodes,stopWhenHavingUnexercisedAction)
                }
            }*/
            if (allPaths.size > pathCountLimitation && !shortest) {
                return
            }
            if (allPaths.isEmpty() || (allPaths.size <= pathCountLimitation && !shortest) )
                findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        traversingNodes =  nextLevelNodes,
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent = includeBackEvent,
                        childParentMap =  childParentMap,
                        depth =  depth+1,
                        useVirtualAbstractState =  useVirtualAbstractState,
                        stopWhenHavingUnexercisedAction =  stopWhenHavingUnexercisedAction,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        considerResetOrLaunchAction = considerResetOrLaunchAction,
                        includeImplicitInteraction = includeImplicitInteraction)
        }

        private fun getNextTraversingNodes(autautMF: AutAutMF, windowStack: Stack<WTGNode>, graph: AbstractTransitionGraph, source: AbstractState,
                                           depth: Int,
                                           childParentMap: HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,ArrayList<HashMap<Widget,String>>>?>,
                                           finalTarget: AbstractState, root: AbstractState,
                                           allPaths: ArrayList<TransitionPath>, nextLevelNodes: ArrayList<Pair<Stack<WTGNode>, AbstractState>>,
                                           includeWTG: Boolean,
                                           stopWhenHavingUnexercisedAction: Boolean,
                                           includeImplicitInteraction: Boolean,
                                           includeResetAction: Boolean,
                                           includeBackEvent: Boolean) {

            if (includeBackEvent && windowStack.isNotEmpty()) {
                val processingBackEdges = ArrayList<Edge<AbstractState, AbstractInteraction>>()
                val backEdges = graph.edges(source).filter {
                    it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK
                            && it.destination != null
                }

                val backCandiates = backEdges.filter {
                    it.destination!!.data.window !is WTGOutScopeNode
                            && it.destination!!.data.window !is WTGLauncherNode
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
                } else if (backCandiates.containsKey(true) && depth==0) {
                    backCandiates[true]!!.forEach {
                        if (!childParentMap.containsKey(it.destination!!.data)) {
                            processingBackEdges.add(it)
                        }
                    }
                }

                processingBackEdges.forEach { edge ->
                    val backEvent = edge.label
                    val backState = edge.destination!!.data
                    val edgeCondition = graph.edgeConditions[edge]!!
                    childParentMap.put(backState, Triple(source, backEvent,edgeCondition))
                    if (isArrived(backState, finalTarget, stopWhenHavingUnexercisedAction,includeWTG)) {
                        val fullPath = createTransitionPath(autautMF, backState, root, childParentMap)
                        if (!autautMF.isDisablePath(fullPath)) {
                            allPaths.add(fullPath)
                            autautMF.registerTransitionPath(root, backState, fullPath)
                        }
                    }
                    val nextWindowStack = createWindowStackForNext(windowStack, source, backState)
                    nextLevelNodes.add(Pair(first = nextWindowStack, second = backState))
                }
            }

            val edgesFromSource = graph.edges(source).filter { it.destination!=null && it.source != it.destination }
            val forwardTransitions = edgesFromSource.filter {
                it.destination != null
                        //&& it.source != it.destination
                        && it.destination!!.data.window !is WTGFakeNode
                        && !(it.label.abstractAction.actionType == AbstractActionType.MINIMIZE_MAXIMIZE
                        || it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK)
                        && includingRotateUIOrNot(autautMF, it)
                        && includingResetOrNot(it, includeResetAction,depth)
                        && includingWTGOrNot(it, includeWTG)
                        && includingLaunchOrNot(it, includeBackEvent,depth)
                /*&& isTheSamePrevWindow(windowStack.peek(), it)*/
            }

            val possibleTransitions = ArrayList<Edge<AbstractState, AbstractInteraction>>()
            forwardTransitions.groupBy { it.label.abstractAction }.forEach { action, edges ->
                if (action.isLaunchOrReset()) {
                    possibleTransitions.addAll(edges)
                } else if (edges.groupBy { it.destination }.size > 1) {
                    // check if the same prev but differ destination
                    if (edges.groupBy { it.label.prevWindow }.size > 1) {
                        edges.groupBy { it.label.prevWindow }.forEach { prevWindow, edges ->
                            if (prevWindow == null && includeWTG){
                                possibleTransitions.addAll(edges)
                            } else if (edges.groupBy { it.destination }.size > 1) {
                                possibleTransitions.addAll(edges)
                            } else {
                                var count = 0
                                edges.forEach {
                                    if (isTheSamePrevWindow(windowStack.peek(),it)) {
                                        possibleTransitions.add(it)
                                        count++
                                    }
                                }
                                if (count == 0) {
                                    possibleTransitions.addAll(edges)
                                }
                            }
                        }
                     } else {
                        possibleTransitions.addAll(edges)
                    }
                } else {
                    // different prevWindow but the same destination
                    possibleTransitions.addAll(edges)
                }

            }
            val processedTransition = ArrayList<Edge<AbstractState, AbstractInteraction>>()
            possibleTransitions.groupBy({ it.label.abstractAction }, { it })
                    .forEach { interaction, u ->
                        val reliableTransition = u.filter { !it.label.isImplicit }
                        val implicitTransitions = u.filter { it.label.isImplicit }
                        if (reliableTransition.isEmpty()) {
                            //        if (reliableTransition.isEmpty()) {
                            if (includeImplicitInteraction == true)
                                processedTransition.addAll(implicitTransitions)
                            else if (depth == 0)
                                processedTransition.addAll(implicitTransitions)
                        }
                        processedTransition.addAll(reliableTransition)

                    }

            processedTransition.forEach {
                val nextState = it.destination!!.data
                //Avoid loop
                if (!childParentMap.containsKey(nextState)) {
                    val event = it.label
                    val edgeCondition = graph.edgeConditions[it]!!
                    childParentMap.put(nextState, Triple(source, event, edgeCondition))
                    val fullGraph = createTransitionPath(autautMF, nextState, root, childParentMap)
                    var foundPath = false
                    if (!autautMF.isDisablePath(fullGraph)) {
                        if (isArrived(nextState, finalTarget, stopWhenHavingUnexercisedAction,includeWTG)) {
                            allPaths.add(fullGraph)
                            autautMF.registerTransitionPath(root, nextState, fullGraph)
                            foundPath = true
                        }
                        val nextWindowStack = if (event.abstractAction.isLaunchOrReset()) {
                            Stack<WTGNode>().also { it.push(WTGLauncherNode.getOrCreateNode()) }
                        } else {
                            createWindowStackForNext(windowStack, source, nextState)
                        }
                        nextLevelNodes.add(Pair(first = nextWindowStack, second = nextState))
                    }
                }
            }
        }

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
            if (useVirtualAbstractState && nextState.window == finalTarget.window)
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

        private fun includingRotateUIOrNot(autautMF: AutAutMF, it: Edge<AbstractState, AbstractInteraction>): Boolean {
            if (autautMF.appRotationSupport)
                return true
            return it.label.abstractAction.actionType != AbstractActionType.ROTATE_UI
        }

        private fun isTheSamePrevWindow(prevWindow: WTGNode?, it: Edge<AbstractState, AbstractInteraction>): Boolean {
            return (
                    (prevWindow != null && it.label.prevWindow == prevWindow)
                            || prevWindow == null || it.label.prevWindow == null)
        }

        private fun createTransitionPath(autautMF: AutAutMF, finalTarget: AbstractState, startingNode: AbstractState, childParentMap: HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,ArrayList<HashMap<Widget,String>>>?>): TransitionPath {
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

    }
}