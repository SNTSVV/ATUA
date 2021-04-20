package org.droidmate.exploration.modelFeatures.atua.helper

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransitionGraph
import org.droidmate.exploration.modelFeatures.atua.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.EWTG.PathTraverser
import org.droidmate.exploration.modelFeatures.atua.EWTG.TransitionPath
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.FakeWindow
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.explorationModel.interaction.State
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PathFindingHelper {
    companion object {
        val allAvailableTransitionPaths =  HashMap<Pair<AbstractState,AbstractState>,ArrayList<TransitionPath>>()
        private val disableEdges = HashSet<Edge<AbstractState, AbstractTransition>>()
        private val disablePaths = HashSet<List<AbstractTransition>>()

        fun findPathToTargetComponent(autautMF: ATUAMF, currentState: State<*>, root: AbstractState, finalTarget: AbstractState
                                      , allPaths: ArrayList<TransitionPath>
                                      , shortest: Boolean = true
                                      , pathCountLimitation: Int = 1
                                      , pathType: PathType) {
            val existingPaths = allAvailableTransitionPaths.get(Pair(root,finalTarget))
            if (existingPaths!= null && existingPaths.isNotEmpty()) {
               val satisfiedPaths = existingPaths.filter {
                            it.pathType == pathType
               }
                if (satisfiedPaths.isNotEmpty()) {
                    allPaths.addAll(satisfiedPaths)
                    if  (shortest || allPaths.size >= pathCountLimitation) {
                        return
                    }
                }
            }
            val pathTracking = HashMap<AbstractTransition,
                    Triple<AbstractState,
                            AbstractTransition?,
                            ArrayList<HashMap<UUID,String>>
                            >?
                    >()
            val targetTraces = if ( pathType == PathType.RESET) {
                val edgesToTarget = autautMF.abstractTransitionGraph.edges().filter { it.label.dest == finalTarget }
                edgesToTarget.map { it.label.tracing }.flatten().map { it.first }.distinct()
            } else {
                emptyList()
            }
            when (pathType) {
                PathType.INCLUDE_INFERED ->  findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        prevEdgeIds =  emptyList(),
                        traversedEdges = HashMap(),
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  true,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = false,
                        includeImplicitInteraction = true,
                        includeLaunchAction = false,
                        pathType = pathType,
                        targetTraces = targetTraces)
                PathType.NORMAL -> findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        finalTarget =  finalTarget,
                        prevEdgeIds =  emptyList(),
                        traversedEdges = HashMap(),
                        allPaths =  allPaths,
                        includeBackEvent =  true,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = false,
                        includeImplicitInteraction = false,
                        includeLaunchAction = true,
                        pathType = pathType,
                        targetTraces = targetTraces)
                PathType.RESET -> findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        prevEdgeIds =  emptyList(),
                        traversedEdges = HashMap(),
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  true,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = true,
                        includeImplicitInteraction = false,
                        includeLaunchAction = false,
                        pathType = pathType,
                        targetTraces = targetTraces)
                PathType.WTG -> findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        prevEdgeIds =  emptyList(),
                        traversedEdges = HashMap(),
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  true,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  true,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = true,
                        includeImplicitInteraction = false,
                        includeLaunchAction = true,
                        pathType = pathType,
                        targetTraces = targetTraces)
            }
        }

        private fun satisfyResetPathType(it: TransitionPath):Boolean
        {
            if (it.path.get(0)!!.abstractAction.actionType!=AbstractActionType.RESET_APP) {
                return false
            }
            return true
        }


        fun findPathToTargetComponentByBFS(autautMF: ATUAMF, currentState: State<*>, root: AbstractState,
                                           prevEdgeIds: List<Int>,
                                           finalTarget: AbstractState, allPaths: ArrayList<TransitionPath>,
                                           traversedEdges: HashMap<Int, Pair<AbstractTransition, Stack<Window>>>,
                                           pathTracking: HashMap<Int,Int>,
                                           depth: Int,
                                           includeWTG: Boolean,
                                           stopWhenHavingUnexercisedAction: Boolean = false,
                                           shortest: Boolean = true,
                                           pathCountLimitation: Int = 1,
                                           includeResetAction: Boolean,//TODO rename
                                           includeBackEvent: Boolean,
                                           includeImplicitInteraction: Boolean,
                                           includeLaunchAction: Boolean,
                                           pathType: PathType,
                                           targetTraces: List<Int>
        ) {
            val graph = autautMF.abstractTransitionGraph
            val nextTransitions = ArrayList<Int>()
            if (prevEdgeIds.isEmpty()) {
                if (depth == 0) {
                    val source = root
                    val windowStack = autautMF.windowStack.clone() as Stack<Window>
                    getNextTraversingNodes(
                            autautMF = autautMF,
                            includeBackEvent =  includeBackEvent,
                            windowStack =  windowStack,
                            graph =  graph,
                            source =  source,
                            prevEdgeId = null,
                            depth =  depth,
                            traversedEdges =  traversedEdges,
                            pathTracking = pathTracking,
                            finalTarget =  finalTarget,
                            root =  root,
                            allPaths =  allPaths,
                            nextTransitions =  nextTransitions,
                            stopWhenHavingUnexercisedAction =  stopWhenHavingUnexercisedAction,
                            includeWTG =  includeWTG,
                            includeResetAction =  includeResetAction,
                            includeImplicitInteraction = includeImplicitInteraction,
                            includeLaunchAction = includeLaunchAction,
                            pathType = pathType,
                            targetTraces = targetTraces)
                } else {
                    return
                }
            } else {
                for (traversing in prevEdgeIds)
                {
                    val source = traversedEdges[traversing]!!.first.dest
                    if (source.window is Launcher)
                        continue
                    if (source is VirtualAbstractState && !includeWTG) {
                        continue
                    }
                    val windowStack = traversedEdges[traversing]!!.second
                    if (windowStack.isEmpty())
                        continue
                    getNextTraversingNodes(
                            autautMF = autautMF,
                            includeBackEvent =  includeBackEvent,
                            windowStack =  windowStack,
                            graph =  graph,
                            source =  source,
                            prevEdgeId = traversing,
                            depth =  depth,
                            traversedEdges =  traversedEdges,
                            pathTracking = pathTracking,
                            finalTarget =  finalTarget,
                            root =  root,
                            allPaths =  allPaths,
                            nextTransitions =  nextTransitions,
                            stopWhenHavingUnexercisedAction =  stopWhenHavingUnexercisedAction,
                            includeWTG =  includeWTG,
                            includeResetAction =  includeResetAction,
                            includeImplicitInteraction = includeImplicitInteraction,
                            includeLaunchAction = includeLaunchAction,
                            pathType = pathType,
                            targetTraces = targetTraces)
                }
            }



            /*if (nextLevelNodes.isEmpty() && !includeWTG) {
                for (traversing in prevTransitions)
                {
                    val source = traversing.second
                    val windowStack = traversing.first
                    if (windowStack.isEmpty())
                        continue
                    getNextTraversingNodes(includeBackEvent, windowStack, graph, source, depth, pathTracking, finalTarget, root, allPaths, nextLevelNodes,stopWhenHavingUnexercisedAction)
                }

           }*/
            if (nextTransitions.isEmpty())
                return
            if (allPaths.size > pathCountLimitation && !shortest) {
                return
            }

            if (allPaths.isEmpty() || (allPaths.size <= pathCountLimitation && !shortest) )
                findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        finalTarget =  finalTarget,
                        traversedEdges = traversedEdges,
                        prevEdgeIds = nextTransitions,
                        allPaths =  allPaths,
                        includeBackEvent = includeBackEvent,
                        pathTracking =  pathTracking,
                        depth =  depth+1,
                        includeWTG =  includeWTG,
                        stopWhenHavingUnexercisedAction =  stopWhenHavingUnexercisedAction,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = includeResetAction,
                        includeImplicitInteraction = includeImplicitInteraction,
                        includeLaunchAction = includeLaunchAction,
                        pathType = pathType,
                        targetTraces = targetTraces)
        }

        private fun getNextTraversingNodes(autautMF: ATUAMF, windowStack: Stack<Window>, graph: AbstractTransitionGraph, source: AbstractState,
                                           prevEdgeId : Int?,
                                           depth: Int,
                                           traversedEdges: HashMap<Int, Pair<AbstractTransition, Stack<Window>>>,
                                           pathTracking: HashMap<Int, Int>,
                                           finalTarget: AbstractState, root: AbstractState,
                                           allPaths: ArrayList<TransitionPath>,
                                           nextTransitions: ArrayList<Int>,
                                           includeWTG: Boolean,
                                           stopWhenHavingUnexercisedAction: Boolean,
                                           includeImplicitInteraction: Boolean,
                                           includeResetAction: Boolean,
                                           includeBackEvent: Boolean,
                                           includeLaunchAction: Boolean,
                                           pathType: PathType,
                                           targetTraces: List<Int>
                                           ) {

        /*    if (includeBackEvent && windowStack.isNotEmpty()) {
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
                            if (!traversedEdges.containsKey(it.destination!!.data)) {
                                processingBackEdges.add(it)
                            }
                        }
                    }
                } else if (backCandiates.containsKey(true)) {
                    backCandiates[true]!!.forEach {
                        if (!traversedEdges.containsKey(it.destination!!.data)) {
                            processingBackEdges.add(it)
                        }
                    }
                }

                processingBackEdges.forEach { edge ->
                    val backTransition = edge.label
                    val backState = edge.destination!!.data
                    val edgeCondition = graph.edgeConditions[edge]!!
                    traversedEdges.put(backState, Triple(source, backTransition,edgeCondition))
                    val fullGraph = createTransitionPath(autautMF, pathType, backState, root, traversedEdges)
                    if (!isDisablePath(fullGraph)) {
                        if (isArrived(backTransition, backState, finalTarget, stopWhenHavingUnexercisedAction,includeWTG)) {
                            allPaths.add(fullGraph)
                            registerTransitionPath(root, backState, fullGraph)
                        }
                        val nextWindowStack = if (backTransition.abstractAction.isLaunchOrReset()) {
                            Stack<Window>().also { it.push(Launcher.getOrCreateNode()) }
                        } else {
                            createWindowStackForNext(windowStack, source, backState)
                        }
                        nextTransitions.add(Pair(first = nextWindowStack, second = backState))
                    }
                }
            }*/

            val originaEdges = graph.edges(source)
            val edgesFromSource = originaEdges.filter { it.destination!=null
                    && AbstractStateManager.instance.ABSTRACT_STATES.contains(it.label.dest)
                    && it.source.data.abstractTransitions.contains(it.label)
            }
           val forwardTransitions = edgesFromSource.filter {
                it.destination != null
                        //&& it.source != it.destination
                        && it.destination!!.data.window !is FakeWindow
                        && includingBackEventOrNot(it,includeBackEvent)
                        && includingRotateUIOrNot(autautMF, it)
                        && includingResetOrNot(it, includeResetAction,depth)
                        && includingWTGOrNot(it, includeWTG)
                        && includingLaunchOrNot(it, includeLaunchAction,depth)
                        && followTrace(it,depth,targetTraces ,traversedEdges, prevEdgeId,pathTracking, pathType)
                /*&& isTheSamePrevWindow(windowStack.peek(), it)*/
            }

            val possibleTransitions = ArrayList<Edge<AbstractState, AbstractTransition>>()

            if (pathType == PathType.RESET) {
                possibleTransitions.addAll(forwardTransitions)
            } else {
                forwardTransitions.groupBy { it.label.abstractAction }.forEach { action, edges ->
                    if (action.isLaunchOrReset()) {
                        if (depth == 0)
                            possibleTransitions.addAll(edges)
                    } else {
                        val groupByDestination = edges.groupBy { it.destination }
                        if (groupByDestination.size > 1) {
                            val groupByPreWindow = edges.groupBy { it.label.prevWindow }
                            groupByPreWindow.forEach { prevWindow, edges ->
                                if (prevWindow == null && includeWTG) {
                                    possibleTransitions.addAll(edges)
                                } else if (prevWindow != null) {
                                    edges.forEach {
                                        if (isTheSamePrevWindow(windowStack.peek(), it)) {
                                            possibleTransitions.add(it)
                                        }
                                    }
                                }
                            }
                        } else {
                            // different prevWindow but the same destination
                            possibleTransitions.addAll(edges)
                        }
                    }
                }
            }

            val selectedTransition = ArrayList<Edge<AbstractState, AbstractTransition>>()
            possibleTransitions.groupBy({ it.label.abstractAction }, { it })
                    .forEach { interaction, u ->
                        if (interaction.isLaunchOrReset() && depth == 0)
                            selectedTransition.addAll(u)
                        else {
                            val reliableTransition = u.filter { !it.label.isImplicit }
                            val implicitTransitions = u.filter { it.label.isImplicit }
                            if (reliableTransition.isEmpty()) {
                                //        if (reliableTransition.isEmpty()) {
                                if (implicitTransitions.any { it.label.abstractAction.actionType == AbstractActionType.ROTATE_UI }) {
                                    selectedTransition.addAll(implicitTransitions)
                                }
                                else if (includeImplicitInteraction == true && depth == 0)
                                    selectedTransition.addAll(implicitTransitions.filter {
                                        it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK
                                                || it.label.abstractAction.actionType == AbstractActionType.ROTATE_UI
                                                || it.label.abstractAction.actionType == AbstractActionType.SWIPE
                                    })
                                else if (includeWTG)
                                    selectedTransition.addAll(implicitTransitions.filter { it.label.fromWTG })
                                else if (depth == 0) {
                                    val loadedAbstractTransitions = implicitTransitions.filter { it.label.dest.loadedFromModel }
                                    selectedTransition.addAll(loadedAbstractTransitions)
                                }
                            }
                            selectedTransition.addAll(reliableTransition)
                        }
                    }

            selectedTransition.forEach {
                val nextState = it.destination!!.data
                val transition = it.label
                val edgeCondition = it.label.userInputs
                val traversedNodes = traversedEdges.values.map {  it.first.dest}
                if (!traversedNodes.contains(it.label.source) && it.label.source!=root) {
                    val strange=""
                }
                if (!traversedNodes.contains(it.label.dest) || pathType == PathType.RESET) {
                    val fullGraph = createTransitionPath(autautMF, pathType, nextState, transition, prevEdgeId, root, traversedEdges, pathTracking)
                    if (fullGraph.path.size <= 10 || pathType == PathType.RESET) {
                        val checkTrace = (pathType == PathType.RESET)
                        if (!isDisablePath(fullGraph, checkTrace)) {
                            if (isArrived(nextState, finalTarget, stopWhenHavingUnexercisedAction, includeWTG)) {
                                allPaths.add(fullGraph)
                                registerTransitionPath(root, nextState, fullGraph)
                            }
                            val nextWindowStack = if (transition.abstractAction.isLaunchOrReset()) {
                                Stack<Window>().also { it.push(Launcher.getOrCreateNode()) }
                            } else {
                                createWindowStackForNext(windowStack, source, nextState)
                            }
                            val key = if (traversedEdges.isEmpty())
                                0
                            else
                                traversedEdges.keys.max()!! + 1
                            traversedEdges.put(key, Pair(it.label, nextWindowStack))
                            if (prevEdgeId != null)
                                pathTracking.put(key, prevEdgeId)
                            nextTransitions.add(key)
                        }
                    }
                }

                /*//Avoid loop
                if (!traversedEdges.containsKey(it.label)) {
                }*/
            }
        }

        private fun followTrace(edge: Edge<AbstractState, AbstractTransition>, depth: Int,targetTraces: List<Int>, traversedEdges: HashMap<Int, Pair<AbstractTransition, Stack<Window>>>, prevEdgeId: Int?, pathTracking: HashMap<Int, Int>, pathType: PathType): Boolean {
            if (pathType != PathType.RESET) {
                return true
            }
            if (prevEdgeId == null)
                return true
            if (edge.label.abstractAction.actionType == AbstractActionType.RESET_APP)
                return true
            if (!edge.label.tracing.any { targetTraces.contains(it.first) })
                return false
            var prevTransitionId = prevEdgeId
            while (prevTransitionId!=null) {
                val prevTransition = traversedEdges.get(prevTransitionId)
                if (prevTransition == null)
                    throw Exception("Prev transition is null")
                if (prevTransition.first.abstractAction.actionType == AbstractActionType.RESET_APP) {
                    if (edge.label.tracing.any { it.second == 1 })
                        return true
                    else
                        return false
                }
                if (edge.label.tracing.any { t -> targetTraces.contains(t.first) && t.second == depth }) {
                    return true
                }
                return false
            }
            return false
        }

        private fun includingBackEventOrNot(it: Edge<AbstractState, AbstractTransition>, includeBackEvent: Boolean): Boolean {
            if (includeBackEvent)
                return true
            if (it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK)
                return false
            return true
        }

        private fun includingWTGOrNot(edge: Edge<AbstractState, AbstractTransition>, includeWTG: Boolean): Boolean {
            if (includeWTG)
                return true
            if (edge.label.fromWTG)
                return false
            return true
        }

        private fun isArrived(nextState: AbstractState, finalTarget: AbstractState, stopWhenHavingUnexercisedAction: Boolean, useVirtualAbstractState: Boolean): Boolean {
            if (nextState == finalTarget){
               return true
            }
            if (stopWhenHavingUnexercisedAction &&
                    nextState !is VirtualAbstractState &&
                    nextState.getUnExercisedActions(null).isNotEmpty())
                return true
            if (useVirtualAbstractState && nextState.window == finalTarget.window) {
                if(finalTarget is VirtualAbstractState && nextState == finalTarget) {
                    return true
                } else if (finalTarget !is VirtualAbstractState){
                    return true
                }
                return false
            }
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

        private fun includingRotateUIOrNot(autautMF: ATUAMF, it: Edge<AbstractState, AbstractTransition>): Boolean {
            if (autautMF.appRotationSupport)
                return true
            return it.label.abstractAction.actionType != AbstractActionType.ROTATE_UI
        }

        private fun isTheSamePrevWindow(prevWindow: Window?, it: Edge<AbstractState, AbstractTransition>): Boolean {
            return (
                    (prevWindow != null && it.label.prevWindow == prevWindow)
                            || prevWindow == null || it.label.prevWindow == null)
        }

        private fun createTransitionPath(autautMF: ATUAMF, pathType: PathType, destination: AbstractState, lastTransition: AbstractTransition, prevEdgeId: Int?,
                                         startingNode: AbstractState, traversedEdges: HashMap<Int, Pair<AbstractTransition, Stack<Window>>>,
                                         pathTracking: HashMap<Int, Int>): TransitionPath {
            val fullPath = TransitionPath(startingNode,pathType,destination)
            val path = LinkedList<AbstractTransition>()
            path.add(lastTransition)

            var traceBackEdgeId: Int? = prevEdgeId
            //construct path as a linkedlist

            while (traceBackEdgeId != null)
            {
                val transition = traversedEdges[traceBackEdgeId]!!.first
                path.addFirst(transition)

                traceBackEdgeId = pathTracking[traceBackEdgeId]
                if (traceBackEdgeId == null)
                    break
            }
            var transitionId = 0
            while (path.isNotEmpty())
            {
                val transition = path.first
                val source = transition.source
                val destination = transition.dest
                fullPath.path.put(transitionId,transition)
                //fullPath.edgeConditions[edge] = pathTracking[backwardNode]!!.third
                val graphEdge = autautMF.abstractTransitionGraph.edge(source,destination,transition)
                path.removeFirst()
                transitionId++
            }
            return fullPath
        }

        fun registerTransitionPath(source: AbstractState, destination: AbstractState, fullPath: TransitionPath) {
            if (!allAvailableTransitionPaths.containsKey(Pair(source, destination)))
                allAvailableTransitionPaths.put(Pair(source, destination), ArrayList())
            allAvailableTransitionPaths[Pair(source, destination)]!!.add(fullPath)
        }
        val unsuccessfulInteractions = HashMap<AbstractTransition, Int>()

        fun addDisablePathFromState(transitionPath: TransitionPath, corruptedTransition: AbstractTransition){
           /* val abstractInteraction = corruptedTransition.label
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
            //abstractTransitionGraph.remove(corruptedTransition)

            val corruptedEdge = corruptedTransition
            /* if (corruptedEdge.label.fromWTG) {
                 disableEdges.add(corruptedEdge)
             }*/

            val disablePath = LinkedList<AbstractTransition>()
            var pathTraverser = PathTraverser(transitionPath)
            // Move to after reset action
            while (!pathTraverser.finalStateAchieved()) {
                val edge = pathTraverser.getNextTransition()
                if (edge == null)
                    break
                if (edge.abstractAction.isLaunchOrReset()) {
                    break
                }
            }
            if (pathTraverser.finalStateAchieved()) {
                if (pathTraverser.getPrevTransition()!!.abstractAction.isLaunchOrReset()) {
                    return
                }
                // No reset action
                pathTraverser = PathTraverser(transitionPath)
            }

            while (!pathTraverser.finalStateAchieved()) {
                val edge = pathTraverser.getNextTransition()
                if (edge == null)
                    break
                disablePath.add(edge)
                if (edge.abstractAction.isLaunchOrReset()) {
                    disablePath.clear()
                }
                if (edge == corruptedEdge) {
                    break
                }
            }

            if (disablePath.isNotEmpty())
                disablePaths.add(disablePath)

            val root = transitionPath.root
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

        fun isDisablePath(path: TransitionPath, checkTrace:Boolean): Boolean {
            if (checkTrace) {
                if (!isFollowingTrace(path)) {
                    return true
                }
            }
            disablePaths.forEach {
                var matched = samePrefix(it, path)
                if (matched)
                    return true
            }

            /*if (path.edges().any { disableEdges.contains(it) }) {
                return true
            }*/
            return false
        }

        private fun isFollowingTrace(path: TransitionPath): Boolean {
            val pathTraverser = PathTraverser(path)
            //move current cursor to the node after the RESET action
            while (!pathTraverser.finalStateAchieved()) {
                val nextTransition = pathTraverser.getNextTransition()
                if (nextTransition == null)
                    break
                val actionType = nextTransition.abstractAction.actionType
                if (actionType == AbstractActionType.RESET_APP || actionType == AbstractActionType.LAUNCH_APP) {
                    break
                }
            }
            if (pathTraverser.finalStateAchieved()) {
                val prevTransition = pathTraverser.getPrevTransition()
                if (prevTransition!=null && prevTransition.abstractAction.isLaunchOrReset())
                    return true
                else {
                    //No reset or launch action
                    pathTraverser.reset()
                }
            }

            val tracing = arrayListOf<Pair<Int,Int>>()
            while (!pathTraverser.finalStateAchieved()) {
                val nextTransition = pathTraverser.getNextTransition()
                if (nextTransition == null)
                    break
                val edgeTracing =nextTransition.tracing
                if (edgeTracing.isEmpty())
                    break
                if (tracing.isEmpty()) {
                    tracing.addAll(edgeTracing)
                } else {
                    val nextTraces = ArrayList<Pair<Int,Int>>()
                    edgeTracing.forEach {trace ->
                        if (tracing.any { trace.first == it.first && trace.second == it.second+1 }) {
                            nextTraces.add(trace)
                        }
                    }
                    tracing.clear()
                    tracing.addAll(nextTraces)
                }
            }
            if (tracing.isNotEmpty())
                return true
            return false
        }

        private fun samePrefix(edges: List<AbstractTransition>, path: TransitionPath): Boolean {
            val iterator = edges.iterator()
            //Fixme

            val pathTraverser = PathTraverser(path)

            while(!pathTraverser.finalStateAchieved()) {
                val nextTransition = pathTraverser.getNextTransition()
                if (nextTransition == null)
                    break
                val actionType = nextTransition.abstractAction.actionType
                if (actionType == AbstractActionType.RESET_APP || actionType == AbstractActionType.LAUNCH_APP){
                    break
                }
            }
            val prevTransition = pathTraverser.getPrevTransition()
            if (prevTransition!=null && !prevTransition.abstractAction.isLaunchOrReset()) {
                //No reset or launch action
                pathTraverser.reset()
            }
            var samePrefix = false
            while (iterator.hasNext() && !pathTraverser.finalStateAchieved()) {
                val edge1 = iterator.next()
                val edge2 = pathTraverser.getNextTransition()
                if (edge2 == null)
                    return false
                if (edge1 != edge2)
                    return false
                samePrefix = true
            }
            return samePrefix
        }
    }

    enum class PathType {
        INCLUDE_INFERED,
        NORMAL,
        RESET,
        WTG,
        ANY
    }
}