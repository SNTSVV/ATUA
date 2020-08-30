package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.strategy.autaut.AutAutTestingStrategy
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ExerciseTargetComponentTask private constructor(
        regressionWatcher: AutAutMF,
        autAutTestingStrategy: AutAutTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean)
    : AbstractStrategyTask(autAutTestingStrategy, regressionWatcher, delay, useCoordinateClicks){

    private var recentChangedSystemConfiguration: Boolean = false
    var environmentChange: Boolean = false
    val eventList:  ArrayList<AbstractAction> = ArrayList()
    var chosenAbstractAction: AbstractAction? = null
    var currentAbstractState: AbstractState? = null
    var fillingData = false
    var dataFilled = false
    var randomRefillingData = false
    private var prevAbstractState: AbstractState?=null
    var originalEventList: ArrayList<AbstractAction> = ArrayList()
    private val fillDataTask = PrepareContextTask.getInstance(regressionTestingMF,autAutTestingStrategy, delay, useCoordinateClicks)

    override fun chooseRandomOption(currentState: State<*>) {
        log.debug("Do nothing")
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        return false
    }
    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (eventList.isNotEmpty()) {
            return false
        }
        if (isCameraOpening(currentState)) {
            return false
        }
        return true
    }

    private var mainTaskFinished:Boolean = false
    private val randomExplorationTask = RandomExplorationTask(regressionWatcher,autAutTestingStrategy, delay,useCoordinateClicks,true,3)
    private val candidateWidgetsMap = HashMap<State<*>, List<Widget>>()
    private val openNavigationBar = OpenNavigationBarTask.getInstance(regressionWatcher,autAutTestingStrategy,delay, useCoordinateClicks)
    override fun initialize(currentState: State<*>) {
        reset()
        randomExplorationTask.fillingData=false
        mainTaskFinished = false
        currentAbstractState = regressionTestingMF.getAbstractState(currentState)
        initializeExtraTasks(currentState)
        eventList.addAll(autautStrategy.phaseStrategy.getCurrentTargetEvents(currentState))
        originalEventList.addAll(eventList)
    }

    private fun initializeExtraTasks(currentState: State<*>) {
        extraTasks.clear()
//        if (openTargetDialogTask.isAvailable(currentState)) {
//            extraTasks.add(openTargetDialogTask.also { it.initialize(currentState) })
//        }
//        if (openOptionMenuTask.isAvailable(currentState)) {
//            extraTasks.add(openOptionMenuTask.also { it.initialize(currentState) })
//        }
//        if (openContextMenuTask.isAvailable(currentState)) {
//            extraTasks.add(openContextMenuTask.also { it.initialize(currentState) })
//        }
        extraTasks.add(openNavigationBar)
        extraTasks.add(randomExplorationTask)
        currentExtraTask = null
    }

    override fun reset() {
        extraTasks.clear()
        eventList.clear()
        originalEventList.clear()
        currentExtraTask = null
        mainTaskFinished = false
        prevAbstractState = null
        dataFilled = false
        fillingData = false
        randomRefillingData = false
        recentChangedSystemConfiguration = false
        environmentChange = false
    }

    override fun isAvailable(currentState: State<*>): Boolean {
        reset()
        log.info("Checking if current state contains target Events")
        eventList.addAll(autautStrategy.phaseStrategy.getCurrentTargetEvents(currentState))
        originalEventList.addAll(eventList)
        if (eventList.isNotEmpty()){
            log.info("Current node has ${eventList.size} target event(s).")
            return true
        }
        log.info("Current node has no target event.")
        return false
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        //check if we can encounter any target component in current state
        var candidates= ArrayList<Widget>()
        candidates.addAll(regressionTestingMF.getRuntimeWidgets(chosenAbstractAction!!.widgetGroup!!,currentAbstractState!!, currentState))
        if (candidates.isNotEmpty())
        {
            return candidates
        }
        //if have target widget
        if (candidates.isNotEmpty())
        {
            return candidates
        }
        regressionTestingMF.addUnreachableTargetComponentState(currentState)
        return emptyList()
    }

    fun StateGraphMF.dfsPath (source: State<*>, destination: State<*>): List<Edge<State<*>, Interaction<*>>>{
        val resultPath = ArrayList<Edge<State<*>, Interaction<*>>>()
        val processingEdges = Stack<Edge<State<*>,Interaction<*>>>()
        val vertexStack = Stack<State<*>>()
        val currentNode = regressionTestingMF.getAbstractState(source)
        vertexStack.push(source)
        val visitedVertex = ArrayList<State<*>>()
        while (vertexStack.isNotEmpty())
        {
            var currentVertex: State<*> = vertexStack.peek()
            if (currentVertex.uid == destination.uid)
            {
                //found path
                break
            }
            if (visitedVertex.contains(currentVertex))
            {
                val edge = processingEdges.firstOrNull{
                    it.source.data == currentVertex
                }
                if (edge == null) //No more edge
                {
                    //Remove this vertex
                    vertexStack.pop()
                    //Also remove edge to this vertex
                    resultPath.remove(resultPath.last())
                }
                else
                {
                    //Have edge to process
                    //Add this edge as the path
                    resultPath.add(edge)
                    //Push the new vertex
                    vertexStack.push(edge.destination!!.data)
                }
            }
            else
            {
                //New vertex
                //Get all edges
                visitedVertex.add(currentVertex)
                val nextEdges =  this.edges(currentVertex).filter { it.destination != null
                        && regressionTestingMF.getAbstractState(it.destination!!.data) == currentNode
                }
                if (nextEdges.isEmpty())
                {
                    //Cannot continue, so go back
                    //Remove this vertex
                    vertexStack.pop()
                    //Also remove edge to this vertex
                    resultPath.remove(resultPath.last())
                }
                else
                {
                    //Add to processingEdges
                    nextEdges.forEach {
                        processingEdges.push(it)
                    }

                }
            }
        }
        return resultPath
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        executedCount++
        val currentAbstractState = regressionTestingMF.getAbstractState(currentState)!!
        if (currentAbstractState != null) {
            prevAbstractState = currentAbstractState
        }
        if (isCameraOpening(currentState)) {
            randomExplorationTask.chooseAction(currentState)
        }
        if (!fillingData && random.nextBoolean()) {
            //reset input
            fillingData = false
            dataFilled = false
        }
        randomExplorationTask.isClickedShutterButton = false
        if (!recentChangedSystemConfiguration && environmentChange && random.nextBoolean()) {
            recentChangedSystemConfiguration = true
            if (regressionTestingMF.havingInternetConfiguration(currentAbstractState.window)) {
                if (random.nextInt(4)<3)
                    return GlobalAction(ActionType.EnableData).also {
                        regressionTestingMF.internetStatus = true
                    }
                else
                    return GlobalAction(ActionType.DisableData).also {
                        regressionTestingMF.internetStatus = false
                    }
            } else {
                return GlobalAction(ActionType.EnableData).also {
                    regressionTestingMF.internetStatus = false
                }
            }
        }
        if (fillingData && !fillDataTask.isTaskEnd(currentState))
            return fillDataTask.chooseAction(currentState)
        if (fillingData) {
            fillingData = false
            dataFilled = true
        }
        if (!dataFilled && fillDataTask.isAvailable(currentState))
        {
            fillDataTask.initialize(currentState)
            fillingData = true
            return fillDataTask.chooseAction(currentState)
        }
       /* if (randomRefillingData
                && originalEventList.size > eventList.size
                && fillDataTask.isAvailable(currentState)) {
            fillDataTask.initialize(currentState)
            fillingData = true
            return fillDataTask.chooseAction(currentState)
        }*/
        //TODO check eventList is not empty
        if (eventList.isEmpty()) {
            log.debug("No more target event. Random exploration.")
            return randomExplorationTask.chooseAction(currentState)
        }

        if (!eventList.any { it.widgetGroup!=null && it.widgetGroup.attributePath.isInputField() }) {
            chosenAbstractAction = eventList.filterNot { it.widgetGroup!=null && it.widgetGroup.attributePath.isInputField() }.random()
        } else {
            chosenAbstractAction = eventList.random()
        }

        eventList.remove(chosenAbstractAction!!)
        dataFilled = false
        fillingData = false
        if (chosenAbstractAction!=null)
        {
            log.info("Exercise Event: ${chosenAbstractAction!!.actionName}")
            var chosenWidget: Widget? = null
            if (chosenAbstractAction!!.widgetGroup!=null)
            {
                val candidates = chooseWidgets(currentState)
                chosenWidget = candidates.firstOrNull()
                if (chosenWidget==null)
                {
                    log.debug("No widget found. Choose another event.")
                    return chooseAction(currentState)
                }
                log.info("Choose Action for Widget: $chosenWidget")
            }
            val recommendedAction = chosenAbstractAction!!.actionName
            log.debug("Target action: $recommendedAction")
            val chosenAction = when (chosenAbstractAction!!.actionName)
            {
                "CallIntent" -> chooseActionWithName(recommendedAction,chosenAbstractAction!!.extra, null, currentState,chosenAbstractAction)
                "RotateUI" -> chooseActionWithName(recommendedAction,90,null,currentState,chosenAbstractAction)
                else -> chooseActionWithName(recommendedAction, chosenAbstractAction!!.extra?:"", chosenWidget, currentState,chosenAbstractAction)
            }
            if (chosenAction == null)
            {
                //regressionTestingMF.registerTriggeredEvents(chosenEvent!!)
                if (eventList.isNotEmpty())
                {
                    return chooseAction(currentState)
                }
                log.debug("Cannot get action for this widget.")
                return ExplorationAction.pressBack()
            }
            else
            {
                regressionTestingMF.lastExecutedAction = chosenAbstractAction
                autautStrategy.phaseStrategy.registerTriggeredEvents(chosenAbstractAction!!,currentState)
                regressionTestingMF.isAlreadyRegisteringEvent = true
                return chosenAction
            }
        }
        return ExplorationAction.pressBack()

    }

    companion object
    {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        private var instance: ExerciseTargetComponentTask? = null
        var executedCount:Int = 0
        fun getInstance(regressionWatcher: AutAutMF,
                        autAutTestingStrategy: AutAutTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): ExerciseTargetComponentTask {
            if (instance == null)
            {
                instance = ExerciseTargetComponentTask(regressionWatcher, autAutTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }



}