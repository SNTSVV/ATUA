package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.deviceInterface.exploration.isLaunchApp
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.launchApp
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.autaut.DSTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Activity
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class PhaseOneStrategy(
        autAutTestingStrategy: AutAutTestingStrategy,
        budgetScale: Double,
        delay: Long,
        useCoordinateClicks: Boolean
):AbstractPhaseStrategy (
        autAutTestingStrategy = autAutTestingStrategy,
        scaleFactor = budgetScale,
        delay = delay,
        useCoordinateClicks = useCoordinateClicks,
        useVirtualAbstractState = true
) {
    override fun isTargetWindow(window: Window): Boolean {
        if (window == targetWindow)
            return true
        return false
    }


    val untriggeredWidgets = arrayListOf<EWTGWidget>()
    val untriggeredTargetEvents = arrayListOf<Input>()

    var attemps: Int
    var targetWindow: Window? = null
    val flaggedWindows = HashSet<Window>()
    val unreachableWindows = HashSet<Window>()
    val fullyCoveredWindows = HashSet<Window>()
    val targetWindowTryCount: HashMap<Window,Int> = HashMap()
    val windowRandomExplorationBudget: HashMap<Window,Int> = HashMap()
    val windowRandomExplorationBudgetUsed: HashMap<Window, Int> = HashMap()
    val windowRandomExplorationBudget2: HashMap<AbstractState,Int> = HashMap()
    val windowRandomExplorationBudgetUsed2: HashMap<AbstractState, Int> = HashMap()
    val window_Widgets = HashMap<Window, HashSet<Widget>> ()

    var delayCheckingBlockStates = 0
    init {
        phaseState = PhaseState.P1_INITIAL
        autautMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        attemps = autautMF.allTargetWindow_ModifiedMethods.size
        autautMF.allTargetWindow_ModifiedMethods.keys.forEach {
            targetWindowTryCount.put(it,0)
        }
        autautMF.allTargetInputs.forEach {
            untriggeredTargetEvents.add(it)
        }
    }


    override fun registerTriggeredEvents( abstractAction: AbstractAction, guiState: State<*>)
    {
        val abstractState = AbstractStateManager.instance.getAbstractState(guiState)!!
        //val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(abstractState).filter { it.label.abstractAction.equals(abstractAction) }.map { it.label }

        val staticEvents = abstractState.inputMappings[abstractAction]
        if (staticEvents != null) {
            staticEvents.forEach {
                untriggeredTargetEvents.remove(it)
                /*if (autautMF.targetItemEvents.containsKey(it)) {
                    autautMF.targetItemEvents[it]!!["count"] = autautMF!!.targetItemEvents[it]!!["count"]!! + 1
                    if (autautMF.targetItemEvents[it]!!["count"] == autautMF!!.targetItemEvents[it]!!["max"]!!) {
                        untriggeredTargetEvents.remove(it)
                    }
                } else {
                    untriggeredTargetEvents.remove(it)
                }*/
            }
        }
    }

    var tryCount = 0
    var forceEnd = false
    override fun hasNextAction(currentState: State<*>): Boolean {

        //For debug, end phase one after 100 actions
        /*if (autAutTestingStrategy.eContext.explorationTrace.getActions().size > 100)
            return true*/
        if (autautMF.lastUpdatedStatementCoverage == 1.0) {
            return false
        }
        updateBudgetForWindow(currentState)
        updateTargetWindows()
        updateFlaggedWindows()
        updateUnreachableWindows(currentState)
        if (forceEnd)
            return false
        val currentAbstractState = autautMF.getAbstractState(currentState)
        if (currentAbstractState!=null && (currentAbstractState.window is Dialog || currentAbstractState.window is OptionsMenu || currentAbstractState.window is OutOfApp))
            return true

        if (targetWindowTryCount.filterNot{fullyCoveredWindows.contains(it.key) || flaggedWindows.contains(it.key)}.isEmpty()) {
            return false
        }
        if (delayCheckingBlockStates > 0) {
            delayCheckingBlockStates--
            return true
        }
        delayCheckingBlockStates = 5
        return isAvailableAbstractStatesExisting(currentState).also {
            if (it == false) {
                log.debug("No available abstract states to explore.")
            }
        }
        /*val toExploreWindows = targetWindowTryCount.filterNot { flaggedWindows.contains(it.key) || fullyCoveredWindows.contains(it.key) || unreachableWindows.contains(it.key)}
        if (toExploreWindows.isEmpty()) {

            return true
        } else  {
           return false
        }*/
    }

    private fun updateUnreachableWindows(currentState: State<*>) {
        val currentAppState = autautMF.getAbstractState(currentState)!!
        if (unreachableWindows.contains(currentAppState.window)) {
            unreachableWindows.remove(currentAppState.window)
        }
    }

    private fun isAvailableAbstractStatesExisting(currentState: State<*>): Boolean {
        val availableAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
                .filter {
                    it !is VirtualAbstractState &&
                            !flaggedWindows.contains(it.window) &&

                            it.guiStates.isNotEmpty() &&
                            hasBudgetLeft(it.window)
                }
        if (availableAbstractStates.isEmpty())
            return false
        PathFindingHelper.allAvailableTransitionPaths.clear()
        if (availableAbstractStates.any { !isBlocked(it, currentState) }) {
            return true
        }
        return false
    }

    private fun updateBudgetForWindow(currentState: State<*>) {
        //val currentAppState = autautMF.getAbstractState(currentState)!!
        val currentAppState = autautMF.getAbstractState(currentState)!!
        val window = currentAppState.window
        if (window is Launcher) {
            return
        }
        if (!window_Widgets.containsKey(window)) {
            window_Widgets.put(window,HashSet())
        }
        val widgets = window_Widgets[window]!!
        val newWidgets = ArrayList<Widget>()

        val interactableWidgets = Helper.getActionableWidgetsWithoutKeyboard(currentState).filter{
            (it.scrollable || it.clickable || it.longClickable) && !Helper.hasParentWithType(it,currentState,"WebView")
                    && !Helper.isUserLikeInput(it) && !it.isInputField
        }
/*
        interactableWidgets.filter { w -> !widgets.any { it.uid == w.uid } }.forEach { w ->
            if (!widgets.any { w.uid == it.uid }) {
                widgets.add(w)
            }
            if (!newWidgets.any { w.uid == it.uid })
                newWidgets.add(w)
        }
*/
        if (widgets.isEmpty()) {
            interactableWidgets.forEach { w ->
                if (!widgets.any { w.uid == it.uid }) {
                    widgets.add(w)
                }
                newWidgets.add(w)
            }
        } else
        {
            // we count only new widget with new resourceId
            interactableWidgets.filter{w -> w.resourceId.isNotBlank() }. filter { w -> !widgets.any { it.resourceId == w.resourceId } }.forEach { w ->
            //interactableWidgets.filter { w -> !widgets.any { it.uid == w.uid } }.forEach { w ->
                if (!widgets.any { w.resourceId == it.resourceId }) {
                    widgets.add(w)
                }
                if (!newWidgets.any { w.resourceId == it.resourceId })
                    newWidgets.add(w)
            }
        }
        if (!windowRandomExplorationBudget.containsKey(window)) {
            if (window is Activity)
                windowRandomExplorationBudget[window] = 4
            else
                windowRandomExplorationBudget[window] = 0
            windowRandomExplorationBudgetUsed[window] = 0
        }
        var newActions = 0
        newWidgets.forEach {
            if (it.visibleBounds.width > 200 && it.visibleBounds.height > 200 ) {
               newActions += it.availableActions(delay, useCoordinateClicks).size
            } else {
                newActions += it.availableActions(delay, useCoordinateClicks).filterNot { it is Swipe }.size
            }
            if (it.className == "android.webkit.WebView") {
                newActions += 50
            }
        }
        windowRandomExplorationBudget[window] = windowRandomExplorationBudget[window]!! + (newActions*scaleFactor).toInt()
        if (window is OptionsMenu || window is Dialog || window is OutOfApp) {
            val activityWindow = WindowManager.instance.allMeaningWindows.find { it is Activity && it.activityClass == window.activityClass }
            if (activityWindow!=null && windowRandomExplorationBudget.containsKey(activityWindow))
                windowRandomExplorationBudget[activityWindow] = windowRandomExplorationBudget[activityWindow]!! + (newActions*scaleFactor).toInt()
        }
        ExplorationTrace.widgetTargets.clear()

    }

    private fun updateFlaggedWindows() {
        val addToFlagWindows = ArrayList<Window>()
        flaggedWindows.forEach {
            if (it is Activity) {
                val optionsMenu = autautMF.wtg.getOptionsMenu(it)
                if (optionsMenu!=null) {
                    addToFlagWindows.add(optionsMenu)
                }
                val dialogs = autautMF.wtg.getDialogs(it)
                addToFlagWindows.addAll(dialogs)
            }
        }
        flaggedWindows.addAll(addToFlagWindows)
        val availableAbState_Window =  AbstractStateManager.instance.ABSTRACT_STATES
                .filter { it !is VirtualAbstractState &&
                        !flaggedWindows.contains(it.window) &&
                        !unreachableWindows.contains(it.window)
                }.groupBy { it.window }
        availableAbState_Window.forEach {
          if (!hasBudgetLeft(it.key)) {
              flaggedWindows.add(it.key)
              if (it.key is Activity) {
                  val optionsMenu = autautMF.wtg.getOptionsMenu(it.key)
                  if (optionsMenu!=null) {
                      flaggedWindows.add(optionsMenu)
                  }
                  val dialogs = autautMF.wtg.getDialogs(it.key)
                  flaggedWindows.addAll(dialogs)
              }
          }

        }
    }

    private fun updateTargetWindows() {
        autautMF.allTargetWindow_ModifiedMethods.map { it.key }.forEach {
            var coverCriteriaCount = 0
            if (autautMF.allTargetWindow_ModifiedMethods[it]!!.all { autautMF.allModifiedMethod[it] == true }) {
                coverCriteriaCount++
            }
            if (autautMF.untriggeredTargetHandlers.intersect(
                            autautMF.windowHandlersHashMap[it] ?: emptyList()
                    ).isEmpty()) {
                coverCriteriaCount++
            }
            if (coverCriteriaCount==2) {
                if (!fullyCoveredWindows.contains(it)) {
                    fullyCoveredWindows.add(it)
                    if (targetWindow == it) {
                        targetWindow = null
                        strategyTask = null
                    }
                }
            }
            if (!targetWindowTryCount.containsKey(it))
                targetWindowTryCount.put(it, 0)
        }
    }

    private fun isBlocked(abstractState: AbstractState,currentState: State<*>): Boolean {
        val transitionPath = ArrayList<TransitionPath>()
        val abstractStates = HashMap<AbstractState,Double>()
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        if (abstractState == currentAbstractState)
            return false
        abstractStates.put(abstractState,1.0)
        getPathToStates(transitionPaths=transitionPath,
                stateByScore = abstractStates,
                stopWhenHavingUnexercisedAction = false,
                currentState = currentState,
                currentAbstractState = currentAbstractState,
                shortest = true,
                pathCountLimitation = 1,
                pathType = PathFindingHelper.PathType.RESET
                )
        if (transitionPath.isEmpty()) {
            getPathToStates(transitionPaths=transitionPath,
                    stateByScore = abstractStates,
                    stopWhenHavingUnexercisedAction = false,
                    currentState = currentState,
                    currentAbstractState = currentAbstractState,
                    shortest = true,
                    pathCountLimitation = 1,
                    pathType = PathFindingHelper.PathType.RESET)
            return true
        }

        return false
    }

    override fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction {
        //TODO Update target windows

        val currentState = eContext.getCurrentState()
        val currentAppState = autautMF.getAbstractState(currentState)!!
        log.info("Current abstract state: $currentAppState")
        log.info("Current window: ${currentAppState.window}")
        var chosenAction:ExplorationAction

        if (flaggedWindows.contains(targetWindow)) {
            targetWindow = null
        }
        if (unreachableWindows.contains(targetWindow))
            targetWindow = null

        if (targetWindow != currentAppState.window) {
            if (isTargetWindow(currentAppState)) {
                val oldTargetWindow = targetWindow
                targetWindow = currentAppState.window
                strategyTask = null
                phaseState = PhaseState.P1_INITIAL
                /*if (getCurrentTargetEvents(currentState).isNotEmpty()) {
                    //if current window is a target window and has target inputs
                    //update targetWindow
                    strategyTask = null
                    phaseState = PhaseState.P1_INITIAL
                } else {
                    //restore the current target window
                    targetWindow = oldTargetWindow
                }*/
            }
        }
        if (targetWindow == null) {
            //try select a target window
            selectTargetNode(currentState, 0).also {
                if (targetWindow != null) {
                    strategyTask = null
                    phaseState = PhaseState.P1_INITIAL
                }
            }
        } else if (flaggedWindows.contains(targetWindow!!)) {
            //try select another target window
            selectTargetNode(currentState, 0).also {
                if (targetWindow != null) {
                    strategyTask = null
                    phaseState = PhaseState.P1_INITIAL
                }
            }
        } /*else if (currentAppState.window == targetWindow && getCurrentTargetEvents(currentState).isEmpty()) {
            if (getPathsToTargetWindows(currentState,PathFindingHelper.PathType.ANY).isEmpty()) {
                //if current abstract state is a target but does not have target events
                //and their no path to the abstract states with target events
                //select another target window
                selectTargetNode(currentState, 0).also {
                    if (targetWindow != null) {
                        strategyTask = null
                        phaseState = PhaseState.P1_INITIAL
                    }
                }
            }
        }*/

        if (targetWindow == null ) {
            if (targetWindowTryCount.containsKey(currentAppState.window) && hasBudgetLeft(currentAppState.window)) {
                targetWindow = currentAppState.window
            }
        }
        ExplorationTrace.widgetTargets.clear()

        log.info("Target window: $targetWindow")
        chooseTask_P1(eContext, currentState)
        if (needResetApp) {
            needResetApp = false
            return eContext.resetApp()
        }
        if (strategyTask != null) {
            chosenAction = strategyTask!!.chooseAction(currentState)
            if (isCountAction(chosenAction)
                    && windowRandomExplorationBudgetUsed.containsKey(currentAppState.window)
                    && (strategyTask is RandomExplorationTask && (strategyTask as RandomExplorationTask).fillingData == false)) {

                windowRandomExplorationBudgetUsed[currentAppState.window] = windowRandomExplorationBudgetUsed[currentAppState.window]!! + 1
/*                if (windowRandomExplorationBudgetUsed[currentAppState.window]!! > windowRandomExplorationBudget[currentAppState.window]!!) {
                    flaggedWindows.add(currentAppState.window)
                }*/
            }
        } else {
            log.debug("No task seleted. It might be a bug.")
            chosenAction = eContext.resetApp()
        }
        actionCountSinceSelectTarget++
        return chosenAction
    }

    private fun chooseTask_P1(eContext: ExplorationContext<*, *, *>, currentState: State<*>) {
        log.debug("Choosing Task")
        val fillDataTask = PrepareContextTask.getInstance(autautMF,autAutTestingStrategy,delay, useCoordinateClicks)
        val exerciseTargetComponentTask = ExerciseTargetComponentTask.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val goToTargetNodeTask = GoToTargetWindowTask.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherWindow.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val randomExplorationTask = RandomExplorationTask.getInstance(autautMF, autAutTestingStrategy,delay, useCoordinateClicks)
        val openNavigationBarTask = OpenNavigationBarTask.getInstance(autautMF,autAutTestingStrategy,delay, useCoordinateClicks)
        val currentState = eContext.getCurrentState()
        val currentAppState = autautMF.getAbstractState(currentState)!!

        log.debug("${currentAppState.window} - Budget: ${windowRandomExplorationBudgetUsed[currentAppState.window]}/${windowRandomExplorationBudget[currentAppState.window]}")

        if (targetWindow == null) {
            nextActionWithoutTargetWindow(currentState, currentAppState, randomExplorationTask, goToAnotherNode)
            return
        }
        if (phaseState == PhaseState.P1_INITIAL || strategyTask == null) {
            nextActionOnInitial(currentAppState, exerciseTargetComponentTask, currentState, randomExplorationTask, goToAnotherNode, goToTargetNodeTask)
            return
        }
        if (phaseState == PhaseState.P1_EXERCISE_TARGET_NODE) {
            nextActionOnExerciseTargetWindow(currentAppState, currentState, exerciseTargetComponentTask, randomExplorationTask, goToAnotherNode, goToTargetNodeTask)
            return
        }
        if (phaseState == PhaseState.P1_GO_TO_TARGET_NODE) {
            nextActionOnGoToTargetNode(currentAppState, exerciseTargetComponentTask, currentState, randomExplorationTask, goToAnotherNode,goToTargetNodeTask)
            return

        }
        if (phaseState == PhaseState.P1_GO_TO_EXPLORE_STATE) {
            nextActionOnGoToExploreState(currentAppState, exerciseTargetComponentTask, currentState, randomExplorationTask, goToAnotherNode, goToTargetNodeTask)
            return
        }
        if (phaseState == PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE) {
            nextActionOnRandomInTargetWindow(currentAppState, randomExplorationTask, exerciseTargetComponentTask, currentState, goToAnotherNode, goToTargetNodeTask)
            return
        }
        if (phaseState == PhaseState.P1_RANDOM_EXPLORATION) {
            nextActionOnRandomExploration(currentAppState, exerciseTargetComponentTask, currentState, randomExplorationTask, goToAnotherNode, goToTargetNodeTask)
            return
        }
        selectTargetNode(currentState,0)
        phaseState = PhaseState.P1_INITIAL
        //needResetApp = true
        return

    }

    private fun nextActionWithoutTargetWindow(currentState: State<*>, currentAppState: AbstractState, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow) {
        if (strategyTask != null) {
            if (continueOrEndCurrentTask(currentState)) return
        }
        if (randomExplorationInSpecialWindows(currentAppState, randomExplorationTask, currentState)) return
        if (hasBudgetLeft(currentAppState.window) && hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, false,true)
            return
        }
        if (goToWindowToExploreOrRandomExploration(currentAppState, goToAnotherNode, currentState, randomExplorationTask)) return
        if (strategyTask is GoToAnotherWindow) {
            setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
            return
        }
        if (strategyTask is RandomExplorationTask
                && randomExplorationTask.isFullyExploration) {
            forceEnd = true
            return
        }
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        return
    }

    private fun nextActionOnInitial(currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow, goToTargetNodeTask: GoToTargetWindowTask) {
        if (currentAppState.window == targetWindow) {
            if (exerciseTargetIfAvailable(exerciseTargetComponentTask, currentState)) return
            // In case target events not found
            if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
                if (goToTargetNodeTask.possiblePaths.any { isTargetAbstractState(it.getFinalDestination())  }) {
                    setGoToTarget(goToTargetNodeTask, currentState)
                    return
                }
            }
            // Try random exploration
            if (randomlyExploreTargetIfHasBudgetAndUnexploredWidgets(currentState, randomExplorationTask)) return
            if (hasBudgetLeft(currentAppState.window)) {
            /*    if (goToAnotherNode.isAvailable(currentState, targetWindow!!, true, true)) {
                    setGoToExploreState(goToAnotherNode, currentState)
                    return
                       }*/
                setRandomExploration(randomExplorationTask, currentState)
                return
            }
        }
        if (randomExplorationInSpecialWindows(currentAppState, randomExplorationTask, currentState)) return
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        //unreachableWindows.add(targetWindow!!)
        if (hasBudgetLeft(currentAppState.window) && hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, false,false)
            return
        }
        if (goToWindowToExploreOrRandomExploration(currentAppState, goToAnotherNode, currentState, randomExplorationTask)) return
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        return
    }

    private fun nextActionOnExerciseTargetWindow(currentAppState: AbstractState, currentState: State<*>, exerciseTargetComponentTask: ExerciseTargetComponentTask, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow, goToTargetNodeTask: GoToTargetWindowTask) {
        if (currentAppState.window == targetWindow) {
            if (continueOrEndCurrentTask(currentState)) return
            if (exerciseTargetIfAvailable(exerciseTargetComponentTask, currentState)) return
            // In case target events not found
            if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
                if (goToTargetNodeTask.possiblePaths.any { isTargetAbstractState(it.getFinalDestination())  }) {
                    setGoToTarget(goToTargetNodeTask, currentState)
                    return
                }
            }
            // Try random exploration

            if (randomlyExploreTargetIfHasBudgetAndUnexploredWidgets(currentState, randomExplorationTask)) return
            if (hasBudgetLeft(currentAppState.window)) {
                /*if (goToAnotherNode.isAvailable(currentState, targetWindow!!, true, true)) {
                    setGoToExploreState(goToAnotherNode, currentState)
                    return
                }*/
                setRandomExploration(randomExplorationTask, currentState)
                return
            }
        }
        if (nextActionOnDialog(currentAppState, currentState, randomExplorationTask, goToTargetNodeTask)) return
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
                 setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        //unreachableWindows.add(targetWindow!!)
        if (hasBudgetLeft(currentAppState.window) && hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, true, false)
            return
        }
        if (goToWindowToExploreOrRandomExploration(currentAppState, goToAnotherNode, currentState, randomExplorationTask)) return
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        return
    }

    private fun nextActionOnGoToTargetNode(currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow, goToTargetNodeTask: GoToTargetWindowTask) {
        if (currentAppState.window == targetWindow) {
            if (continueOrEndCurrentTask(currentState)) return
            if (exerciseTargetIfAvailable(exerciseTargetComponentTask, currentState)) return
            // In case target events not found
            // Try random exploration if having budget
            if (randomlyExploreTargetIfHasBudgetAndUnexploredWidgets(currentState, randomExplorationTask)) return
            if (hasBudgetLeft(currentAppState.window)) {
               /* if (goToAnotherNode.isAvailable(currentState, targetWindow!!, true, true)) {
                    setGoToExploreState(goToAnotherNode, currentState)
                    return
                }*/
                setRandomExploration(randomExplorationTask, currentState)
                return
            }
        }
        if (continueOrEndCurrentTask(currentState)) return
        //unreachableWindows.add(targetWindow!!)
        if (randomExplorationInSpecialWindows(currentAppState, randomExplorationTask, currentState)) return
        if (hasBudgetLeft2(currentState, currentAppState) && hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, false,false)
            return
        }
        if (goToWindowToExploreOrRandomExploration(currentAppState, goToAnotherNode, currentState, randomExplorationTask)) return
        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        return
    }

    private fun randomExplorationInSpecialWindows(currentAppState: AbstractState, randomExplorationTask: RandomExplorationTask, currentState: State<*>): Boolean {
        if (currentAppState.window is Dialog || currentAppState.window is OptionsMenu || currentAppState.window is OutOfApp) {
            setRandomExploration(randomExplorationTask, currentState, true, false)
            return true
        }
        return false
    }

    private fun nextActionOnGoToExploreState(currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow, goToTargetNodeTask: GoToTargetWindowTask) {
        if (currentAppState.window == targetWindow) {
            if (exerciseTargetIfAvailable(exerciseTargetComponentTask, currentState)) return
            if (continueOrEndCurrentTask(currentState)) return
            // In case target events not found
            // Try random exploration if having budget
            if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
                if (goToTargetNodeTask.possiblePaths.any { isTargetAbstractState(it.getFinalDestination())  }) {
                    setGoToTarget(goToTargetNodeTask, currentState)
                    return
                }
            }
            if (randomlyExploreTargetIfHasBudgetAndUnexploredWidgets(currentState, randomExplorationTask)) return
            if (hasBudgetLeft(currentAppState.window)) {
                setRandomExploration(randomExplorationTask, currentState)
                return
            }
        }
        if (hasBudgetLeft(currentAppState.window) && hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState)
            return
        }
        if (continueOrEndCurrentTask(currentState)) return
        if (nextActionOnDialog(currentAppState, currentState, randomExplorationTask, goToTargetNodeTask)) return
        if (goToAnotherNode.destWindow == currentAppState.window) {
            if (hasBudgetLeft2(currentState, currentAppState)) {
                setRandomExploration(randomExplorationTask, currentState,false,true)
                return
            }
            setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        }
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        //unreachableWindows.add(targetWindow!!)
        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        return
    }

    private fun nextActionOnRandomInTargetWindow(currentAppState: AbstractState, randomExplorationTask: RandomExplorationTask, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, goToAnotherNode: GoToAnotherWindow, goToTargetNodeTask: GoToTargetWindowTask) {
        if (currentAppState.window == targetWindow) {
            if (continueRandomExplorationIfIsFillingData(randomExplorationTask)) return
            if (exerciseTargetIfAvailable(exerciseTargetComponentTask, currentState)) return
            if (continueOrEndCurrentTask(currentState)) return
            if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
                if (goToTargetNodeTask.possiblePaths.any { isTargetAbstractState(it.getFinalDestination())  }) {
                    setGoToTarget(goToTargetNodeTask, currentState)
                    return
                }
            }
            if (randomlyExploreTargetIfHasBudgetAndUnexploredWidgets(currentState, randomExplorationTask)) return
            if (hasBudgetLeft(currentAppState.window)) {
                setRandomExploration(randomExplorationTask, currentState)
                return
            }

        }
        if (continueOrEndCurrentTask(currentState)) return
        if (nextActionOnDialog(currentAppState, currentState, randomExplorationTask, goToTargetNodeTask)) return

        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        //unreachableWindows.add(targetWindow!!)

        if (hasBudgetLeft(currentAppState.window) && hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, false,false)
            return
        }
        if (goToWindowToExploreOrRandomExploration(currentAppState, goToAnotherNode, currentState, randomExplorationTask)) return
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }

        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        return
    }

    private fun goToWindowToExploreOrRandomExploration(currentAppState: AbstractState, goToAnotherNode: GoToAnotherWindow, currentState: State<*>, randomExplorationTask: RandomExplorationTask): Boolean {
        if (hasBudgetLeft(currentAppState.window)) {
            if (goToAnotherNode.isAvailable(currentState, currentAppState.window, true, true, true)) {
                setGoToExploreState(goToAnotherNode, currentState)
                return true
            }
            setRandomExploration(randomExplorationTask, currentState, true, false)
            return true
        }
        return false
    }

    private fun selectAnotherTargetIfFullyExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>): Boolean {
        if (randomExplorationTask.isFullyExploration || shouldChangeTargetWindow()) {
            selectTargetNode(currentState, 0)
            phaseState = PhaseState.P1_INITIAL
            //needResetApp = true
            return true
        }
        return false
    }

    private fun nextActionOnDialog(currentAppState: AbstractState, currentState: State<*>, randomExplorationTask: RandomExplorationTask, goToTargetNodeTask: GoToTargetWindowTask): Boolean {
        if (currentAppState.window is Dialog || currentAppState.window is OptionsMenu || currentAppState.window is OutOfApp) {
            setRandomExploration(randomExplorationTask, currentState, true, lockWindow = false)
            return true
        }
        return false
    }

    private fun nextActionOnRandomExploration(currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow, goToTargetNodeTask: GoToTargetWindowTask) {
        if (currentAppState.window == targetWindow) {
            if (exerciseTargetIfAvailable(exerciseTargetComponentTask, currentState)) return
            if (continueRandomExplorationIfIsFillingData(randomExplorationTask)) return
            if (randomlyExploreTargetIfHasBudgetAndUnexploredWidgets(currentState, randomExplorationTask)) return
            if (hasBudgetLeft(currentAppState.window)) {
                setRandomExploration(randomExplorationTask, currentState)
                return
            }
        }
        if (continueRandomExplorationIfIsFillingData(randomExplorationTask)) return
        if (randomExplorationTask.isFullyExploration && !strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!}")
            return
        }

        if (randomExplorationTask.stopWhenHavingTestPath && currentAppState.window !is Dialog && currentAppState.window !is OptionsMenu && currentAppState.window !is OutOfApp) {
            if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
                setGoToTarget(goToTargetNodeTask, currentState)
                return
            }
        }
        if (randomExplorationInSpecialWindows(currentAppState, randomExplorationTask, currentState)) return
        if (hasBudgetLeft(currentAppState.window) && !hasUnexploreWidgets(currentState)) {
            if (goToWindowToExploreOrRandomExploration(currentAppState, goToAnotherNode, currentState, randomExplorationTask)) return
        }
        if (hasBudgetLeft(currentAppState.window)) {
            if (continueOrEndCurrentTask(currentState)) return
        }
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true,false)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }
        if (randomExplorationTask.isFullyExploration) {
            selectTargetNode(currentState, 0)
            phaseState = PhaseState.P1_INITIAL
            //needResetApp = true
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        return
    }

    private fun goToUnexploitedAbstractStateOrRandomlyExplore(currentAppState: AbstractState, goToAnotherNode: GoToAnotherWindow, currentState: State<*>, randomExplorationTask: RandomExplorationTask): Boolean {
        if (hasBudgetLeft(currentAppState.window)) {
            if (goToAnotherNode.isAvailable(currentState, currentAppState.window, true, true,true)) {
                setGoToExploreState(goToAnotherNode, currentState)
                return true
            }
            setRandomExploration(randomExplorationTask, currentState)
            return true
        }
        return false
    }

    private fun randomlyExploreTargetIfHasBudgetAndUnexploredWidgets(currentState: State<*>, randomExplorationTask: RandomExplorationTask): Boolean {
        if (hasBudgetLeft(targetWindow!!) && hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask,currentState)
            //setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
            return true
        }
        return false
    }

    private fun exerciseTargetIfAvailable(exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>): Boolean {
        if (exerciseTargetComponentTask.isAvailable(currentState)) {
            setExerciseTarget(exerciseTargetComponentTask, currentState)
            return true
        }
        return false
    }

    private fun continueRandomExplorationIfIsFillingData(randomExplorationTask: RandomExplorationTask): Boolean {
        if (randomExplorationTask.fillingData || randomExplorationTask.attemptCount == 0) {
            // if random can be still run, keep running
            log.info("Continue ${strategyTask!!}")
            return true
        }
        return false
    }

    private fun continueOrEndCurrentTask(currentState: State<*>): Boolean {
        if (!strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!}")
            return true
        }
        return false
    }


    override fun isTargetState(currentState: State<*>): Boolean {
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        if (isTargetAbstractState(currentAbstractState)) {
            return true
        }
        if (autautMF.untriggeredTargetHandlers.intersect(
                        autautMF.windowHandlersHashMap[currentAbstractState.window] ?: emptyList()
                ).isNotEmpty()) {
            if (hasBudgetLeft2(currentState,currentAbstractState))
                return true
        }
        return false
    }

    private fun setGoToExploreState(goToAnotherNode: GoToAnotherWindow, currentState: State<*>) {
        strategyTask = goToAnotherNode.also {
            it.initialize(currentState)
            it.retryTimes = 0
        }
        log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
        phaseState = PhaseState.P1_GO_TO_EXPLORE_STATE
    }

    private fun setGoToTarget(goToTargetNodeTask: GoToTargetWindowTask, currentState: State<*>) {
        strategyTask = goToTargetNodeTask.also {
            it.initialize(currentState)
            it.retryTimes = 0
        }
        log.info("Go to target window: ${targetWindow.toString()}")
        phaseState = PhaseState.P1_GO_TO_TARGET_NODE
    }


    private fun selectTargetNode(currentState: State<*>, tried: Int) {
        //Try finding reachable target
        val maxTry = targetWindowTryCount.size/2+1
        val explicitTargetWindows = WindowManager.instance.allMeaningWindows.filter { window ->
            untriggeredTargetEvents.any { it.sourceWindow == window }
        }
        var candidates = targetWindowTryCount.filter { isExplicitCandidateWindow(explicitTargetWindows,it) }
        if (candidates.isNotEmpty()) {
            val leastTriedWindow = candidates.map { Pair<Window,Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
            targetWindow = leastTriedWindow.value.random().first
        } else {
            targetWindow = null
        }
        if (targetWindow == null) {
            candidates = targetWindowTryCount.filter { isCandidateWindow(it) }
            if (candidates.isNotEmpty()) {
                val leastTriedWindow = candidates.map { Pair<Window, Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
                targetWindow = leastTriedWindow.value.random().first
            }
        }
        if (targetWindow != null) {
            val transitionPaths = getPathsToWindowToExplore(currentState,targetWindow!!,PathFindingHelper.PathType.ANY,false)
            targetWindowTryCount[targetWindow!!] = targetWindowTryCount[targetWindow!!]!! + 1
            if (transitionPaths.isEmpty()) {
                unreachableWindows.add(targetWindow!!)
                if (tried < maxTry)
                    return selectTargetNode(currentState, tried+1)
                else
                    targetWindow = null
            }
        }

        actionCountSinceSelectTarget = 0
    }

    private fun isExplicitCandidateWindow(explicitTargetWindows: List<Window>, it: Map.Entry<Window, Int>) =
            explicitTargetWindows.contains(it.key) && !flaggedWindows.contains(it.key) && !fullyCoveredWindows.contains(it.key)

    private fun isCandidateWindow(it: Map.Entry<Window, Int>) =
            !flaggedWindows.contains(it.key) && !fullyCoveredWindows.contains(it.key)

    override fun  getPathsToVisitedStates(currentState: State<*>,pathType: PathFindingHelper.PathType): List<TransitionPath> {
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(autautMF.appPrevState!!)
        if (currentAbstractState==null)
            return transitionPaths
        val runtimeAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
                .filterNot { it is VirtualAbstractState ||
                        it == currentAbstractState ||
                        it.window is Launcher ||
                        it.window is OutOfApp ||
                        flaggedWindows.contains(it.window) ||
                        it.isRequestRuntimePermissionDialogBox ||
                        it.isAppHasStoppedDialogBox ||
                        it.guiStates.isEmpty()
                }
        val abstratStateCandidates = runtimeAbstractStates

        val stateByActionCount = HashMap<AbstractState,Double>()
        abstratStateCandidates.forEach {
            val weight =  it.computeScore(autautMF)
            if (weight>0.0) {
                stateByActionCount.put(it, weight)
            }
        }
        val stateCandidates: Map<AbstractState,Double>
        stateCandidates = stateByActionCount
        getPathToStatesBasedOnPathType(pathType,transitionPaths, stateByActionCount, currentAbstractState, currentState)
        return transitionPaths
    }



    override fun getPathsToTargetWindows(currentState: State<*>,pathType: PathFindingHelper.PathType): List<TransitionPath> {
        log.debug("getAllTargetNodePaths")
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        val targetStates = getTargetAbstractStates(currentNode = currentAbstractState)
        val stateScores: HashMap<AbstractState,Double> = HashMap<AbstractState,Double>()
        targetStates.filterNot { it == currentState }. forEach {
            val score =  it.computeScore(autautMF)
            stateScores.put(it,score)
        }
        getPathToStatesBasedOnPathType(pathType,transitionPaths,stateScores,currentAbstractState,currentState)
        return transitionPaths
    }

    override fun getCurrentTargetEvents(currentState: State<*>): Set<AbstractAction> {
        val targetEvents = HashMap<Input,List<AbstractAction>>()
        if (autautMF.getAbstractState(currentState)!!.window != targetWindow)
            return emptySet<AbstractAction>()
        val currentWindowTargetEvents = untriggeredTargetEvents.filter { it.sourceWindow == targetWindow }
        currentWindowTargetEvents.forEach {
            val abstractInteractions = autautMF.validateEvent(it, currentState)

            if (abstractInteractions.isNotEmpty())
            {
                targetEvents.put(it,abstractInteractions)
            }
        }
        return targetEvents.map { it.value }.flatten().toSet()
        /*val currentAppState = regressionTestingMF.getAbstractState(currentState)!!
        val targetActions = currentAppState.targetActions
        val untriggerTargetActions = targetActions.filter {
            if (it.widgetGroup==null) {
                currentAppState.actionCount[it] == 0
            } else {
                it.widgetGroup.actionCount[it] == 0
            }
        }
        return untriggerTargetActions*/
    }

    var clickedOnKeyboard = false
    var needResetApp = false
    var actionCountSinceSelectTarget: Int = 0


    private fun isCountAction(chosenAction: ExplorationAction) =
           !chosenAction.isFetch()
                   && chosenAction.name!="CloseKeyboard"
                   && !chosenAction.name.isLaunchApp()

    private fun isTargetWindow(currentAppState: AbstractState): Boolean {
        return targetWindowTryCount.filterNot {
            flaggedWindows.contains(it.key) || fullyCoveredWindows.contains(it.key)
                    || currentAppState.window == targetWindow
        }.any { it.key == currentAppState.window }
    }

    var numOfContinousTry = 0


    private fun shouldChangeTargetWindow() = autautMF.updateMethodCovFromLastChangeCount > 25 * scaleFactor

    private fun setFullyRandomExplorationInTargetWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>, currentAppState: AbstractState) {
        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        randomExplorationTask.lockTargetWindow(currentAppState.window)
        phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
    }

    private fun setExerciseTarget(exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>) {
        strategyTask = exerciseTargetComponentTask.also {
            it.initialize(currentState)
        }
        log.info("This window has target events.")
        log.info("Exercise target component task chosen")
        phaseState = PhaseState.P1_EXERCISE_TARGET_NODE
    }

    private fun hasBudgetLeft(window: Window): Boolean {
        if (!windowRandomExplorationBudget.containsKey(window))
            return true
        if (windowRandomExplorationBudgetUsed[window]!! <= windowRandomExplorationBudget[window]!!) {
            return true
        }
        return false
    }

    private fun hasBudgetLeft2(currentState: State<*>?, currentAbstractState: AbstractState): Boolean {
        if (hasBudgetLeft(currentAbstractState.window))
            return true
//        if (currentAbstractState.getUnExercisedActions(currentState).filter { it.isWidgetAction() }.isNotEmpty()) {
//            return true
//        }
       /* if (!windowRandomExplorationBudget2.containsKey(abstractState))
            return true
        val totalActionCount = getAbstractStateExecutedActionsCount(abstractState)
        if (totalActionCount < windowRandomExplorationBudget2[abstractState]!!) {
            return true
        }*/

        return false
    }

    private fun getAbstractStateExecutedActionsCount(abstractState: AbstractState) =
            abstractState.getActionCountMap().filter {it.key.isWidgetAction()} .map { it.value }.sum()

    private fun isLoginWindow(currentAppState: AbstractState): Boolean {
        val activity = currentAppState.window.classType.toLowerCase()
        return activity.contains("login") || activity.contains("signin")
    }

    private fun setRandomExploration(randomExplorationTask: RandomExplorationTask,
                                     currentState: State<*>,
                                     stopWhenTestPathIdentified: Boolean = false,
                                     lockWindow: Boolean = false) {
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.stopWhenHavingTestPath = stopWhenTestPathIdentified
            val randomBudgetLeft = windowRandomExplorationBudget[currentAbstractState.window]!! - windowRandomExplorationBudgetUsed[currentAbstractState.window]!!
            val minRandomBudget = (5 * scaleFactor).toInt()
            if (randomBudgetLeft <= minRandomBudget) {
                it.setMaxiumAttempt(minRandomBudget)
            } else {
                it.setMaxiumAttempt(randomBudgetLeft)
            }
            if (lockWindow && currentAbstractState.belongToAUT())
                it.lockTargetWindow(currentAbstractState.window)
        }
        log.info("Random exploration")
        phaseState = PhaseState.P1_RANDOM_EXPLORATION

    }

    private fun setFullyRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>, currentAbstractState: AbstractState) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.isFullyExploration = true
            it.stopWhenHavingTestPath = true
            val randomBudgetLeft = windowRandomExplorationBudget[currentAbstractState.window]!! - windowRandomExplorationBudgetUsed[currentAbstractState.window]!!
            val minRandomBudget = (5 * scaleFactor).toInt()
            if (randomBudgetLeft <= minRandomBudget) {
                it.setMaxiumAttempt(minRandomBudget)
            } else {
                it.setMaxiumAttempt(randomBudgetLeft)
            }
        }
        log.info("Cannot find path the target node.")
        log.info("Fully Random exploration")
        phaseState = PhaseState.P1_RANDOM_EXPLORATION
/*        if (Random.nextBoolean()) {
            needResetApp = true
        }*/
    }

    private fun setRandomExplorationInTargetWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.lockTargetWindow(targetWindow!!)
            val randomBudgetLeft = windowRandomExplorationBudget[currentAbstractState.window]!! - windowRandomExplorationBudgetUsed[currentAbstractState.window]!!
            val minRandomBudget = (5 * scaleFactor).toInt()
            if (randomBudgetLeft <= minRandomBudget) {
                it.setMaxiumAttempt(minRandomBudget)
            } else {
                it.setMaxiumAttempt(randomBudgetLeft)
            }
        }
        log.info("This window is a target window but cannot find any target window transition")
        log.info("Random exploration in current window")
        phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
    }

    fun getTargetAbstractStates(currentNode: AbstractState): ArrayList<AbstractState>
    {
        if (targetWindow == null)
            return ArrayList()
        val candidates = ArrayList<AbstractState>()
        val excludedNode = currentNode
        if (!AbstractStateManager.instance.ABSTRACT_STATES.any {
                    it !is VirtualAbstractState && it.window == targetWindow
                }) {
            val virtualAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find {
                it is VirtualAbstractState && it.window == targetWindow
            }
            if (virtualAbstractState != null) {
                candidates.add(virtualAbstractState)
            } else {
                log.debug("Something is wrong")
            }
        } else {
            //Get all AbstractState contain target events
            AbstractStateManager.instance.ABSTRACT_STATES
                    .filter {
                        it !is VirtualAbstractState &&
                        it.window == targetWindow
                                && it != excludedNode
                    }
                    .forEach {
                        val hasUntriggeredTargetEvent: Boolean
                        hasUntriggeredTargetEvent = isTargetAbstractState(it)
                        if (hasUntriggeredTargetEvent)
                            candidates.add(it)
                    }
            if (candidates.isEmpty()) {
                AbstractStateManager.instance.ABSTRACT_STATES
                        .filter {
                            it.window == targetWindow
                                    && it != excludedNode
                        }
                        .forEach {
                                candidates.add(it)
                        }
            }
        }
        return candidates
    }

    private fun isTargetAbstractState(abstractState: AbstractState): Boolean {
        if (abstractState is VirtualAbstractState)
            return false
        val staticEvents = abstractState.inputMappings.map { it.value }
        if (staticEvents.find { untriggeredTargetEvents.intersect(it).isNotEmpty() } != null) {
            return true
        } else {
            return false
        }
    }

    companion object {

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(PhaseOneStrategy::class.java) }



    }
}