package org.droidmate.exploration.modelFeatures.autaut.helper

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractTransitionGraph
import org.droidmate.exploration.modelFeatures.autaut.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.WTG.PathTraverser
import org.droidmate.exploration.modelFeatures.autaut.WTG.TransitionPath
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.FakeWindow
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PathFindingHelper {
    companion object {
        val allAvailableTransitionPaths =  HashMap<Pair<AbstractState,AbstractState>,ArrayList<TransitionPath>>()
        private val disableEdges = HashSet<Edge<AbstractState, AbstractTransition>>()
        private val disablePaths = HashSet<List<Edge<AbstractState,AbstractTransition>>>()

        fun findPathToTargetComponent(autautMF: AutAutMF, currentState: State<*>, root: AbstractState, lastTransitions: List<Triple<Stack<Window>,AbstractState, AbstractTransition?>>, finalTarget: AbstractState
                                      , allPaths: ArrayList<TransitionPath>
                                      , shortest: Boolean = true
                                      , pathCountLimitation: Int = 1
                                      , pathType: PathType) {
            val existingPaths = allAvailableTransitionPaths.get(Pair(root,finalTarget))
            if (existingPaths!= null && existingPaths.isNotEmpty()) {
               val satisfiedPaths = existingPaths.filter {
                           (pathType == PathType.INCLUDE_INFERED && it.edges().all { !it.label.fromWTG })
                                   || (pathType == PathType.RESET && satisfyResetPathType(it))
                                   || (pathType == PathType.WTG )
               }
                if (satisfiedPaths.isNotEmpty()) {
                    allPaths.addAll(satisfiedPaths)
                    return
                }
            }
            val pathTracking = HashMap<AbstractTransition,
                    Triple<AbstractState,
                            AbstractTransition?,
                            ArrayList<HashMap<Widget,String>>
                            >?
                    >()
            when (pathType) {
                PathType.INCLUDE_INFERED ->  findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        lastTransitions =  lastTransitions,
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  true,
                        pathTracking =  pathTracking,
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = false,
                        includeImplicitInteraction = true,
                        includeLaunchAction = false,
                        pathType = pathType)
                PathType.FOLLOW_TRACE -> findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        lastTransitions =  lastTransitions,
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  true,
                        pathTracking =  pathTracking,
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = false,
                        includeImplicitInteraction = false,
                        includeLaunchAction = true,
                        pathType = pathType)
                PathType.RESET -> findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        lastTransitions =  lastTransitions,
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  true,
                        pathTracking =  pathTracking,
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = true,
                        includeImplicitInteraction = false,
                        includeLaunchAction = false,
                        pathType = pathType)
                PathType.WTG -> findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        lastTransitions =  lastTransitions,
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeBackEvent =  true,
                        pathTracking =  pathTracking,
                        depth = 0,
                        includeWTG =  true,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = true,
                        includeImplicitInteraction = false,
                        includeLaunchAction = true,
                        pathType = pathType)
            }
        }

        private fun satisfyResetPathType(it: TransitionPath):Boolean
        {
            val edges = it.edges()
            if (edges.first().label.abstractAction.actionType!=AbstractActionType.RESET_APP) {
                return false
            }
            if (edges.all {
                        it.label.abstractAction.actionType!=AbstractActionType.RESET_APP && it.label.isExplicit() })
                return true
            return false
        }


        fun findPathToTargetComponentByBFS(autautMF: AutAutMF, currentState: State<*>, root: AbstractState,
                                           lastTransitions: List<Triple<Stack<Window>, AbstractState, AbstractTransition?>>,
                                           finalTarget: AbstractState, allPaths: ArrayList<TransitionPath>,
                                           pathTracking: HashMap<AbstractTransition,Triple<AbstractState, AbstractTransition?,ArrayList<HashMap<Widget,String>>>?>,
                                           depth: Int,
                                           includeWTG: Boolean,
                                           stopWhenHavingUnexercisedAction: Boolean = false,
                                           shortest: Boolean = true,
                                           pathCountLimitation: Int = 1,
                                           includeResetAction: Boolean,//TODO rename
                                           includeBackEvent: Boolean,
                                           includeImplicitInteraction: Boolean,
                                           includeLaunchAction: Boolean,
                                           pathType: PathType
        ) {
            if (lastTransitions.isEmpty())
                return
            val graph = autautMF.abstractTransitionGraph
            val nextTransitions = ArrayList<Triple<Stack<Window>,AbstractState, AbstractTransition>>()
            for (traversing in lastTransitions)
            {
                val source = traversing.second
                if (source.window is Launcher)
                    continue
                if (source is VirtualAbstractState && !includeWTG) {
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
                        lastTransition = traversing.third,
                        depth =  depth,
                        pathTracking =  pathTracking,
                        finalTarget =  finalTarget,
                        root =  root,
                        allPaths =  allPaths,
                        nextTransitions =  nextTransitions,
                        stopWhenHavingUnexercisedAction =  stopWhenHavingUnexercisedAction,
                        includeWTG =  includeWTG,
                        includeResetAction =  includeResetAction,
                        includeImplicitInteraction = includeImplicitInteraction,
                        includeLauchAction = includeLaunchAction,
                        pathType = pathType)
            }
            /*if (nextLevelNodes.isEmpty() && !includeWTG) {
                for (traversing in lastTransitions)
                {
                    val source = traversing.second
                    val windowStack = traversing.first
                    if (windowStack.isEmpty())
                        continue
                    getNextTraversingNodes(includeBackEvent, windowStack, graph, source, depth, pathTracking, finalTarget, root, allPaths, nextLevelNodes,stopWhenHavingUnexercisedAction)
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
                        lastTransitions =  nextTransitions,
                        finalTarget =  finalTarget,
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
                        pathType = pathType)
        }

        private fun getNextTraversingNodes(autautMF: AutAutMF, windowStack: Stack<Window>, graph: AbstractTransitionGraph, source: AbstractState,
                                           lastTransition: AbstractTransition?,
                                           depth: Int,
                                           pathTracking: HashMap<AbstractTransition, Triple<AbstractState, AbstractTransition?, ArrayList<HashMap<Widget,String>>>?>,
                                           finalTarget: AbstractState, root: AbstractState,
                                           allPaths: ArrayList<TransitionPath>,
                                           nextTransitions: ArrayList<Triple<Stack<Window>, AbstractState, AbstractTransition>>,
                                           includeWTG: Boolean,
                                           stopWhenHavingUnexercisedAction: Boolean,
                                           includeImplicitInteraction: Boolean,
                                           includeResetAction: Boolean,
                                           includeBackEvent: Boolean,
                                           includeLauchAction: Boolean,
                                           pathType: PathType
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
                            if (!pathTracking.containsKey(it.destination!!.data)) {
                                processingBackEdges.add(it)
                            }
                        }
                    }
                } else if (backCandiates.containsKey(true)) {
                    backCandiates[true]!!.forEach {
                        if (!pathTracking.containsKey(it.destination!!.data)) {
                            processingBackEdges.add(it)
                        }
                    }
                }

                processingBackEdges.forEach { edge ->
                    val backTransition = edge.label
                    val backState = edge.destination!!.data
                    val edgeCondition = graph.edgeConditions[edge]!!
                    pathTracking.put(backState, Triple(source, backTransition,edgeCondition))
                    val fullGraph = createTransitionPath(autautMF, pathType, backState, root, pathTracking)
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

            val edgesFromSource = graph.edges(source).filter { it.destination!=null
            }
            val forwardTransitions = edgesFromSource.filter {
                it.destination != null
                        //&& it.source != it.destination
                        && it.destination!!.data.window !is FakeWindow
                        && includingBackEventOrNot(it,includeBackEvent)
                        && includingRotateUIOrNot(autautMF, it)
                        && includingResetOrNot(it, includeResetAction,depth)
                        && includingWTGOrNot(it, includeWTG)
                        && includingLaunchOrNot(it, includeLauchAction,depth)
                        && followTrace(it,lastTransition?.tracing?:emptySet<Pair<Int,Int>>(),pathType)
                /*&& isTheSamePrevWindow(windowStack.peek(), it)*/
            }

            val possibleTransitions = ArrayList<Edge<AbstractState, AbstractTransition>>()

            forwardTransitions.groupBy { it.label.abstractAction }.forEach { action, edges ->
                if (action.isLaunchOrReset()) {
                    if (depth == 0)
                        possibleTransitions.addAll(edges)
                } else {
                    val groupByDestination = edges.groupBy { it.destination }
                    if (groupByDestination.size > 1) {
                        val groupByPreWindow = edges.groupBy { it.label.prevWindow }
                        groupByPreWindow.forEach { prevWindow, edges ->
                            if (prevWindow == null && includeWTG){
                                possibleTransitions.addAll(edges)
                            } else if (prevWindow != null){
                                edges.forEach {
                                    if (isTheSamePrevWindow(windowStack.peek(),it)) {
                                        possibleTransitions.add(it)
                                    }
                                }
                            }
                        }
                    }else {
                        // different prevWindow but the same destination
                        possibleTransitions.addAll(edges)
                    }
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
                                if (implicitTransitions.any { it.label.abstractAction.actionType == AbstractActionType.ROTATE_UI }) {
                                    processedTransition.addAll(implicitTransitions)
                                }
                                else if (includeImplicitInteraction == true && depth == 0)
                                    processedTransition.addAll(implicitTransitions.filter {
                                        it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK
                                                || it.label.abstractAction.actionType == AbstractActionType.ROTATE_UI
                                                || it.label.abstractAction.actionType == AbstractActionType.SWIPE
                                    })
                                else if (includeWTG)
                                    processedTransition.addAll(implicitTransitions.filter { it.label.fromWTG })
                                else if (depth == 0) {
                                    val loadedAbstractTransitions = implicitTransitions.filter { it.label.dest.loaded }
                                    processedTransition.addAll(loadedAbstractTransitions)
                                }
                            }
                            processedTransition.addAll(reliableTransition)
                        }
                    }

            processedTransition.forEach {
                val nextState = it.destination!!.data
                //Avoid loop
                if (!pathTracking.containsKey(it.label)) {
                    val transition = it.label
                    val edgeCondition = graph.edgeConditions[it]!!
                    pathTracking.put(transition, Triple(source, lastTransition, edgeCondition))
                    val fullGraph = createTransitionPath(autautMF,pathType, nextState, transition, root, pathTracking)
                    var foundPath = false
                    val checkTrace = (pathType == PathType.RESET)
                    if (!isDisablePath(fullGraph,checkTrace)) {
                        if (isArrived(it.label, nextState, finalTarget, stopWhenHavingUnexercisedAction,includeWTG)) {
                            allPaths.add(fullGraph)
                            registerTransitionPath(root, nextState, fullGraph)
                            foundPath = true
                        }
                        val nextWindowStack = if (transition.abstractAction.isLaunchOrReset()) {
                            Stack<Window>().also { it.push(Launcher.getOrCreateNode()) }
                        } else {
                            createWindowStackForNext(windowStack, source, nextState)
                        }
                        nextTransitions.add(Triple(first = nextWindowStack, second = it.destination!!.data,third = it.label))
                    }
                }
            }
        }

        private fun followTrace(edge: Edge<AbstractState, AbstractTransition>, tracingList: Set<Pair<Int,Int>>, pathType: PathType): Boolean {
            if (pathType!=PathType.FOLLOW_TRACE && pathType != PathType.RESET) {
                if (edge.source == edge.destination)
                    return false
                return true
            }
            if (tracingList.isEmpty()) {
                if (edge.source == edge.destination)
                    return false
                return true
            }

            if (tracingList.any {trace ->
                edge.label.tracing.any { it.first == trace.first && it.second == trace.second+1 }
            })
                return true
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

        private fun isArrived(abstractTransition: AbstractTransition, nextState: AbstractState, finalTarget: AbstractState, stopWhenHavingUnexercisedAction: Boolean, useVirtualAbstractState: Boolean): Boolean {
            if (nextState == finalTarget){
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

        private fun createTransitionPath(autautMF: AutAutMF, pathType: PathType, finalTarget: AbstractState, lastTransition: AbstractTransition, startingNode: AbstractState, pathTracking: Map<AbstractTransition, Triple<AbstractState,AbstractTransition?, ArrayList<HashMap<Widget,String>>>?>): TransitionPath {
            val fullPath = TransitionPath(startingNode,pathType,finalTarget)
            var backwardNode = finalTarget
            var traceBackTransition = lastTransition
            //construct path as a linkedlist
            val path = LinkedList<AbstractTransition>()
            while (true)
            {
                path.addFirst(traceBackTransition)
                backwardNode = traceBackTransition.source
                val tracking = pathTracking[traceBackTransition]!!
                if (tracking.second == null)
                    break
                traceBackTransition = tracking.second!!
            }

            while (path.isNotEmpty())
            {
                val transition = path.first
                val source = transition.source
                val destination = transition.dest
                val edge = fullPath.add(source, destination,transition)
                //fullPath.edgeConditions[edge] = pathTracking[backwardNode]!!.third
                val graphEdge = autautMF.abstractTransitionGraph.edge(source,destination,edge.label)
                if (graphEdge!=null && autautMF.abstractTransitionGraph.edgeConditions.containsKey(graphEdge))
                {
                    fullPath.edgeConditions.put(edge,autautMF.abstractTransitionGraph.edgeConditions[graphEdge]!!)
                }
                path.removeFirst()
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
            val corruptedEdge = edge
            /* if (corruptedEdge.label.fromWTG) {
                 disableEdges.add(corruptedEdge)
             }*/

            val disablePath = ArrayList<Edge<AbstractState,AbstractTransition>>()
            val pathTraverser = PathTraverser(transitionPath)
            while (pathTraverser.getNextEdge()!=null) {
                val edge = pathTraverser.latestEdge!!
                disablePath.add(edge)
                if (edge.label.abstractAction.isLaunchOrReset()) {
                    disablePath.clear()
                }
                if (edge == corruptedEdge) {
                    break
                }
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
            while (pathTraverser.getNextEdge()!=null) {
                if (pathTraverser.latestEdge==null) {
                    throw Exception()
                }
                val actionType = pathTraverser.latestEdge!!.label.abstractAction.actionType
                if (actionType == AbstractActionType.RESET_APP || actionType == AbstractActionType.LAUNCH_APP) {
                    break
                }
            }
            if (pathTraverser.latestEdge == null) {
                pathTraverser.reset()
                if (path.edges(path.root.data).isEmpty())
                    return true
            }
            if (path.edges(pathTraverser.latestEdge!!.destination!!.data).isEmpty())
                return true
            val tracing = arrayListOf<Pair<Int,Int>>()
            while (pathTraverser.getNextEdge()!=null) {
                val edgeTracing = pathTraverser.latestEdge!!.label.tracing
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

        private fun samePrefix(edges: List<Edge<AbstractState, AbstractTransition>>, path: TransitionPath): Boolean {
            val iterator = edges.iterator()
            //Fixme

            val pathTraverser = PathTraverser(path)
            while(pathTraverser.getNextEdge()!=null) {
                val actionType = pathTraverser.latestEdge!!.label.abstractAction.actionType
                if (actionType == AbstractActionType.RESET_APP || actionType == AbstractActionType.LAUNCH_APP){
                    break
                }
            }
            if (pathTraverser.latestEdge == null) {
                pathTraverser.reset()
            }
            while (iterator.hasNext() && pathTraverser.getNextEdge() != null) {
                val edge1 = iterator.next()
                val edge2 = pathTraverser.latestEdge
                if (edge2 == null)
                    return false
                if (edge1 != edge2)
                    return false
            }
            if (!iterator.hasNext())
                return true
            return false
        }
    }

    enum class PathType {
        INCLUDE_INFERED,
        FOLLOW_TRACE,
        RESET,
        WTG,
        ANY
    }
}