/*
 * ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
 * Copyright (C) 2019 - 2021 University of Luxembourg
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package org.atua.modelFeatures.helper

import org.atua.modelFeatures.dstg.AbstractActionType
import org.atua.modelFeatures.dstg.AbstractTransition
import org.atua.modelFeatures.dstg.AbstractState
import org.atua.modelFeatures.dstg.AbstractStateManager
import org.atua.modelFeatures.dstg.DSTG
import org.atua.modelFeatures.dstg.VirtualAbstractState
import org.atua.modelFeatures.ewtg.PathTraverser
import org.atua.modelFeatures.ewtg.TransitionPath
import org.atua.modelFeatures.ewtg.window.Dialog
import org.atua.modelFeatures.ewtg.window.FakeWindow
import org.atua.modelFeatures.ewtg.window.Launcher
import org.atua.modelFeatures.ewtg.window.Window
import org.atua.modelFeatures.ewtg.window.OptionsMenu
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

        fun findPathToTargetComponent(autautMF: org.atua.modelFeatures.ATUAMF, currentState: State<*>, root: AbstractState, finalTarget: AbstractState
                                      , allPaths: ArrayList<TransitionPath>
                                      , shortest: Boolean = true
                                      , pathCountLimitation: Int = 3
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
            val targetTraces = if (pathType == PathType.PARTIAL_TRACE) {
                val edgesToTarget = autautMF.dstg.edges().filter { it.label.dest == finalTarget }
                edgesToTarget.map { it.label.tracing }.flatten().map { it.first }.distinct()
            } else {
                emptyList()
            }

            val currentAbstractStateStack: List<AbstractState> = autautMF.getAbstractStateStack()
            when (pathType) {
/*                PathType.INCLUDE_INFERED ->  findPathToTargetComponentByBFS(
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
                        targetTraces = targetTraces,
                        currentAbstractStateStack = currentAbstractStateStack)*/
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
                        includeImplicitInteraction = true,
                        includeLaunchAction = false,
                        followTrace = false,
                        pathType = pathType,
                        targetTraces = targetTraces,
                        currentAbstractStateStack = currentAbstractStateStack)
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
                        includeResetAction = false,
                        includeImplicitInteraction = false,
                        includeLaunchAction = true,
                        followTrace = false,
                        pathType = pathType,
                        targetTraces = targetTraces,
                        currentAbstractStateStack = currentAbstractStateStack)
                PathType.PARTIAL_TRACE -> findPathToTargetComponentByBFS(
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
                        includeResetAction = false,
                        includeImplicitInteraction = false,
                        includeLaunchAction = true,
                        followTrace = true,
                        pathType = pathType,
                        targetTraces = targetTraces,
                        currentAbstractStateStack = currentAbstractStateStack)
                PathType.FULLTRACE -> findPathToTargetComponentByBFS(
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
                        pathCountLimitation = 1,
                        includeResetAction = true,
                        includeImplicitInteraction = false,
                        includeLaunchAction = false,
                        followTrace = true,
                        pathType = pathType,
                        targetTraces = targetTraces,
                        currentAbstractStateStack = currentAbstractStateStack)

            }
        }

        private fun satisfyResetPathType(it: TransitionPath):Boolean
        {
            if (it.path.get(0)!!.abstractAction.actionType!=AbstractActionType.RESET_APP) {
                return false
            }
            return true
        }


        fun findPathToTargetComponentByBFS(autautMF: org.atua.modelFeatures.ATUAMF, currentState: State<*>, root: AbstractState,
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
                                           targetTraces: List<Int>,
                                           currentAbstractStateStack: List<AbstractState>
        ) {
            val graph = autautMF.dstg
            val nextTransitions = ArrayList<Int>()
            if (prevEdgeIds.isEmpty()) {
                if (depth == 0) {
                    val source = root
                    val windowStack = autautMF.windowStack.clone() as Stack<Window>
                    getNextTraversingNodes(
                            atuaMF = autautMF,
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
                            targetTraces = targetTraces,
                            currentAbstractStateStack = currentAbstractStateStack)
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
                            atuaMF = autautMF,
                            includeImplicitBackEvent =  includeImplicitBackEvent,
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
                            followTrace = followTrace,
                            pathType = pathType,
                            targetTraces = targetTraces,
                            currentAbstractStateStack = currentAbstractStateStack)
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
                        targetTraces = targetTraces,
                        currentAbstractStateStack = currentAbstractStateStack)
        }

        private fun getNextTraversingNodes(atuaMF: org.atua.modelFeatures.ATUAMF, windowStack: Stack<Window>, graph: DSTG, source: AbstractState,
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
                                           targetTraces: List<Int>,
                                           currentAbstractStateStack: List<AbstractState>
                                           ) {
            val originaEdges = graph.edges(source)
            val prevAbstractTransition = if (prevEdgeId!=null) {
                traversedEdges.get(prevEdgeId)
            } else
                null
            val ancestorAbstractStates = ArrayList<AbstractState>()
            var ancestorEdgeId = prevEdgeId
            var startedWithLaunchOrResetAction = IsCurrentPathStartingWithLaunchOrReset(ancestorEdgeId, traversedEdges, ancestorAbstractStates, pathTracking)
            if (!startedWithLaunchOrResetAction) {
                currentAbstractStateStack.forEach { abstractState ->
                    if (!ancestorAbstractStates.any { it.window == abstractState.window }) {
                        ancestorAbstractStates.add(abstractState)
                    }
                }
            }

            val edgesFromSource = originaEdges.filter { it.destination!=null
                    && AbstractStateManager.INSTANCE.ABSTRACT_STATES.contains(it.label.dest)
                    && it.source.data.abstractTransitions.contains(it.label)
            }
           val forwardTransitions = edgesFromSource.filter {
                it.destination != null
                        //&& it.source != it.destination
                        && it.destination!!.data.window !is FakeWindow
                        && includingBackEventOrNot(it,includeImplicitBackEvent)
                        && includingRotateUIOrNot(atuaMF, it)
                        && includingResetOrNot(it, includeResetAction,depth, pathType == PathType.FULLTRACE)
                        && includingWTGOrNot(it, includeWTG)
                        && includingLaunchOrNot(it, includeLaunchAction,depth)
                        && followTrace(it,depth,targetTraces ,traversedEdges, prevEdgeId,pathTracking, pathType)
                       /* && (it.label.dependentAbstractStates.isEmpty() ||
                        it.label.dependentAbstractStates.intersect(ancestorAbstractStates).isNotEmpty())*/
                /*&& isTheSamePrevWindow(windowStack.peek(), it)*/
            }

            val filteredTransitions = ArrayList<Edge<AbstractState, AbstractTransition>>()

            // remove LaunchApp or ResetApp if it is necessary
            forwardTransitions.groupBy { it.destination }.forEach { t, u ->
                val nonLaunchOrResetTransitions = u.filter {!it.label.abstractAction.isLaunchOrReset() }
                if (nonLaunchOrResetTransitions.isNotEmpty()) {
                    filteredTransitions.addAll(nonLaunchOrResetTransitions)
                } else {
                    val launchTransitions = u.filter {it.label.abstractAction.actionType == AbstractActionType.LAUNCH_APP }
                    if (launchTransitions.isNotEmpty())
                        filteredTransitions.addAll(launchTransitions)
                    else {
                        filteredTransitions.addAll(u)
                    }
                }
            }


            val possibleTransitions = ArrayList<Edge<AbstractState, AbstractTransition>>()

            if (pathType == PathType.PARTIAL_TRACE || pathType == PathType.FULLTRACE) {
                possibleTransitions.addAll(filteredTransitions)
            } else {
                filteredTransitions.groupBy { it.label.abstractAction }.forEach { action, edges ->
                    if (action.isLaunchOrReset() || action.actionType == AbstractActionType.SEND_INTENT) {
                        if (depth == 0)
                            possibleTransitions.addAll(edges)
                    } else {
                        edges.forEach {
                            possibleTransitions.add(it)
                            /*val prevWindows = it.label.dependentAbstractStates.map { it.window }
                            if (prevWindows.isEmpty() && includeWTG) {
                                possibleTransitions.add(it)
                            } else {
                                prevWindows.forEach { prevWindow->
                                    if (isTheSamePrevWindow(windowStack.peek(), prevWindow)) {
                                        possibleTransitions.add(it)
                                    }
                                }
                            }*/
                        }
                    }
                }
            }

            val selectedTransition = ArrayList<Edge<AbstractState, AbstractTransition>>()


            possibleTransitions.groupBy({ it.label.abstractAction }, { it })
                    .forEach { abstractAction, u ->
                        if ((abstractAction.isLaunchOrReset() || abstractAction.actionType == AbstractActionType.SEND_INTENT) && depth == 0) {
                            selectedTransition.addAll(u.filter { it.label.isImplicit })
                        }
                        else {
                            val explicitTransitions = u.filter { !it.label.isImplicit }
                            val implicitTransitions = u.filter { it.label.isImplicit }
                            if (explicitTransitions.isEmpty()) {
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
                                            (it.label.dependentAbstractStates.isEmpty() || !it.label.guardEnabled ||
                                                    it.label.dependentAbstractStates.intersect(ancestorAbstractStates).isNotEmpty())
                                        }.let {
                                            selectedTransition.addAll(it)
                                        }
                                    }
                                }
                                else if (includeWTG)
                                    selectedTransition.addAll(implicitTransitions.filter { it.label.fromWTG })
                            } else {
                                if (pathType == PathType.FULLTRACE){
                                    selectedTransition.addAll(explicitTransitions)
                                } else if (pathType == PathType.PARTIAL_TRACE) {
                                    explicitTransitions.filter {
                                                (it.label.dependentAbstractStates.isEmpty() || !it.label.guardEnabled ||
                                                it.label.dependentAbstractStates.intersect(ancestorAbstractStates).isNotEmpty())
                                    }.let{
                                        selectedTransition.addAll(it)
                                    }
                                }
                                else {
                                    if (prevAbstractTransition == null)
                                        selectedTransition.addAll(explicitTransitions)
                                    else{
                                        explicitTransitions.filter {
                                            (it.label.dependentAbstractStates.isEmpty() || !it.label.guardEnabled ||
                                                    it.label.dependentAbstractStates.intersect(ancestorAbstractStates).isNotEmpty())
                                        }.let{
                                            selectedTransition.addAll(it)
                                        }
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
                if (!traversedNodes.contains(it.label.dest) || pathType == PathType.PARTIAL_TRACE || pathType == PathType.FULLTRACE) {
                    val fullGraph = createTransitionPath(atuaMF, pathType, nextState, transition, prevEdgeId, root, traversedEdges, pathTracking)
                    if (fullGraph.path.size <= 15 || pathType == PathType.PARTIAL_TRACE || pathType == PathType.FULLTRACE) {
                        if (!isDisablePath(fullGraph, followTrace)) {
                            if (isArrived(transition, finalTarget, stopWhenHavingUnexercisedAction, includeWTG,atuamf = atuaMF)) {
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

        private fun IsCurrentPathStartingWithLaunchOrReset(ancestorEdgeId: Int?, traversedEdges: HashMap<Int, Pair<AbstractTransition, Stack<Window>>>, ancestorAbstractStates: ArrayList<AbstractState>, pathTracking: HashMap<Int, Int>): Boolean {
            var ancestorEdgeId1 = ancestorEdgeId
            var startedWithLaunchOrResetAction = false
            while (ancestorEdgeId1 != null) {
                val ancestorEdge = traversedEdges.get(ancestorEdgeId1)!!.first
                if (ancestorEdge.abstractAction.isLaunchOrReset()) {
                    startedWithLaunchOrResetAction = true
                    break
                }
                val sourceAbstractState = ancestorEdge.source
                if (!ancestorAbstractStates.any { it.window == sourceAbstractState.window })
                    ancestorAbstractStates.add(sourceAbstractState)
                ancestorEdgeId1 = pathTracking.get(ancestorEdgeId1)
            }
            return startedWithLaunchOrResetAction
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
            if (pathType != PathType.PARTIAL_TRACE && pathType != PathType.FULLTRACE ) {
                return true
            }
            if (prevEdgeId == null)
                return true
            if (edge.label.isImplicit)
                return false
            if (edge.label.abstractAction.actionType == AbstractActionType.RESET_APP)
                return true
            if (!edge.label.tracing.any { targetTraces.contains(it.first) })
                return false
            var prevTransitionId: Int? = prevEdgeId
            var prevTransition: AbstractTransition? = traversedEdges.get(prevTransitionId)!!.first
            var currentTransition = edge.label
            val validCurrentTracing = HashMap<Pair<Int,Int>,Pair<Int,Int>>()
            // initiate valideNextTracing as prevTransition's tracing
            prevTransition!!.tracing.forEach {
                validCurrentTracing.put(it,it)
            }
            if (prevTransition!!.abstractAction.actionType == AbstractActionType.RESET_APP) {
                if (edge.label.tracing.any { it.second==1 }) {
                    return true
                }
                return false
            }
            if (pathType==PathType.PARTIAL_TRACE && prevTransition!!.abstractAction.actionType == AbstractActionType.LAUNCH_APP) {
                if (edge.label.tracing.isNotEmpty())
                    return true
                return false
            }
            // trace backward to remove nonValidTracing
            while (prevTransitionId!=null && validCurrentTracing.isNotEmpty()) {
                prevTransitionId = pathTracking.get(prevTransitionId)
                if (prevTransitionId == null)
                    break
                prevTransition = traversedEdges.get(prevTransitionId)!!.first
                if (prevTransition!!.abstractAction.actionType == AbstractActionType.RESET_APP) {
                    break
                }
                if (prevTransition!!.abstractAction.actionType == AbstractActionType.LAUNCH_APP
                        && pathType == PathType.PARTIAL_TRACE) {
                    break
                }
                val kill = HashSet<Pair<Int,Int>>()
                val new = HashMap<Pair<Int,Int>,Pair<Int,Int>>()
                validCurrentTracing.forEach { currentTrace, backwardTrace ->
                    val backwardCompatible = prevTransition!!.tracing.filter { it.first == currentTrace.first
                            && it.second == backwardTrace.second-1}
                    if (backwardCompatible.isEmpty())
                        kill.add(currentTrace)
                    else
                    {
                        new.put(currentTrace,backwardCompatible.single())
                    }
                }
                validCurrentTracing.clear()
                validCurrentTracing.putAll(new)
            }

            if (edge.label.tracing.any {
                        validCurrentTracing.keys.map { it.first }.contains(it.first)
                                && validCurrentTracing.keys.map { it.second+1 }.contains(it.second)
                    }) {
                return true
            } else {
                return false
            }

            val traceIds = HashSet<Int>()
            traceIds.addAll(edge.label.tracing.map { it.first })
            while (prevTransitionId!=null && traceIds.isNotEmpty()) {
                val prevTransition = traversedEdges.get(prevTransitionId)?.first
                if (prevTransition == null)
                    throw Exception("Prev transition is null")
                if (prevTransition.abstractAction.actionType == AbstractActionType.RESET_APP) {
                    if (pathType == PathType.PARTIAL_TRACE || currentTransition.tracing.any { it.second == 1})
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

        private fun isArrived(transition: AbstractTransition, finalTarget: AbstractState, stopWhenHavingUnexercisedAction: Boolean, useVirtualAbstractState: Boolean,atuamf: org.atua.modelFeatures.ATUAMF): Boolean {
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

        private fun includingRotateUIOrNot(autautMF: org.atua.modelFeatures.ATUAMF, it: Edge<AbstractState, AbstractTransition>): Boolean {
            if (autautMF.appRotationSupport)
                return true
            return it.label.abstractAction.actionType != AbstractActionType.ROTATE_UI
        }

        private fun isTheSamePrevWindow(prevWindow1: Window?, prevWindow2: Window?): Boolean {
            return (
                    (prevWindow1 != null && prevWindow2 == prevWindow1)
                            || prevWindow1 == null || prevWindow2 == null)
        }

        private fun createTransitionPath(autautMF: org.atua.modelFeatures.ATUAMF, pathType: PathType, destination: AbstractState, lastTransition: AbstractTransition, prevEdgeId: Int?,
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
                    // This incorrect transition will be automatically removed
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
                if (edge1.abstractAction == edge2.abstractAction && edge1.dependentAbstractStates.intersect(edge2.dependentAbstractStates).isNotEmpty()) {
                    continue
                } else {
                    samePrefix = false
                    break
                }
            }
           /* if (samePrefix && pathTraverser.finalStateAchieved()) {
                if (type == DisablePathType.UNAVAILABLE_ACTION) {
                    samePrefix = false
                }
            }*/
            return samePrefix
        }
    }

    enum class PathType {
        INCLUDE_INFERED,
        NORMAL,
        NORMAL_RESET,
        FULLTRACE,
        PARTIAL_TRACE,
        WTG,
        ANY
    }
}

enum class DisablePathType {
    UNAVAILABLE_ACTION,
    UNACHIEVABLE_FINAL_STATE
}
