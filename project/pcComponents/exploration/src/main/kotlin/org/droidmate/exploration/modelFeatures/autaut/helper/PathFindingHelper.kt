package org.droidmate.exploration.modelFeatures.autaut.helper

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractTransitionGraph
import org.droidmate.exploration.modelFeatures.autaut.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.WTG.TransitionPath
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.FakeWindow
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PathFindingHelper {
    companion object {
        val allAvailableTransitionPaths =  HashMap<Pair<AbstractState,AbstractState>,ArrayList<TransitionPath>>()
        private val disableEdges = HashSet<Edge<AbstractState, AbstractTransition>>()
        private val disablePaths = HashSet<List<Edge<AbstractState,AbstractTransition>>>()

        fun findPathToTargetComponent(autautMF: AutAutMF, currentState: State<*>, root: AbstractState, traversingNodes: List<Pair<Stack<Window>, AbstractState>>, finalTarget: AbstractState
                                      , allPaths: ArrayList<TransitionPath>
                                      , includeBackEvent: Boolean
                                      , includingWTG: Boolean
                                      , stopWhenHavingUnexercisedAction: Boolean = false
                                      , includeReset: Boolean = true
                                      , shortest: Boolean = true
                                      , pathCountLimitation: Int = 1
                                      , forcingExplicit: Boolean = false) {
            val existingPaths = allAvailableTransitionPaths.get(Pair(root,finalTarget))
            if (existingPaths!= null && existingPaths.isNotEmpty()) {
               val satisfiedPaths = existingPaths.filter {
                   (includeBackEvent || it.edges().all { it.label.abstractAction.actionType!=AbstractActionType.PRESS_BACK })
                           && (!forcingExplicit || it.edges().all { it.label.isExplicit() })
                           && (includeReset || it.edges().all { it.label.abstractAction.actionType != AbstractActionType.RESET_APP })
                           && (includingWTG || it.edges().all { !it.label.fromWTG })
               }
                if (satisfiedPaths.isNotEmpty()) {
                    allPaths.addAll(satisfiedPaths)
                    return
                }
            }
            val childParentMap = HashMap<AbstractState,
                    Triple<AbstractState,
                            AbstractTransition,
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
                    includeImplicitInteraction = !forcingExplicit,
                    includeLaunchAction = false)
            if (allPaths.isEmpty()) {
                // consider Launch Action
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
                        considerResetOrLaunchAction = false,
                        includeImplicitInteraction = !forcingExplicit,
                        includeLaunchAction = true)
            }
            if (allPaths.isEmpty() && includeReset) {
                // consider reset actions
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
                        includeImplicitInteraction = false,
                        includeLaunchAction = true)
            }
            if (allPaths.isEmpty() && includingWTG && !forcingExplicit) {
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
                        considerResetOrLaunchAction = false,
                        includeImplicitInteraction = !forcingExplicit,
                        includeLaunchAction = false)
            }
            if (allPaths.isEmpty() && includingWTG && includeReset && forcingExplicit) {
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
                        includeImplicitInteraction = false,
                        includeLaunchAction = true)
            }

        }

        fun findPathToTargetComponentByBFS(autautMF: AutAutMF, currentState: State<*>, root: AbstractState,
                                           traversingNodes: List<Pair<Stack<Window>, AbstractState>>,
                                           finalTarget: AbstractState, allPaths: ArrayList<TransitionPath>,
                                           childParentMap: HashMap<AbstractState,Triple<AbstractState, AbstractTransition,ArrayList<HashMap<Widget,String>>>?>,
                                           depth: Int,
                                           useVirtualAbstractState: Boolean,
                                           stopWhenHavingUnexercisedAction: Boolean = false,
                                           shortest: Boolean = true,
                                           pathCountLimitation: Int = 1,
                                           considerResetOrLaunchAction: Boolean,//TODO rename
                                           includeBackEvent: Boolean,
                                           includeImplicitInteraction: Boolean,
                                           includeLaunchAction: Boolean
        ) {
            if (traversingNodes.isEmpty())
                return
            val graph = autautMF.abstractTransitionGraph
            val nextLevelNodes = ArrayList<Pair<Stack<Window>,AbstractState>>()
            for (traversing in traversingNodes)
            {

                val source = traversing.second
                if (source.window is Launcher)
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
                        includeImplicitInteraction = includeImplicitInteraction,
                        includeLauchAction = includeLaunchAction)
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
                        includeImplicitInteraction = includeImplicitInteraction,
                        includeLaunchAction = includeLaunchAction)
        }

        private fun getNextTraversingNodes(autautMF: AutAutMF, windowStack: Stack<Window>, graph: AbstractTransitionGraph, source: AbstractState,
                                           depth: Int,
                                           childParentMap: HashMap<AbstractState, Triple<AbstractState, AbstractTransition,ArrayList<HashMap<Widget,String>>>?>,
                                           finalTarget: AbstractState, root: AbstractState,
                                           allPaths: ArrayList<TransitionPath>, nextLevelNodes: ArrayList<Pair<Stack<Window>, AbstractState>>,
                                           includeWTG: Boolean,
                                           stopWhenHavingUnexercisedAction: Boolean,
                                           includeImplicitInteraction: Boolean,
                                           includeResetAction: Boolean,
                                           includeBackEvent: Boolean,
                                           includeLauchAction: Boolean) {

            if (includeBackEvent && windowStack.isNotEmpty()) {
                val processingBackEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
                val backEdges = graph.edges(source).filter {
                    it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK
                            && it.destination != null
                }

                val backCandiates = backEdges.filter {
                    it.destination!!.data.window !is OutOfApp
                            && it.destination!!.data.window !is Launcher
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
                    val edgeCondition = graph.edgeConditions[edge]!!
                    childParentMap.put(backState, Triple(source, backEvent,edgeCondition))
                    if (isArrived(edge.label, backState, finalTarget, stopWhenHavingUnexercisedAction,includeWTG)) {
                        val fullPath = createTransitionPath(autautMF, backState, root, childParentMap)
                        if (!isDisablePath(fullPath)) {
                            allPaths.add(fullPath)
                            registerTransitionPath(root, backState, fullPath)
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
                        && it.destination!!.data.window !is FakeWindow
                        && it.label.abstractAction.actionType != AbstractActionType.PRESS_BACK
                        && includingRotateUIOrNot(autautMF, it)
                        && includingResetOrNot(it, includeResetAction,depth)
                        && includingWTGOrNot(it, includeWTG)
                        && includingLaunchOrNot(it, includeLauchAction,depth)
                /*&& isTheSamePrevWindow(windowStack.peek(), it)*/
            }

            val possibleTransitions = ArrayList<Edge<AbstractState, AbstractTransition>>()
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
            val processedTransition = ArrayList<Edge<AbstractState, AbstractTransition>>()
            possibleTransitions.groupBy({ it.label.abstractAction }, { it })
                    .forEach { interaction, u ->
                        if (interaction.isLaunchOrReset())
                            processedTransition.addAll(u)
                        else {
                            val reliableTransition = u.filter { !it.label.isImplicit }
                            val implicitTransitions = u.filter { it.label.isImplicit }
                            if (reliableTransition.isEmpty()) {
                                //        if (reliableTransition.isEmpty()) {
                                if (includeImplicitInteraction == true && depth == 0)
                                    processedTransition.addAll(implicitTransitions)
                                else if (includeWTG)
                                    processedTransition.addAll(implicitTransitions.filter { it.label.fromWTG })
                            }
                            processedTransition.addAll(reliableTransition)
                        }
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
                    if (!isDisablePath(fullGraph)) {
                        if (isArrived(it.label, nextState, finalTarget, stopWhenHavingUnexercisedAction,includeWTG)) {
                            allPaths.add(fullGraph)
                            registerTransitionPath(root, nextState, fullGraph)
                            foundPath = true
                        }
                        val nextWindowStack = if (event.abstractAction.isLaunchOrReset()) {
                            Stack<Window>().also { it.push(Launcher.getOrCreateNode()) }
                        } else {
                            createWindowStackForNext(windowStack, source, nextState)
                        }
                        nextLevelNodes.add(Pair(first = nextWindowStack, second = nextState))
                    }
                }
            }
        }

        private fun includingWTGOrNot(edge: Edge<AbstractState, AbstractTransition>, includeWTG: Boolean): Boolean {
            if (includeWTG)
                return true
            if (edge.label.fromWTG)
                return false
            return true
        }

        private fun isArrived(abstractTransition: AbstractTransition, nextState: AbstractState, finalTarget: AbstractState, stopWhenHavingUnexercisedAction: Boolean, useVirtualAbstractState: Boolean): Boolean {
            if (nextState == finalTarget){
                if (abstractTransition.isExplicit())
                    return true
                if (useVirtualAbstractState)
                    return true
            }

            if (stopWhenHavingUnexercisedAction &&
                    nextState !is VirtualAbstractState &&
                    nextState.getUnExercisedActions(null).isNotEmpty())
                return true
            if (useVirtualAbstractState && nextState.window == finalTarget.window)
                return true
            return false
        }

        private fun includingResetOrNot(it: Edge<AbstractState, AbstractTransition>, includeReset: Boolean, depth: Int): Boolean {
            if (includeReset && depth == 0) {
                if (it.label.abstractAction.actionType==AbstractActionType.RESET_APP) {
                    return true
                }
                return false
            }
            else
                return it.label.abstractAction.actionType!=AbstractActionType.RESET_APP
        }

        private fun includingLaunchOrNot(it: Edge<AbstractState, AbstractTransition>, includeLaunch: Boolean, depth: Int): Boolean {
            if (includeLaunch && depth == 0) {
                return true
            }
            else
                return it.label.abstractAction.actionType!=AbstractActionType.LAUNCH_APP
        }

        private fun createWindowStackForNext(windowStack: Stack<Window>, prevState: AbstractState, nextState: AbstractState): Stack<Window> {
            val nextWindowStack = windowStack.clone() as Stack<Window>
            if (nextWindowStack.contains(nextState.window) && nextWindowStack.size > 1) {
                // Return to the prev window
                // Pop the window
                while (nextWindowStack.pop() != nextState.window) {

                }
            } else {
                if (nextState.window != prevState.window) {
                    if (prevState.window !is Dialog && prevState.window !is OptionsMenu) {
                        nextWindowStack.push(prevState.window)
                    }
                } else if (nextState.isOpeningKeyboard) {
                    nextWindowStack.push(nextState.window)
                }
            }
            return nextWindowStack
        }

        private fun backToLauncherOrNot(it: Edge<AbstractState, AbstractTransition>) =
                it.destination!!.data.window !is Launcher

        private fun includingRotateUIOrNot(autautMF: AutAutMF, it: Edge<AbstractState, AbstractTransition>): Boolean {
            if (autautMF.appRotationSupport)
                return true
            return it.label.abstractAction.actionType != AbstractActionType.ROTATE_UI
        }

        private fun isTheSamePrevWindow(prevWindow: Window?, it: Edge<AbstractState, AbstractTransition>): Boolean {
            return (
                    (prevWindow != null && it.label.prevWindow == prevWindow)
                            || prevWindow == null || it.label.prevWindow == null)
        }

        private fun createTransitionPath(autautMF: AutAutMF, finalTarget: AbstractState, startingNode: AbstractState, childParentMap: HashMap<AbstractState, Triple<AbstractState, AbstractTransition,ArrayList<HashMap<Widget,String>>>?>): TransitionPath {
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
        fun registerTransitionPath(source: AbstractState, destination: AbstractState, fullPath: TransitionPath) {
            if (!allAvailableTransitionPaths.containsKey(Pair(source, destination)))
                allAvailableTransitionPaths.put(Pair(source, destination), ArrayList())
            allAvailableTransitionPaths[Pair(source, destination)]!!.add(fullPath)
        }
        val unsuccessfulInteractions = HashMap<AbstractTransition, Int>()

        fun addDisablePathFromState(transitionPath: TransitionPath, edge: Edge<AbstractState,AbstractTransition>, currentAbstractState: AbstractState){
           /* val abstractInteraction = edge.label
            if (abstractInteraction.abstractAction.isItemAction())
            {
                if (!unsuccessfulInteractions.containsKey(abstractInteraction)) {
                    unsuccessfulInteractions.put(abstractInteraction,0)
                }
                if (unsuccessfulInteractions[abstractInteraction]!! < 5) {
                    unsuccessfulInteractions[abstractInteraction] = unsuccessfulInteractions[abstractInteraction]!!+1
                    return
                }
            }*/
            //abstractTransitionGraph.remove(edge)


            val expectedAbstractState = edge.destination?.data!!
            val corruptedEdge = if (currentAbstractState.window != expectedAbstractState.window) {
                edge
            } else if (expectedAbstractState !is VirtualAbstractState && currentAbstractState.isOpeningKeyboard != expectedAbstractState.isOpeningKeyboard) {
                edge
            } else {
                transitionPath.edges(expectedAbstractState!!).firstOrNull()
            }
            if (corruptedEdge == null)
                return
            /* if (corruptedEdge.label.fromWTG) {
                 disableEdges.add(corruptedEdge)
             }*/

            val disablePath = ArrayList<Edge<AbstractState,AbstractTransition>>()
            var abstrateState: AbstractState? = transitionPath.root.data
            while (abstrateState!=null) {
                val edge = transitionPath.edges(abstrateState).firstOrNull()
                if (edge == null) {
                    break
                }
                disablePath.add(edge)
                if (edge.label.abstractAction.isLaunchOrReset()) {
                    disablePath.clear()
                }
                if (edge == corruptedEdge) {
                    break
                }
                abstrateState = edge.destination?.data
            }
            /*if (disablePath.size == 1 && disablePath.all { it.label.fromWTG }) {

            }*/
            if (disablePath.isNotEmpty())
                disablePaths.add(disablePath)
            /*if (edge.label.abstractAction.actionName.isPressBack()) {
                abstractTransitionGraph.edges(edge.source.data).filter {
                    it.label.abstractAction == edge.label.abstractAction
                            && it.destination?.data?.window == edge.destination?.data?.window
                            && it.label.prevWindow == edge.label.prevWindow
                }.forEach {
                    abstractTransitionGraph.remove(it)
                }
            }*/


            val root = transitionPath.root.data
            val destination = transitionPath.getFinalDestination()
            unregisteredTransitionPath(root, destination, transitionPath)
            //log.debug("Disable edges count: ${disableEdges.size}")

        }

        private fun unregisteredTransitionPath(root: AbstractState, destination: AbstractState, transitionPath: TransitionPath) {
            if (allAvailableTransitionPaths.containsKey(Pair(root, destination))) {
                val existingPaths = allAvailableTransitionPaths[Pair(root, destination)]!!
                if (existingPaths.contains(transitionPath)) {
                    existingPaths.remove(transitionPath)
                }
            }
        }

        fun checkIsDisableEdge(edge: Edge<AbstractState, AbstractTransition>): Boolean {
            if (disableEdges.contains(edge)) {
                return true
            } else
                return false
        }

        fun isDisablePath(path: TransitionPath): Boolean {
            disablePaths.forEach {
                var matched = false
                if (path.edges().any { it.label.abstractAction.actionType == AbstractActionType.RESET_APP }) {
                    matched = path.edges().filterNot { it.label.abstractAction.actionType == AbstractActionType.RESET_APP }.containsAll(it)
                } else
                    matched = path.edges().containsAll(it)
                if (matched)
                    return true
            }
            disableEdges.forEach {
                var matched = false
                matched = path.edges().contains(it)
                if (matched)
                    return true
            }
            /*if (path.edges().any { disableEdges.contains(it) }) {
                return true
            }*/
            return false
        }
    }
}