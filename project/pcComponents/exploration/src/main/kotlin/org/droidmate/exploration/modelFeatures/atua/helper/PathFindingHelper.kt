package org.droidmate.exploration.modelFeatures.atua.helper

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.DSTG
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
        private val disablePaths = HashSet<Pair<List<AbstractTransition>, DisablePathType>>()

        fun findPathToTargetComponent(autautMF: ATUAMF, currentState: State<*>, root: AbstractState, finalTarget: AbstractState
                                      , allPaths: ArrayList<TransitionPath>
                                      , shortest: Boolean = true
                                      , pathCountLimitation: Int = 1
                                      , pathType: PathType) {
/*            val existingPaths = allAvailableTransitionPaths.get(Pair(root,finalTarget))
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
            }*/
            val pathTracking = HashMap<AbstractTransition,
                    Triple<AbstractState,
                            AbstractTransition?,
                            ArrayList<HashMap<UUID,String>>
                            >?
                    >()
            val targetTraces = if (pathType == PathType.TRACE) {
                val edgesToTarget = autautMF.dstg.edges().filter { it.label.dest == finalTarget }
                edgesToTarget.map { it.label.tracing }.flatten().map { it.first }.distinct()
            } else {
                emptyList()
            }
            val inEdgesTrace =  if (pathType == PathType.TRACE) {
                val edgesToTarget = autautMF.dstg.edges().filter { it.label.dest == finalTarget }
                edgesToTarget.map { it.label.tracing }.flatten()
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
                        includeImplicitBackEvent =  true,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = false,
                        includeImplicitInteraction = true,
                        includeLaunchAction = false,
                        followTrace = false,
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
                        includeImplicitBackEvent =  true,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = false,
                        includeImplicitInteraction = false,
                        includeLaunchAction = true,
                        followTrace = false,
                        pathType = pathType,
                        targetTraces = targetTraces)
                PathType.TRACE -> findPathToTargetComponentByBFS(
                        autautMF = autautMF,
                        currentState = currentState,
                        root = root,
                        prevEdgeIds =  emptyList(),
                        traversedEdges = HashMap(),
                        finalTarget =  finalTarget,
                        allPaths =  allPaths,
                        includeImplicitBackEvent =  false,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = true,
                        includeImplicitInteraction = false,
                        includeLaunchAction = false,
                        followTrace = true,
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
                        includeImplicitBackEvent =  false,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  false,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = true,
                        includeImplicitInteraction = false,
                        includeLaunchAction = false,
                        followTrace = true,
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
                        includeImplicitBackEvent =  true,
                        pathTracking =  HashMap(),
                        depth = 0,
                        includeWTG =  true,
                        stopWhenHavingUnexercisedAction = false,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = true,
                        includeImplicitInteraction = false,
                        includeLaunchAction = false,
                        followTrace = false,
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
                                           includeLaunchAction: Boolean,
                                           includeImplicitBackEvent: Boolean,
                                           includeResetAction: Boolean,//TODO rename
                                           includeImplicitInteraction: Boolean,
                                           followTrace: Boolean,
                                           pathType: PathType,
                                           targetTraces: List<Int>
        ) {
            val graph = autautMF.dstg
            val nextTransitions = ArrayList<Int>()
            if (prevEdgeIds.isEmpty()) {
                if (depth == 0) {
                    val source = root
                    val windowStack = autautMF.windowStack.clone() as Stack<Window>
                    getNextTraversingNodes(
                            autautMF = autautMF,
                            includeImplicitBackEvent =  includeImplicitBackEvent,
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
                            followTrace = followTrace,
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
                    val abstateStack = traversedEdges[traversing]!!.second
                    if (abstateStack.isEmpty())
                        continue
                    getNextTraversingNodes(
                            autautMF = autautMF,
                            includeImplicitBackEvent =  includeImplicitBackEvent,
                            windowStack =  abstateStack,
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
                            followTrace = followTrace,
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
                    getNextTraversingNodes(includeImplicitBackEvent, windowStack, graph, source, depth, pathTracking, finalTarget, root, allPaths, nextLevelNodes,stopWhenHavingUnexercisedAction)
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
                        includeImplicitBackEvent = includeImplicitBackEvent,
                        pathTracking =  pathTracking,
                        depth =  depth+1,
                        includeWTG =  includeWTG,
                        stopWhenHavingUnexercisedAction =  stopWhenHavingUnexercisedAction,
                        shortest = shortest,
                        pathCountLimitation = pathCountLimitation,
                        includeResetAction = includeResetAction,
                        includeImplicitInteraction = includeImplicitInteraction,
                        includeLaunchAction = includeLaunchAction,
                        followTrace = followTrace,
                        pathType = pathType,
                        targetTraces = targetTraces)
        }

        private fun getNextTraversingNodes(autautMF: ATUAMF, windowStack: Stack<Window>, graph: DSTG, source: AbstractState,
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
                                           includeImplicitBackEvent: Boolean,
                                           includeLaunchAction: Boolean,
                                           followTrace: Boolean,
                                           pathType: PathType,
                                           targetTraces: List<Int>
                                           ) {
            val originaEdges = graph.edges(source)
            val edgesFromSource = originaEdges.filter { it.destination!=null
                    && AbstractStateManager.instance.ABSTRACT_STATES.contains(it.label.dest)
                    && it.source.data.abstractTransitions.contains(it.label)
            }
           val forwardTransitions = edgesFromSource.filter {
                it.destination != null
                        //&& it.source != it.destination
                        && it.destination!!.data.window !is FakeWindow
                        && it.destination!!.data.window !is Launcher
                        && includingBackEventOrNot(it,includeImplicitBackEvent)
                        && includingRotateUIOrNot(autautMF, it)
                        && includingResetOrNot(it, includeResetAction,depth, pathType == PathType.RESET)
                        && includingWTGOrNot(it, includeWTG)
                        && includingLaunchOrNot(it, includeLaunchAction,depth)
                        && followTrace(it,depth,targetTraces ,traversedEdges, prevEdgeId,pathTracking, pathType)
                /*&& isTheSamePrevWindow(windowStack.peek(), it)*/
            }

            val possibleTransitions = ArrayList<Edge<AbstractState, AbstractTransition>>()

            if (pathType == PathType.TRACE) {
                possibleTransitions.addAll(forwardTransitions)
            } else {
                forwardTransitions.groupBy { it.label.abstractAction }.forEach { action, edges ->
                    if (action.isLaunchOrReset() || action.actionType == AbstractActionType.SEND_INTENT) {
                        if (depth == 0)
                            possibleTransitions.addAll(edges)
                    } else {
                        var selectedAtLeastOne = false
                        val groupByPreWindow = edges.groupBy { it.label.prevWindow }
                        groupByPreWindow.forEach { prevWindow, groupedEdges1 ->
                            if (prevWindow == null && includeWTG) {
                                possibleTransitions.addAll(groupedEdges1)
                                selectedAtLeastOne = true
                            } else if (prevWindow != null && !selectedAtLeastOne) {
                                if (isTheSamePrevWindow(windowStack.peek(), prevWindow)) {
                                    possibleTransitions.addAll(groupedEdges1)
                                    selectedAtLeastOne = true
                                }
                            }
                        }
                        if (!selectedAtLeastOne) {
                            possibleTransitions.addAll(edges)
                        }
                    }
                }
            }

            val selectedTransition = ArrayList<Edge<AbstractState, AbstractTransition>>()
            val prevAbstractTransition = if (prevEdgeId!=null) {
                traversedEdges.get(prevEdgeId)
            } else
                null
            possibleTransitions.groupBy({ it.label.abstractAction }, { it })
                    .forEach { interaction, u ->
                        if ((interaction.isLaunchOrReset() || interaction.actionType == AbstractActionType.SEND_INTENT) && depth == 0)
                            selectedTransition.addAll(u)
                        else {
                            val reliableTransition = u.filter { !it.label.isImplicit }
                            val implicitTransitions = u.filter { it.label.isImplicit }
                            if (reliableTransition.isEmpty()) {
                                //        if (reliableTransition.isEmpty()) {
                                if (implicitTransitions.any {
                                            it.label.abstractAction.actionType == AbstractActionType.ROTATE_UI
                                                    || it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK }) {
                                    selectedTransition.addAll(implicitTransitions)
                                }
                                else if (includeImplicitInteraction == true) {
                                    if (prevAbstractTransition==null)
                                        selectedTransition.addAll(implicitTransitions)
                                    else if (prevAbstractTransition.first.isImplicit && !prevAbstractTransition.first.abstractAction.isLaunchOrReset()) {
                                        implicitTransitions.filter {
                                            prevAbstractTransition.first.guaranteedAVMs.contains(
                                                    it.label.abstractAction.attributeValuationMap
                                            )
                                        }.let {
                                            selectedTransition.addAll(it)
                                        }
                                    }
                                }
                                else if (includeWTG)
                                    selectedTransition.addAll(implicitTransitions.filter { it.label.fromWTG })
                            } else {
                                if (prevAbstractTransition==null || !prevAbstractTransition.first.isImplicit
                                        || prevAbstractTransition.first.abstractAction.isLaunchOrReset())
                                    selectedTransition.addAll(reliableTransition)
                                else {
                                    reliableTransition.filter {
                                        prevAbstractTransition.first.guaranteedAVMs.containsAll(it.label.source.attributeValuationMaps)
                                    }.let{
                                        selectedTransition.addAll(it)
                                    }
                                }
                            }

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
                if (!traversedNodes.contains(it.label.dest) || pathType == PathType.TRACE) {
                    val fullGraph = createTransitionPath(autautMF, pathType, nextState, transition, prevEdgeId, root, traversedEdges, pathTracking)
                    if (fullGraph.path.size <= 15 || pathType == PathType.TRACE) {
                        if (!isDisablePath(fullGraph, followTrace)) {
                            if (isArrived(transition, finalTarget, stopWhenHavingUnexercisedAction, includeWTG,atuamf = autautMF)) {
                                allPaths.add(fullGraph)
                                registerTransitionPath(root, nextState, fullGraph)
                            }
                            val nextAbstateStack = if (transition.abstractAction.isLaunchOrReset()) {
                                Stack<Window>().also { it.add(Launcher.getOrCreateNode())}
                            } else {
                                createWindowStackForNext(windowStack, source, nextState)
                            }
                            val key = if (traversedEdges.isEmpty())
                                0
                            else
                                traversedEdges.keys.max()!! + 1
                            traversedEdges.put(key, Pair(it.label, nextAbstateStack))
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

        private fun isTheSamePrevWinAbstractState(prevWinAbstractState1: AbstractState?, prevWinAbstractState2: AbstractState?): Boolean {
            return (
                    (prevWinAbstractState1 != null && prevWinAbstractState2 == prevWinAbstractState1)
                            || prevWinAbstractState1 == null || prevWinAbstractState2 == null)
        }

        private fun getPrevWinAbstractState(prevWindow: Window?, pathTracking: HashMap<Int, Int>, prevEdgeId: Int?, traversedEdges: HashMap<Int, Pair<AbstractTransition, Stack<AbstractState>>>): AbstractState? {
            if (prevWindow == null )
                return null
            if (prevEdgeId == null)
                return null
            var prevEdgeId2 = prevEdgeId
            var prevWinAbstractState = traversedEdges.get(prevEdgeId2)!!.first.source
            while (prevWinAbstractState.window != prevWindow) {
                prevEdgeId2 = pathTracking.get(prevEdgeId2)
                if (prevEdgeId2 == null) {
                    return null
                }
                prevWinAbstractState = traversedEdges.get(prevEdgeId2)!!.first.source
            }
            return prevWinAbstractState
        }

        private fun followTrace(edge: Edge<AbstractState, AbstractTransition>, depth: Int,targetTraces: List<Int>, traversedEdges: HashMap<Int, Pair<AbstractTransition, Stack<Window>>>, prevEdgeId: Int?, pathTracking: HashMap<Int, Int>, pathType: PathType): Boolean {
            if (pathType != PathType.TRACE) {
                return true
            }
            if (prevEdgeId == null)
                return true
            if (edge.label.abstractAction.actionType == AbstractActionType.RESET_APP)
                return true
            if (!edge.label.tracing.any { targetTraces.contains(it.first) })
                return false
            var prevTransitionId = prevEdgeId
            var currentTransition = edge.label
            val traceIds = HashSet<Int>()
            traceIds.addAll(edge.label.tracing.map { it.first })
            while (prevTransitionId!=null && traceIds.isNotEmpty()) {
                val prevTransition = traversedEdges.get(prevTransitionId)?.first
                if (prevTransition == null)
                    throw Exception("Prev transition is null")
                if (prevTransition.abstractAction.actionType == AbstractActionType.RESET_APP) {
                    if (currentTransition.tracing.any { it.second == 1})
                        return true
                    else
                        return false
                }
                if (isTransitionFollowingTrace(currentTransition, targetTraces, prevTransition,traceIds)) {
                    val validTraces = currentTransition.tracing.filter { t1->
                        traceIds.contains(t1.first) &&
                        prevTransition.tracing.any { t2->
                            t2.first == t1.first
                                    && t2.second+1==t1.second
                        }
                    }
                    if (validTraces.isEmpty()) {
                        return false
                    }
                    traceIds.clear()
                    traceIds.addAll(validTraces.map { it.first })
                    prevTransitionId = pathTracking.get(prevTransitionId)
                    currentTransition = prevTransition
                    continue
                }
                return false
            }
            if (prevTransitionId == null)
                return true
            return false
        }

        private fun isTransitionFollowingTrace(currentTransition: AbstractTransition, targetTraces: List<Int>, prevTransition: AbstractTransition, traceIds: HashSet<Int>): Boolean {
            if (currentTransition.tracing.all { t-> !targetTraces.contains(t.first)
                            || !traceIds.contains(t.first)}) {
                return false
            }
            return currentTransition.tracing.any { t ->
                        traceIds.contains(t.first) &&
                        prevTransition.tracing.any {
                    it.first == t.first
                            && it.second + 1 == t.second
                }
            }
        }

        private fun includingBackEventOrNot(it: Edge<AbstractState, AbstractTransition>, includeImplicitBackEvent: Boolean): Boolean {
            if (includeImplicitBackEvent)
                return true
            if (it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK && it.label.isImplicit)
                return false
            return true
        }

        private fun includingWTGOrNot(edge: Edge<AbstractState, AbstractTransition>, includeWTG: Boolean): Boolean {
            if (includeWTG)
                return true
            if (edge.label.fromWTG || edge.label.dest is VirtualAbstractState)
                return false
            return true
        }

        private fun isArrived(transition: AbstractTransition, finalTarget: AbstractState, stopWhenHavingUnexercisedAction: Boolean, useVirtualAbstractState: Boolean,atuamf: ATUAMF): Boolean {
            if (transition.isImplicit && finalTarget !is VirtualAbstractState) {
                if (!(transition.abstractAction.actionType == AbstractActionType.PRESS_BACK
                        || transition.abstractAction.actionType == AbstractActionType.ROTATE_UI
                        || transition.abstractAction.isLaunchOrReset())) {
                    return false
                }
            }
            val nextState = transition.dest
            if (nextState == finalTarget){
               return true
            }
            if (stopWhenHavingUnexercisedAction &&
                    nextState !is VirtualAbstractState &&
                    nextState.getUnExercisedActions(null,atuamf).isNotEmpty())
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

        private fun includingResetOrNot(it: Edge<AbstractState, AbstractTransition>, includeReset: Boolean, depth: Int, onlyStartWithReset: Boolean): Boolean {
            if (includeReset && depth == 0) {
                if (onlyStartWithReset)
                    return it.label.abstractAction.actionType == AbstractActionType.RESET_APP
                else
                    return true
            }
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
            val newWindowStack = windowStack.clone() as Stack<Window>
            if (newWindowStack.contains(nextState.window) && newWindowStack.size > 1) {
                // Return to the prev window
                // Pop the window
                while (newWindowStack.pop() != nextState.window) {
                }
            } else {
                if (nextState.window != prevState.window) {
                    if (prevState.window !is Dialog && prevState.window !is OptionsMenu) {
                        newWindowStack.push(prevState.window)
                    }
                } else if (nextState.isOpeningKeyboard) {
                    newWindowStack.push(nextState.window)
                }
            }
            return newWindowStack
        }

        private fun backToLauncherOrNot(it: Edge<AbstractState, AbstractTransition>) =
                it.destination!!.data.window !is Launcher

        private fun includingRotateUIOrNot(autautMF: ATUAMF, it: Edge<AbstractState, AbstractTransition>): Boolean {
            if (autautMF.appRotationSupport)
                return true
            return it.label.abstractAction.actionType != AbstractActionType.ROTATE_UI
        }

        private fun isTheSamePrevWindow(prevWindow1: Window?, prevWindow2: Window?): Boolean {
            return (
                    (prevWindow1 != null && prevWindow2 == prevWindow1)
                            || prevWindow1 == null || prevWindow2 == null)
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
                val graphEdge = autautMF.dstg.edge(source,destination,transition)
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

        fun addDisablePathFromState(transitionPath: TransitionPath, corruptedTransition: AbstractTransition?){
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

            if (corruptedEdge!=null && corruptedEdge.abstractAction.isLaunchOrReset())
                return
            /* if (corruptedEdge.label.fromWTG) {
                 disableEdges.add(corruptedEdge)
             }*/

            val disablePath = LinkedList<AbstractTransition>()
            var pathTraverser = PathTraverser(transitionPath)
            // Move to after reset action
            while (!pathTraverser.finalStateAchieved()) {
                val edge = pathTraverser.next()
                if (edge == null)
                    break
                if (edge.abstractAction.isLaunchOrReset()) {
                    break
                }
            }
            if (pathTraverser.finalStateAchieved()) {
                if (pathTraverser.getCurrentTransition()!!.abstractAction.isLaunchOrReset()) {
                    return
                }
                // No reset action
                pathTraverser = PathTraverser(transitionPath)
            }

            while (!pathTraverser.finalStateAchieved()) {
                val edge = pathTraverser.next()
                if (edge == null)
                    break
                disablePath.add(edge)
                if (edge.abstractAction.isLaunchOrReset()) {
                    disablePath.clear()
                }
                if (corruptedEdge!=null && edge == corruptedEdge) {
                    break
                }
            }

            if (disablePath.isNotEmpty()) {
                if (corruptedEdge == null)
                    disablePaths.add(Pair(disablePath,DisablePathType.UNACHIEVABLE_FINAL_STATE))
                else
                    disablePaths.add(Pair(disablePath,DisablePathType.UNAVAILABLE_ACTION))
            }
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
                var matched = samePrefix(it.first,it.second, path)
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
                val nextTransition = pathTraverser.next()
                if (nextTransition == null)
                    break
                val actionType = nextTransition.abstractAction.actionType
                if (actionType == AbstractActionType.RESET_APP || actionType == AbstractActionType.LAUNCH_APP) {
                    break
                }
            }
            if (pathTraverser.finalStateAchieved()) {
                val prevTransition = pathTraverser.getCurrentTransition()
                if (prevTransition!=null && prevTransition.abstractAction.isLaunchOrReset())
                    return true
                else {
                    //No reset or launch action
                    pathTraverser.reset()
                }
            }

            val tracing = arrayListOf<Pair<Int,Int>>()
            while (!pathTraverser.finalStateAchieved()) {
                val nextTransition = pathTraverser.next()
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

        private fun samePrefix(edges: List<AbstractTransition>, type: DisablePathType, path: TransitionPath): Boolean {
            val iterator = edges.iterator()
            //Fixme

            val pathTraverser = PathTraverser(path)

            while(!pathTraverser.finalStateAchieved()) {
                val nextTransition = pathTraverser.next()
                if (nextTransition == null)
                    break
                val actionType = nextTransition.abstractAction.actionType
                if (actionType == AbstractActionType.RESET_APP || actionType == AbstractActionType.LAUNCH_APP){
                    break
                }
            }
            val prevTransition = pathTraverser.getCurrentTransition()
            if (prevTransition!=null && !prevTransition.abstractAction.isLaunchOrReset()) {
                //No reset or launch action
                pathTraverser.reset()
            }

            var samePrefix = true
            var initial=true
            while (iterator.hasNext() ) {
                val edge1 = iterator.next()
                if (pathTraverser.finalStateAchieved()) {
                    samePrefix = false
                    break
                }
                val edge2 = pathTraverser.next()
                if (edge2 == null) {
                    samePrefix = false
                    break
                }
                if (initial) {
                    initial = false
                    val startState1 = edge1.source
                    val startState2 = edge2.source
                    if (startState1 != startState2) {
                        return false
                    }

                }
                if (edge1.abstractAction == edge2.abstractAction && edge1.prevWindow == edge2.prevWindow) {
                    continue
                } else {
                    samePrefix = false
                    break
                }
            }
            if (samePrefix && pathTraverser.finalStateAchieved()) {
                if (type == DisablePathType.UNAVAILABLE_ACTION) {
                    samePrefix = false
                }
            }
            return samePrefix
        }
    }

    enum class PathType {
        INCLUDE_INFERED,
        NORMAL,
        RESET,
        TRACE,
        WTG,
        ANY
    }
}

enum class DisablePathType {
    UNAVAILABLE_ACTION,
    UNACHIEVABLE_FINAL_STATE
}
