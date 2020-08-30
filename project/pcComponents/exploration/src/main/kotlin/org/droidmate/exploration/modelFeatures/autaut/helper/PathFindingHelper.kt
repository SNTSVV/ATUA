package org.droidmate.exploration.modelFeatures.autaut.helper

import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractTransitionGraph
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.WidgetGroup
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
        fun findPathToTargetComponentByBFS(currentState: State<*>, root: AbstractState, traversingNodes: List<Pair<Stack<WTGNode>, AbstractState>>, finalTarget: AbstractState
                                           , allPaths: ArrayList<TransitionPath>, includeBackEvent: Boolean
                                           , childParentMap: HashMap<AbstractState,Triple<AbstractState, AbstractInteraction,HashMap<Widget,String>>?>, level: Int, useVirtualAbstractState: Boolean
                                           , stopWhenHavingUnexercisedAction: Boolean = false
        , abstractTransitionGraph: AbstractTransitionGraph) {
            if (traversingNodes.isEmpty())
                return
            val graph = abstractTransitionGraph
            val nextLevelNodes = ArrayList<Pair<Stack<WTGNode>, AbstractState>>()
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
                findPathToTargetComponentByBFS(currentState
                        , root
                        , nextLevelNodes
                        , finalTarget
                        , allPaths
                        , includeBackEvent
                        , childParentMap
                        , level+1
                        ,useVirtualAbstractState
                        ,stopWhenHavingUnexercisedAction
                        ,graph)
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
                    if (backState == finalTarget ||
                            (stopWhenHavingUnexercisedAction &&
                                    backState !is VirtualAbstractState &&
                                    backState.getUnExercisedActions(null).isNotEmpty())) {
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
                        && it.destination!!.data.window !is WTGOutScopeNode
                        && !(it.label.abstractAction.actionName.equals("MinimizeMaximize")
                        || it.label.abstractAction.actionName.isPressBack()
                        || it.label.abstractAction.actionName == "CloseKeyboard")
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
                    if (nextState == finalTarget ||
                            (stopWhenHavingUnexercisedAction &&
                                    nextState !is VirtualAbstractState &&
                                    nextState.getUnExercisedActions(null).isNotEmpty())) {

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


        fun isTheSamePrevWindow(prevWindow: WTGNode?, it: Edge<AbstractState, AbstractInteraction>): Boolean {
            return (
                    (prevWindow != null && it.label.prevWindow == prevWindow)
                            || prevWindow == null || it.label.prevWindow == null)
        }

        private fun backToLauncherOrNot(it: Edge<AbstractState, AbstractInteraction>) =
                it.destination!!.data.window !is WTGLauncherNode

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

        private fun createTransitionPath(finalTarget: AbstractState
                                         , startingNode: AbstractState
                                         , childParentMap: HashMap<AbstractState
                        , Triple<AbstractState, AbstractInteraction,HashMap<WidgetGroup,String>>?>
        , abstractTransitionGraph: AbstractTransitionGraph): TransitionPath {
            val fullPath = TransitionPath(startingNode)
            var backwardNode = finalTarget
            while (backwardNode!=startingNode)
            {
                val source = childParentMap[backwardNode]!!.first
                val event = childParentMap[backwardNode]!!.second
                val edge = fullPath.add(source,backwardNode,event)
                //fullPath.edgeConditions[edge] = childParentMap[backwardNode]!!.third
                val graphEdge = abstractTransitionGraph.edge(source,backwardNode,event)
                if (graphEdge!=null && abstractTransitionGraph.edgeConditions.containsKey(graphEdge))
                {
                    fullPath.edgeConditions.put(edge,abstractTransitionGraph.edgeConditions[graphEdge]!!)
                }
                backwardNode = source
            }
            return fullPath
        }

    }
}