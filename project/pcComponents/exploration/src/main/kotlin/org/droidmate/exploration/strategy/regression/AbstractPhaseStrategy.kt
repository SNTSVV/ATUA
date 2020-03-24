package org.droidmate.exploration.strategy.regression

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.isClick
import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.regression.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.regression.staticModel.*
import org.droidmate.exploration.strategy.regression.task.AbstractStrategyTask
import org.droidmate.exploration.strategy.regression.task.RandomExplorationTask
import org.droidmate.explorationModel.interaction.State
import kotlin.random.Random

abstract class AbstractPhaseStrategy(
        val regressionTestingStrategy: RegressionTestingStrategy,
        val delay: Long,
        val useCoordinateClicks: Boolean
) {
    lateinit var phaseState: PhaseState
    lateinit var regressionTestingMF: RegressionTestingMF

    var strategyTask: AbstractStrategyTask? = null
    abstract fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction
    internal fun dealWithCamera(eContext: ExplorationContext<*,*,*>, currentState: State<*>): ExplorationAction {
        val gotItButton = currentState.widgets.find { it.text.toLowerCase().equals("got it") }
        if (gotItButton != null)
            return gotItButton.click()
        val shutterbutton = currentState.actionableWidgets.find { it.resourceId.contains("shutter_button") }
        if (shutterbutton!=null)
        {
            val clickActions = shutterbutton.availableActions(delay, useCoordinateClicks).filter { it.name.isClick()}
            if (clickActions.isNotEmpty())
                return clickActions.random()
        }
        val doneButton = currentState.actionableWidgets.find { it.resourceId.contains("done_button") }
        if (doneButton!=null)
        {
            val clickActions = doneButton.availableActions(delay, useCoordinateClicks).filter { it.name.isClick()}
            if (clickActions.isNotEmpty())
                return clickActions.random()
        }
        return tryRandom(eContext)
    }
    abstract fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath>
    abstract fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath>
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


    fun findPathToTargetComponentByBFS(currentState: State<*>, root: AbstractState, parentNodes: List<AbstractState>, finalTarget: AbstractState
                                       , allPaths: ArrayList<TransitionPath>, includeBackEvent: Boolean
                                       , childParentMap: HashMap<AbstractState,Pair<AbstractState, AbstractInteraction>?>, level: Int) {
        if (parentNodes.isEmpty())
            return
        val nextLevelNodes = ArrayList<AbstractState>()
        for (source in parentNodes)
        {

            if (includeBackEvent)
            {
                //ingore any similar state that pressback can lead to homescreen
                if (!regressionTestingMF.isPressBackCanGoToHomescreen(source)) {
                    //if backNodes is empty -> no avaible information, so try get back state from ancestors
                    val backNodes = ArrayList<AbstractState>()
                    if (backNodes.isEmpty())
                    {
                        val backCandidates = regressionTestingMF.abstractTransitionGraph.edges().filter {
                            it.destination!=null
                                    && it.destination!!.data == source
                                    && !it.label.abstractAction.actionName.isPressBack()
                        }
                        backCandidates.forEach {
                            val ancestor = it.source.data
                            if (!backNodes.contains(ancestor))
                            {
                                backNodes.add(ancestor)
                            }
                        }
                    }

                    if (backNodes.isEmpty())
                    {

                        val backCandiates = regressionTestingMF.abstractTransitionGraph.edges(source).filter {
                            it.label.abstractAction.actionName.isPressBack()
                                    && it.destination!=null
                                    && it.destination!!.data.staticNode !is WTGLauncherNode
                                    && it.destination!!.data.staticNode !is WTGOutScopeNode
                        }
                        backCandiates.forEach {
                            val ancestor = it.destination!!.data
                            if (!backNodes.contains(ancestor))
                            {
                                backNodes.add(ancestor)
                            }
                        }
                    }

                    backNodes.forEach { ancestor ->
                        //Avoid loop
                        if (!childParentMap.containsKey(ancestor)) {
                            var backEdge = regressionTestingMF.abstractTransitionGraph.edges(source)
                                    .find { it.label.abstractAction.actionName.isPressBack()
                                            && it.destination?.data?.staticNode==ancestor.staticNode}
                            if (backEdge!=null)
                            {
                                val backEvent = backEdge.label
                                val isDisableEdge: Boolean = regressionTestingMF.checkIsDisableEdge(backEdge)
                                if (!isDisableEdge) {
                                    childParentMap.put(ancestor,Pair(source,backEvent))
                                    if (ancestor == finalTarget) {
                                        val fullPath = createTransitionPath(ancestor, root,childParentMap)
                                        allPaths.add(fullPath)
                                        regressionTestingMF.registerTransitionPath(root, finalTarget, fullPath)
                                        return
                                    } else {
                                        nextLevelNodes.add(ancestor)
                                    }
                                }
                            }
//                        if (backEvent == null) {
//                            backEvent = StaticEvent(EventType.implicit_back_event, ArrayList<String>(),
//                                    null, source)
//                            transitionGraph.add(source, ancestor, backEvent)
//                        }
                        }
                    }
                }

            }
            val possibleTransitions = regressionTestingMF.abstractTransitionGraph.edges(source).filter{
                it.destination!=null
                        && it.source != it.destination
                        && it.destination!!.data.staticNode !is WTGOutScopeNode
                        && it.destination!!.data.staticNode !is WTGLauncherNode
                        && it.destination!!.data.staticNode !is WTGFakeNode
                        && it.destination!!.data.staticNode !is WTGOpeningKeyboardNode
            }.filter { !regressionTestingMF.checkIsDisableEdge(it) }
            val processedTransition = ArrayList<Edge<AbstractState, AbstractInteraction>>()

            possibleTransitions.filter {
                !it.label.abstractAction.actionName.isPressBack()
            }.groupBy({it.label.abstractAction},{it}).forEach { _, u ->
                val reliableTransition =  u.filter { !it.label.isImplicit }
                val implicitTransitions = u.filter { it.label.isImplicit }
                /*if (reliableTransition.isNotEmpty())
                {
                    if (Random.nextBoolean())
                    {
                        processedTransition.addAll(implicitTransitions)
                    }
                }
                else
                {
                    processedTransition.addAll(implicitTransitions)
                }*/
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
                        nextLevelNodes.add(nextNode)
                    }
                }
            }
        }
        findPathToTargetComponentByBFS(currentState, root, nextLevelNodes, finalTarget, allPaths, includeBackEvent, childParentMap, level+1)
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
}