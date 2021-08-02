package org.droidmate.exploration.strategy.atua.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.rotate
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.dstg.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.ewtg.*
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.calm.ModelBackwardAdapter
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.calm.modelReuse.ModelVersion
import org.droidmate.exploration.strategy.atua.ATUATestingStrategy
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList

open class GoToAnotherWindow constructor(
        autautMF: ATUAMF,
        atuaTestingStrategy: ATUATestingStrategy,
        delay: Long, useCoordinateClicks: Boolean) : AbstractStrategyTask(atuaTestingStrategy, autautMF, delay, useCoordinateClicks) {

    private var tryOpenNavigationBar: Boolean = false
    private var tryScroll: Boolean = false
    protected var mainTaskFinished:Boolean = false
    protected var prevState: State<*>?=null
    protected var prevAbState: AbstractState?=null
    protected var randomExplorationTask: RandomExplorationTask = RandomExplorationTask(this.atuaMF,atuaTestingStrategy,delay,useCoordinateClicks,true,1)
    private val fillDataTask = PrepareContextTask(this.atuaMF, atuaTestingStrategy, delay, useCoordinateClicks)

    var isFillingText: Boolean = false
    //protected var currentEdge: AbstractTransition?=null
    protected var expectedNextAbState: AbstractState?=null
    var currentPath: TransitionPath? = null
    val possiblePaths = ArrayList<TransitionPath>()
    var pathTraverser: PathTraverser? = null

    var destWindow: Window? = null
    var useInputTargetWindow: Boolean = false
    var retryTimes: Int = 0
    var isTarget: Boolean = false

    val fillingDataActionList = Stack<Pair<ExplorationAction,Widget>>()
    init {
        randomExplorationTask.isPureRandom = true
    }
    override fun chooseRandomOption(currentState: State<*>) {
        val notIncludeResetPaths = possiblePaths.filter {
            it.path.values.all { it.abstractAction.actionType != AbstractActionType.RESET_APP }
        }
        if (notIncludeResetPaths.isEmpty())
            currentPath = possiblePaths.random()
        else
            currentPath = notIncludeResetPaths.random()
      //  currentEdge = null
        pathTraverser = PathTraverser(currentPath!!)
        destWindow = currentPath!!.getFinalDestination().window
        possiblePaths.remove(currentPath!!)
        expectedNextAbState=currentPath!!.root
        mainTaskFinished = false
        isFillingText = false
        tryOpenNavigationBar = false
        tryScroll = false
    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        // update testing path
        if (atuaMF.prevAbstractStateRefinement>0)
            return true
        if (pathTraverser==null)
            return true
        if (mainTaskFinished)
            return true
        if (currentPath == null)
            return true
        val currentAppState = atuaMF.getAbstractState(currentState)!!
        if (isWindowAsTarget && currentAppState.window == destWindow) {
            return true
        }
        if (isFillingText)
            return false
        //if app reached the final destination

        if (pathTraverser!!.finalStateAchieved()) {
            if (currentAppState == currentPath!!.getFinalDestination()) {
                return true
            }
        }
        //if currentNode is expectedNextNode
        if (isExploration) {
            if (currentAppState.window == destWindow) {
                if (currentAppState.getUnExercisedActions(currentState, atuaMF).isNotEmpty()) {
                    return true
                }
            }
        }
        if (expectedNextAbState!=null) {
            if (!isReachExpectedState(currentState)) {
                // Try another path if current state is not target node
                expectedNextAbState = pathTraverser!!.getCurrentTransition()?.dest
                log.debug("Fail to reach $expectedNextAbState" )
                addIncorrectPath(currentAppState)
                if (pathTraverser!!.finalStateAchieved() && currentPath!!.destination.window == currentAppState.window)
                    return true
                val retryBudget = if (includeResetAction) {
                    (5 * atuaStrategy.scaleFactor).toInt()
                } else
                    (3 * atuaStrategy.scaleFactor).toInt()
                if (retryTimes < retryBudget && currentPath!!.pathType!=PathFindingHelper.PathType.FULLTRACE) {
//                    //TODO check currentPath is valid
//                    if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(expectedNextAbState!!)) {
//                        initPossiblePaths(currentState,true)
//                        log.debug("The expected state is removed by the refinement. Reidentify paths to the destination.")
//                    } else {
//
//                    }
                    retryTimes += 1
                    val currentEdge = pathTraverser!!.getCurrentTransition()
                    /*if (currentEdge!!.abstractAction.actionType == AbstractActionType.PRESS_BACK) {
                        initPossiblePaths(currentState,true)
                        log.debug("Reidentify paths to the destination.")
                    } else {
                    }*/
                    log.debug("Reidentify paths to the destination.")
                    initPossiblePaths(currentState, true)
                    if (possiblePaths.isNotEmpty()) {
                        initialize(currentState)
                        log.debug(" Paths is not empty")
                        return false
                    }
                }
                return true
            }
            else
            {
                expectedNextAbState = pathTraverser!!.getCurrentTransition()!!.dest
                if (pathTraverser!!.finalStateAchieved()) {
                    return true
                }
//                // Try find another available shorter path
//               if (currentPath!!.pathType==PathFindingHelper.PathType.TRACE
//                       || currentPath!!.pathType==PathFindingHelper.PathType.RESET) {
//                   initPossiblePaths(currentState, true)
//                   val currentPathLeft = currentPath!!.path.size - (pathTraverser!!.latestEdgeId!! + 1)
//                   if (possiblePaths.any { it.path.size < currentPathLeft-1 }) {
//                       possiblePaths.removeIf { it.path.size >= currentPathLeft-1 }
//                       initialize(currentState)
//                   }
//               }
                return false
            }
        } else {
            //something wrong, should end task
            return true
        }
    }

     fun isReachExpectedState(currentState: State<*>):Boolean {
         var reached = false
         val currentAbState = atuaMF.getAbstractState(currentState)!!
         var expectedAbstractState = pathTraverser!!.getCurrentTransition()!!.dest
         if (expectedAbstractState == currentAbState)
             return true
         if (pathTraverser!!.finalStateAchieved() && expectedAbstractState.isRequestRuntimePermissionDialogBox)
             return true
         if (expectedAbstractState.isRequestRuntimePermissionDialogBox) {
             pathTraverser!!.next()
             expectedAbstractState = pathTraverser!!.getCurrentTransition()!!.dest
         }
         if (expectedAbstractState == currentAbState)
             return true
         if (expectedAbstractState.modelVersion!=ModelVersion.BASE
                 && !AbstractStateManager.INSTANCE.ABSTRACT_STATES.contains(expectedAbstractState)) {
             val equivalentAbstractState = AbstractStateManager.INSTANCE.ABSTRACT_STATES.find {
                 it.hashCode == expectedAbstractState!!.hashCode
             }
             if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                 return true
             } else {
                 return false
             }
         }
         if (pathTraverser!!.finalStateAchieved()) {
             if (expectedAbstractState is VirtualAbstractState
                     && expectedAbstractState.window == currentAbState.window) {
                 return true
             }
             return false
         }

         val tmpPathTraverser = PathTraverser(currentPath!!)
         tmpPathTraverser.latestEdgeId = pathTraverser!!.latestEdgeId
         while (!tmpPathTraverser.finalStateAchieved()) {
             val currentTransition = tmpPathTraverser.getCurrentTransition()
             if (currentTransition == null)
                 break
             val expectedAbstractState1 = currentTransition.dest

             if (expectedAbstractState1!!.window != currentAbState!!.window) {
                 tmpPathTraverser.next()
                 continue
             }
             if (expectedAbstractState1 == currentAbState) {
                 reached = true
                 break
             }
             if (expectedAbstractState1.modelVersion == ModelVersion.BASE && expectedAbstractState1.guiStates.isEmpty()) {
                 // check the current state is backward equivalent to expectedAbstractState
                 if (ModelBackwardAdapter.instance.backwardEquivalentAbstractStateMapping.containsKey(currentAbState)) {
                     val backwardEquivalences = ModelBackwardAdapter.instance.backwardEquivalentAbstractStateMapping.get(currentAbState)!!
                     if (backwardEquivalences.contains(expectedAbstractState1)) {
                         reached = true
                         val toUpdateTransition = tmpPathTraverser.transitionPath.path[tmpPathTraverser.latestEdgeId!!+1]
                         if (toUpdateTransition!=null && toUpdateTransition.modelVersion == ModelVersion.BASE) {
                             if (ModelBackwardAdapter.instance.backwardEquivalentAbstractTransitionMapping.containsKey(toUpdateTransition)) {
                                 val equivalentTransition = ModelBackwardAdapter.instance.backwardEquivalentAbstractTransitionMapping.get(toUpdateTransition)!!
                                 currentPath!!.path.put(tmpPathTraverser.latestEdgeId!!+1,equivalentTransition.first())
                             }
                         }
                         break
                     }
                 }
             }
             if (!AbstractStateManager.INSTANCE.ABSTRACT_STATES.contains(expectedAbstractState1)) {
                 val equivalentAbstractState = AbstractStateManager.INSTANCE.ABSTRACT_STATES.find {
                     it.hashCode == expectedAbstractState1!!.hashCode
                 }
                 if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                     reached = true
                     break
                 }
             }

             if (expectedAbstractState1 !is VirtualAbstractState) {
                 if (expectedAbstractState1.isOpeningMenus != currentAbState.isOpeningMenus) {
                     tmpPathTraverser.next()
                     continue
                 }
                 if(expectedAbstractState1.rotation != currentAbState.rotation) {
                     tmpPathTraverser.next()
                     continue
                 }
                 if(expectedAbstractState1.isOpeningKeyboard != currentAbState.isOpeningKeyboard) {
                     tmpPathTraverser.next()
                     continue
                 }
             } else {
                 if (expectedAbstractState.window == currentAbState.window) {
                     reached = true
                     break
                 }
             }
             val nextTransition = tmpPathTraverser.transitionPath.path[tmpPathTraverser.latestEdgeId!!+1]
             if (nextTransition!=null) {
                 if (!nextTransition.abstractAction.isWidgetAction()
                     && nextTransition.abstractAction.actionType!=AbstractActionType.RANDOM_KEYBOARD) {
                     reached = true
                     break
                 } else {
                     if (nextTransition.abstractAction.actionType == AbstractActionType.SWIPE
                             && currentPath!!.pathType != PathFindingHelper.PathType.FULLTRACE
                             && currentPath!!.pathType != PathFindingHelper.PathType.PARTIAL_TRACE) {
                         val tmpPathTraverser2 = PathTraverser(tmpPathTraverser.transitionPath)
                         tmpPathTraverser2.latestEdgeId = tmpPathTraverser.latestEdgeId
                         var notSwipeTransition: AbstractTransition? = null
                         while (notSwipeTransition == null) {
                             val nextTransition2 = tmpPathTraverser2.transitionPath.path[tmpPathTraverser2.latestEdgeId!!+1]
                             if (nextTransition2 == null)
                                 break
                             if (nextTransition2.abstractAction.actionType!=AbstractActionType.SWIPE)
                                 notSwipeTransition = nextTransition2
                             else
                                 tmpPathTraverser2.next()
                         }
                         if (notSwipeTransition!=null) {
                             if (!notSwipeTransition.abstractAction.isWidgetAction()) {
                                 if (notSwipeTransition.abstractAction.actionType == AbstractActionType.RANDOM_KEYBOARD) {
                                     if(currentState.widgets.any { it.isKeyboard }) {
                                         reached = true
                                         tmpPathTraverser.latestEdgeId = tmpPathTraverser2.latestEdgeId
                                         break
                                     }
                                 } else {
                                     reached = true
                                     tmpPathTraverser.latestEdgeId = tmpPathTraverser2.latestEdgeId
                                     break
                                 }
                             } else {
                                 val avm = notSwipeTransition.abstractAction.attributeValuationMap!!
                                 val guiWidgets = getGUIWidgetsByAVM(avm, currentState)
                                 if (guiWidgets.isNotEmpty()) {
                                     reached = true
                                     tmpPathTraverser.latestEdgeId = tmpPathTraverser2.latestEdgeId
                                     break
                                 }
                             }
                         }
                     }
                     if (nextTransition!!.abstractAction.actionType == AbstractActionType.RANDOM_KEYBOARD) {
                         if(currentState.widgets.any { it.isKeyboard }) {
                             reached = true
                             break
                         }
                     }
                     val avm = nextTransition.abstractAction.attributeValuationMap!!
                     val guiWidgets = getGUIWidgetsByAVM(avm, currentState)
                     if (guiWidgets.isNotEmpty()) {
                         reached = true
                         break
                     }
                 }
             } else {
                 if (tmpPathTraverser.finalStateAchieved()) {
                     val destState = tmpPathTraverser.getCurrentTransition()!!.dest
                     if (destState.window != currentAbState.window) {
                         reached = false
                     }
                     else if (destState == currentAbState) {
                         reached = true
                     }
                     else if (!AbstractStateManager.INSTANCE.ABSTRACT_STATES.contains(destState)) {
                         val equivalentAbstractState = AbstractStateManager.INSTANCE.ABSTRACT_STATES.find {
                             it.hashCode == destState!!.hashCode
                         }
                         if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                             reached = true
                         }
                     } else if (destState is VirtualAbstractState
                             && destState.window == currentAbState.window) {
                         reached = true
                     }
                     if (!reached) {
                         val lastActionType = atuaStrategy.eContext.getLastActionType()
                         var abstractActionType = when (lastActionType) {
                             "Tick" -> AbstractActionType.CLICK
                             "ClickEvent" -> AbstractActionType.CLICK
                             "LongClickEvent" -> AbstractActionType.LONGCLICK
                             else -> AbstractActionType.values().find { it.actionName.equals(lastActionType) }
                         }
                         if (abstractActionType == AbstractActionType.WAIT) {
                             reached = true
                         }
                     }
                 }
                 break
             }
             tmpPathTraverser.next()
         }
         if (reached) {
             expectedNextAbState = tmpPathTraverser.getCurrentTransition()!!.dest
             pathTraverser!!.latestEdgeId = tmpPathTraverser.latestEdgeId
         }
         return reached
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        if (currentPath!!.root == atuaMF.getAbstractState(currentState)!!
                && possiblePaths.size > 0) {//still in the source activity
            log.debug("Can change to another option.")
            return true
        }
        return false
    }

    override fun initialize(currentState: State<*>) {
        randomExplorationTask!!.fillingData=true
        randomExplorationTask!!.backAction=true
        chooseRandomOption(currentState)
        atuaStrategy.phaseStrategy.fullControl = true
    }

    override fun reset() {
        possiblePaths.clear()
        includePressbackAction = true
        destWindow = null
        currentPath = null
        useInputTargetWindow = false
        includeResetAction = true
        isExploration = false
        useTrace = true
    }

    var useTrace: Boolean = true
    override fun isAvailable(currentState: State<*>): Boolean {
        reset()
        isExploration = true
        isWindowAsTarget = false
        useTrace = false
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }
        return false
    }

    open fun isAvailable(currentState: State<*>, isWindowAsTarget: Boolean): Boolean {
        reset()
        isExploration = true
        this.isWindowAsTarget = isWindowAsTarget
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }
        return false
    }

    var isWindowAsTarget: Boolean = false
    open fun isAvailable(currentState: State<*>, destWindow: Window, isWindowAsTarget: Boolean = false,  includePressback: Boolean, includeResetApp: Boolean, isExploration: Boolean): Boolean {
        log.info("Checking if there is any path to $destWindow")
        reset()
        this.isWindowAsTarget = isWindowAsTarget
        this.includePressbackAction = includePressback
        this.destWindow = destWindow
        this.useInputTargetWindow = true
        this.includeResetAction = includeResetApp
        this.isExploration = isExploration
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }

/*       if (this.destWindow is WTGDialogNode || this.destWindow is WTGOptionsMenuNode) {
            val newTarget = WTGActivityNode.allNodes.find { it.classType == this.destWindow!!.classType || it.activityClass == this.destWindow!!.activityClass }
            if (newTarget == null || abstractState.window == newTarget)
                return false
            if (AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == newTarget }.map { it.getUnExercisedActions(null).size }.any { it > 0 } )
            {
                this.destWindow = newTarget
                initPossiblePaths(currentState)
                if (possiblePaths.size > 0) {
                    return true
                }
            }
        }*/
        return false
    }

    var includePressbackAction = true
    var includeResetAction = true
    var isExploration = true

    open protected fun initPossiblePaths(currentState: State<*>, continueMode:Boolean = false) {
        possiblePaths.clear()
        var nextPathType = if (currentPath == null)
                PathFindingHelper.PathType.NORMAL
        else
            computeNextPathType(currentPath!!.pathType,includeResetAction)

        if (useInputTargetWindow && destWindow!=null) {
            while (possiblePaths.isEmpty()) {
                possiblePaths.addAll(atuaStrategy.phaseStrategy.getPathsToWindowToExplore(currentState,destWindow!!,nextPathType,isExploration))
                if (computeNextPathType(nextPathType,includeResetAction)==PathFindingHelper.PathType.NORMAL)
                    break
                nextPathType = computeNextPathType(nextPathType,includeResetAction)
            }
        } else {
            val curentPathType = nextPathType
            while (possiblePaths.isEmpty()) {
                possiblePaths.addAll(atuaStrategy.phaseStrategy.getPathsToExploreStates(currentState,nextPathType))
                if (computeNextPathType(curentPathType,includeResetAction)==PathFindingHelper.PathType.NORMAL)
                    break
                nextPathType = computeNextPathType(nextPathType,includeResetAction)
            }

        }
    }

    fun computeNextPathType(pathType: PathFindingHelper.PathType,
                            includeResetApp: Boolean): PathFindingHelper.PathType {
        return when (pathType) {
            PathFindingHelper.PathType.INCLUDE_INFERED -> PathFindingHelper.PathType.NORMAL
            PathFindingHelper.PathType.NORMAL -> PathFindingHelper.PathType.WTG
            PathFindingHelper.PathType.WTG ->
                if(useTrace)
                    PathFindingHelper.PathType.PARTIAL_TRACE
                else
                    PathFindingHelper.PathType.NORMAL
            PathFindingHelper.PathType.PARTIAL_TRACE ->
                if (includeResetApp)
                    PathFindingHelper.PathType.FULLTRACE
                else
                    PathFindingHelper.PathType.NORMAL
            PathFindingHelper.PathType.FULLTRACE -> PathFindingHelper.PathType.NORMAL
            else -> PathFindingHelper.PathType.ANY
        }
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        val widgetGroup = pathTraverser!!.getCurrentTransition()!!.abstractAction.attributeValuationMap
        if (widgetGroup==null)
        {
            return emptyList()
        }
        else
        {
            val guiWidgets: List<Widget> = getGUIWidgetsByAVM(widgetGroup,currentState)
            return guiWidgets
        }
    }

    private fun getGUIWidgetsByAVM(avm: AttributeValuationMap, currentState: State<*>): List<Widget> {
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        val widgets = ArrayList<Widget>()
        widgets.addAll(atuaMF.getRuntimeWidgets(avm,currentAbstractState ,currentState))
//        if (widgets.isEmpty()) {
//            val staticWidget = currentAbstractState.EWTGWidgetMapping[avm]
//            if (staticWidget == null)
//                return emptyList()
//            val correspondentWidgetGroups = currentAbstractState.EWTGWidgetMapping.filter { it.value == staticWidget }
//            widgets.addAll(correspondentWidgetGroups.map { atuaMF.getRuntimeWidgets(it.key,currentAbstractState,currentState) }.flatten())
//        }
        return widgets
    }

    open fun increaseExecutedCount(){
        executedCount++
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction? {
        increaseExecutedCount()
        if(currentExtraTask!=null)
            return currentExtraTask!!.chooseAction(currentState)
        if (expectedNextAbState == null)
        {
            mainTaskFinished = true
            return randomExplorationTask.chooseAction(currentState)
        }
        var nextAbstractState = expectedNextAbState
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        if (currentAbstractState.isOpeningKeyboard && !expectedNextAbState!!.isOpeningKeyboard) {
            return  GlobalAction(actionType = ActionType.CloseKeyboard)
        }
        prevState = currentState
        if (isFillingText) {
            if (fillingDataActionList.isEmpty()) {
                isFillingText = false
                return executeCurrentEdgeAction(currentState, pathTraverser!!.getCurrentTransition()!!, currentAbstractState)
            } else {
                var actionInfo = fillingDataActionList.pop()
                while (!currentState.widgets.contains(actionInfo.second)) {
                    if (fillingDataActionList.empty()) {
                        actionInfo = null
                        break
                    }
                    actionInfo = fillingDataActionList.pop()
                }
                if (actionInfo != null) {
                    ExplorationTrace.widgetTargets.add(actionInfo.second)
                    return actionInfo.first
                } else {
                    isFillingText = false
                    return executeCurrentEdgeAction(currentState, pathTraverser!!.getCurrentTransition()!!, currentAbstractState)
                }
            }
        }
        prevAbState = expectedNextAbState
        if (currentPath == null || expectedNextAbState == null)
            return randomExplorationTask.chooseAction(currentState)
        log.info("Destination: ${currentPath!!.getFinalDestination()}")
        if (expectedNextAbState!!.window == currentAbstractState.window) {
            if (expectedNextAbState!!.rotation != currentAbstractState.rotation) {
                if (currentAbstractState.rotation == Rotation.LANDSCAPE) {
                    return ExplorationAction.rotate(-90)
                } else {
                    return ExplorationAction.rotate(90)
                }
            }
            if (!expectedNextAbState!!.isOpeningKeyboard && currentAbstractState.isOpeningKeyboard) {
                return GlobalAction(ActionType.CloseKeyboard)
            }
            val nextTransition = pathTraverser!!.next()
            if (nextTransition != null) {
                nextAbstractState = nextTransition!!.dest
                expectedNextAbState = nextAbstractState
                log.info("Next action: ${nextTransition.abstractAction}")
                log.info("Expected state: ${nextAbstractState}")
                //log.info("Event: ${currentEdge!!.label.abstractAction.actionName} on ${currentEdge!!.label.abstractAction.widgetGroup}")
                //Fill text input (if required)
                //TODO Need save swipe action data
                if (pathTraverser!!.transitionPath.pathType!=PathFindingHelper.PathType.FULLTRACE
                    && pathTraverser!!.transitionPath.pathType != PathFindingHelper.PathType.PARTIAL_TRACE)
                {
                    if (random.nextBoolean() && nextTransition!!.userInputs.isNotEmpty()?:false) {
                        val inputData = nextTransition!!.userInputs.random()
                        inputData.forEach {
                            val inputWidget = currentState.visibleTargets.find { w -> it.key.equals(w.uid) }
                            if (inputWidget != null) {
                                if (inputWidget.isInputField) {
                                    if (inputWidget.text != it.value) {
                                        fillingDataActionList.add(Pair(inputWidget.setText(it.value, sendEnter = false),inputWidget))
                                    }
                                } else if (inputWidget.checked.isEnabled()) {
                                    if (inputWidget.checked.toString() != it.value) {
                                        fillingDataActionList.add(Pair(inputWidget.click(),inputWidget))
                                    }
                                }
                            }
                        }
                    } else if (!nextTransition!!.abstractAction.isCheckableOrTextInput() ) {
                        if (fillDataTask.isAvailable(currentState,true) && random.nextBoolean()) {
                            fillDataTask.initialize(currentState)
                            fillDataTask.fillActions.entries.forEach {
                                fillingDataActionList.add(Pair(it.value,it.key))
                            }
                        }
                    }
                    if (fillingDataActionList.isNotEmpty())
                    {
                        isFillingText = true
                    }
                }
                if (isFillingText) {
                    val actionInfo = fillingDataActionList.pop()
                    ExplorationTrace.widgetTargets.add(actionInfo.second)
                    return actionInfo.first
                }
                return executeCurrentEdgeAction(currentState, nextTransition!!, currentAbstractState)
            }
            else {
                log.debug("Cannot get next transition.")
            }
        }
        mainTaskFinished = true
        log.debug("Cannot get target action, finish task.")
        return randomExplorationTask!!.chooseAction(currentState)
    }

    private fun executeCurrentEdgeAction(currentState: State<*>, nextTransition: AbstractTransition, currentAbstractState: AbstractState): ExplorationAction? {
        val currentEdge = nextTransition
        if (currentEdge!!.abstractAction.actionType == AbstractActionType.PRESS_MENU) {
            return pressMenuOrClickMoreOption(currentState)
        }
        if (currentEdge!!.abstractAction.attributeValuationMap != null) {
            val widgets = chooseWidgets(currentState)
            if (widgets.isNotEmpty()) {
                tryOpenNavigationBar = false
                tryScroll = false
                val candidates = runBlocking { getCandidates(widgets) }
                val chosenWidget = candidates[random.nextInt(candidates.size)]
                val actionName = currentEdge!!.abstractAction.actionType
                val actionData = if (currentEdge!!.data != null && currentEdge!!.data != "") {
                    currentEdge!!.data
                } else {
                    currentEdge!!.abstractAction.extra
                }
                log.info("Widget: $chosenWidget")
                return chooseActionWithName(actionName, actionData, chosenWidget, currentState, currentEdge!!.abstractAction)
                        ?: ExplorationAction.pressBack()
            } else {
                log.debug("Can not get target widget. Random exploration.")
                if (currentEdge.fromWTG && currentEdge.dest is VirtualAbstractState && atuaMF.actionCount.getUnexploredWidget(currentState).isNotEmpty()) {
                    pathTraverser!!.latestEdgeId = pathTraverser!!.latestEdgeId!! - 1
                } else {
                    mainTaskFinished = true
                }
                return randomExplorationTask!!.chooseAction(currentState)
            }
        } else {
            tryOpenNavigationBar = false
            val action = currentEdge!!.abstractAction.actionType
            //val actionCondition = currentPath!!.edgeConditions[currentEdge!!]
            if (currentEdge!!.data != null && currentEdge!!.data != "") {
                return chooseActionWithName(action, currentEdge!!.data, null, currentState, currentEdge!!.abstractAction)
                        ?: ExplorationAction.pressBack()
            } else {
                return chooseActionWithName(action, currentEdge!!.abstractAction.extra
                        ?: "", null, currentState, currentEdge!!.abstractAction) ?: ExplorationAction.pressBack()
            }
        }
    }

    var scrollAttempt = 0

    protected fun addIncorrectPath(currentAbstractState: AbstractState) {
        val currentEdge = if (currentAbstractState.window != expectedNextAbState!!.window)
                pathTraverser!!.getCurrentTransition()
            else
                pathTraverser!!.transitionPath.path[pathTraverser!!.latestEdgeId!!+1]
        PathFindingHelper.addDisablePathFromState(currentPath!!, currentEdge)
        /*if (currentEdge!=null) {

        }*/

    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }

        var instance: GoToAnotherWindow? = null
        var executedCount:Int = 0
        fun getInstance(regressionWatcher: ATUAMF,
                        atuaTestingStrategy: ATUATestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): GoToAnotherWindow {
            if (instance == null) {
                instance = GoToAnotherWindow(regressionWatcher, atuaTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }
}