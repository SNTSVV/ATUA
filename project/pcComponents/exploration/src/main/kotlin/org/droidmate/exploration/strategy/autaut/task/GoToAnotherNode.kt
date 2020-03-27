package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.ArrayList

open class GoToAnotherNode protected constructor(
         regressionWatcher: RegressionTestingMF,
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean) : AbstractStrategyTask(regressionTestingStrategy, regressionWatcher, delay, useCoordinateClicks) {

    protected var mainTaskFinished:Boolean = false
    protected var prevState: State<*>?=null
    protected var prevAbState: AbstractState?=null
    protected val randomExplorationTask = RandomExplorationTask(regressionWatcher,regressionTestingStrategy,delay,useCoordinateClicks,true,1)
    protected var isFillingText: Boolean = false
    protected var currentEdge: Edge<AbstractState, AbstractInteraction>?=null
    protected var expectedNextAbState: AbstractState?=null
    protected var currentPath: TransitionPath? = null
    protected val possiblePaths = ArrayList<TransitionPath>()

    override fun chooseRandomOption(currentState: State<*>) {
        currentPath = possiblePaths.random()
        possiblePaths.remove(currentPath!!)
        expectedNextAbState=currentPath!!.root.data
        val destination = currentPath!!.getFinalDestination()
        log.debug("Try to reach ${destination.staticNode}")
        mainTaskFinished = false
    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (mainTaskFinished)
            return true
        //if app reached the final destination
        val currentAppState = regressionTestingMF.getAbstractState(currentState)
        if (currentAppState == currentPath!!.getFinalDestination())
        {
            return true
        }
        //if this is the end of the path
        if (expectedNextAbState == currentPath!!.getFinalDestination() )
        {
           return true
        }
        //this is in the middle of the path
        //if currentNode is expectedNextNode
        if (expectedNextAbState!=null) {
            if (!isReachExpectedNode(currentState)) {
                log.debug("Fail to reach expected node")
                addIncorrectPath()
                return true
            }
            else
            {
                return false
            }
        }else
        {
            //something wrong, should end task
            return true
        }
    }

    private fun isReachExpectedNode(currentState: State<*>):Boolean {
        val currentAbState = regressionTestingMF.getAbstractState(currentState)
        if (expectedNextAbState!!.activity != currentAbState!!.activity)
            return false
        if (expectedNextAbState == currentAbState)
        {
            return true
        }
        else
        {
            //if next action is feasible
            val nextEdge = currentPath!!.edges(expectedNextAbState!!).firstOrNull()
            if (nextEdge == null)
                return false
            val nextEvent = nextEdge.label
            if (nextEvent.abstractAction.widgetGroup == null)
                return true
            val widget = nextEvent.abstractAction.widgetGroup
            if (regressionTestingMF.getRuntimeWidgets(widget,currentState).isNotEmpty())
                return true
            return false
        }


    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        if (currentPath!!.root.data.activity == regressionTestingMF.getAbstractState(currentState)!!.activity
                && possiblePaths.size > 0) {//still in the source activity
            log.debug("Can change to another option.")
            return true
        }
        return false
    }

    override fun initialize(currentState: State<*>) {
        randomExplorationTask.fillData=true
        chooseRandomOption(currentState)
    }

    override fun reset() {
        possiblePaths.clear()
        currentPath = null
    }

    override fun isAvailable(currentState: State<*>): Boolean {
        reset()
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }
        return false
    }



    open protected fun initPossiblePaths(currentState: State<*>) {
        possiblePaths.clear()
        possiblePaths.addAll(regressionTestingStrategy.phaseStrategy.getPathsToOtherWindows(currentState))
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        if (currentEdge!!.label.abstractAction.widgetGroup==null)
        {
            return emptyList()
        }
        else
        {
            return regressionTestingMF.getRuntimeWidgets(currentEdge!!.label.abstractAction.widgetGroup!!, currentState)
        }
    }
    open fun increaseExecutedCount(){
        executedCount++
    }
    override fun chooseAction(currentState: State<*>): ExplorationAction {
        increaseExecutedCount()
        if(currentExtraTask!=null)
            return currentExtraTask!!.chooseAction(currentState)
        var nextNode = expectedNextAbState
        if (!isFillingText)
        {
            prevState = currentState
            prevAbState = expectedNextAbState
        }
        if (currentPath == null || expectedNextAbState == null)
            return randomExplorationTask.chooseAction(currentState)
        log.info("Destination: ${currentPath!!.getFinalDestination().staticNode}")
        if (expectedNextAbState!!.activity == regressionTestingMF.getAbstractState(currentState)!!.activity) {
            if (!isFillingText)
            {
                currentEdge = currentPath!!.edges(expectedNextAbState!!).firstOrNull()
            }
            if (currentEdge != null) {
                regressionTestingMF.setTargetNode(currentEdge!!.destination!!.data)
                nextNode = currentEdge!!.destination!!.data
                expectedNextAbState = nextNode
                log.info("Next expected node: ${nextNode.staticNode}")
                //log.info("Event: ${currentEdge!!.label.abstractAction.actionName} on ${currentEdge!!.label.abstractAction.widgetGroup}")
                //Fill text input (if required)
                if (currentPath!!.edgeConditions.containsKey(currentEdge!!) && currentEdge!!.label.abstractAction.actionName != "Swipe")
                {
                    val actionList = ArrayList<ExplorationAction>()
                    val textInputData = currentPath!!.edgeConditions[currentEdge!!]!!.filter { it.key.attributePath.isInputField() }
                    textInputData.forEach {
                        if (regressionTestingMF.getRuntimeWidgets(it.key,currentState).isNotEmpty())
                        {
                            val textInputWidget = regressionTestingMF.getRuntimeWidgets(it.key,currentState).random()
                            if (textInputWidget.text!=it.value)
                            {
                                actionList.add(textInputWidget.setText(it.value))
                            }
                        }
                    }
                    if (actionList.isNotEmpty())
                    {
                        isFillingText = true
                        return ActionQueue(actionList,0)
                    }
                }
                isFillingText = false
                if (currentEdge!!.label.abstractAction.actionName.isPressMenu()) {
                    return pressMenuOrClickMoreOption(currentState)
                }
                if (currentEdge!!.label.abstractAction.widgetGroup != null) {
                    val widgets = chooseWidgets(currentState)
                    if (widgets.isNotEmpty()) {

                        val candidates = runBlocking { getCandidates(widgets) }
                        val chosenWidget = candidates[random.nextInt(candidates.size)]
                        val actionName = currentEdge!!.label.abstractAction.actionName
                        val actionCondition = currentPath!!.edgeConditions[currentEdge!!]
                        return chooseActionWithName(actionName, actionCondition?.get(currentEdge!!.label.abstractAction.widgetGroup!!)?:"", chosenWidget, currentState) ?: ExplorationAction.pressBack()
                    } else {
                        expectedNextAbState = prevAbState
                        addIncorrectPath()
                        if (hasAnotherOption(currentState)) {
                            chooseRandomOption(currentState)
                            return chooseAction(currentState)
                        }
                        else
                        {
                            log.debug("Try all options but can not get any widget, finish task.")
                            return randomExplorationTask.chooseAction(currentState)
                        }
                    }
                }
                else
                {
                    val action = currentEdge!!.label.abstractAction.actionName
                    //val actionCondition = currentPath!!.edgeConditions[currentEdge!!]
                    if (action == "CallIntent")
                        return chooseActionWithName(action,currentEdge!!.label.abstractAction.extra, null, currentState) ?: ExplorationAction.pressBack()
                    else if (action == "RotateUI")
                    {
                        val currentRotation = regressionTestingMF.currentRotation
                        val targetRotation = currentEdge!!.destination!!.data.rotation
                        val rotation = (targetRotation - currentRotation)%360
                        return chooseActionWithName(action,rotation,null,currentState)?:ExplorationAction.pressBack()
                    }
                    else {
                        return chooseActionWithName(action, "", null, currentState) ?: ExplorationAction.pressBack()
                    }
                }
                log.debug("Try all options but can not get any widget, finish task.")
//                mainTaskFinished = true
                return randomExplorationTask.chooseAction(currentState)
            }
        }
        if(hasAnotherOption(currentState))
        {
            chooseRandomOption(currentState)
            return chooseAction(currentState)
        }
        return randomExplorationTask.chooseAction(currentState)
    }



    protected fun addIncorrectPath() {
        if (currentEdge!=null)
            regressionTestingMF.addDisablePathFromState(currentPath!!,currentEdge!!)

    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }

        var instance: GoToAnotherNode? = null
        var executedCount:Int = 0
        fun getInstance(regressionWatcher: RegressionTestingMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): GoToAnotherNode {
            if (instance == null) {
                instance = GoToAnotherNode(regressionWatcher, regressionTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }
}