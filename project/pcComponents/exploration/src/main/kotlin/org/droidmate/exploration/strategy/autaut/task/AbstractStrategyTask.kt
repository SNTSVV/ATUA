package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.ActionCounterMF
import org.droidmate.exploration.modelFeatures.explorationWatchers.BlackListMF
import org.droidmate.exploration.modelFeatures.listOfSmallest
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.intent.IntentFilter
import org.droidmate.exploration.modelFeatures.autaut.WTG.Helper
import org.droidmate.exploration.strategy.autaut.AutAutTestingStrategy
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.TextInput
import org.droidmate.exploration.modelFeatures.autaut.WTG.DescendantLayoutDirection
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.debugT
import org.droidmate.explorationModel.firstCenter
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.random.Random

abstract class AbstractStrategyTask (val autautStrategy: AutAutTestingStrategy,
                                     val autautMF: AutAutMF,
                                     val delay: Long,
                                     val useCoordinateClicks: Boolean){


    protected var random = java.util.Random(Random.nextLong())
        private set

    protected var counter: ActionCounterMF = autautStrategy.getActionCounter()
    protected var blackList: BlackListMF = autautStrategy.getBlacklist()

    protected suspend fun getCandidates(widgets: List<Widget>): List<Widget> {
        return widgets
                .let { filteredCandidates ->
                    // for each widget in this state the number of interactions
                    autautMF.widgetnNumExplored(autautStrategy.eContext.getCurrentState(), filteredCandidates).entries
                            .groupBy { it.key.packageName }.flatMap { (pkgName, countEntry) ->
                                if (pkgName != autautStrategy.eContext.apk.packageName) {
                                    val pkgActions = counter.pkgCount(pkgName)
                                    countEntry.map { Pair(it.key, pkgActions) }
                                } else
                                    countEntry.map { Pair(it.key, it.value) }
                            }// we sum up all counters of widgets which do not belong to the app package to prioritize app targets
                            .groupBy { (_, countVal) -> countVal }.let { map ->
                                map.listOfSmallest()?.map { (w, _) -> w }?.let { leastInState: List<Widget> ->
                                    // determine the subset of widgets which were least interacted with
                                    // if multiple widgets clicked with same frequency, choose the one least clicked over all states
                                    if (leastInState.size > 1) {
                                        leastInState.groupBy { counter.widgetCnt(it.uid) }.listOfSmallest()
                                    } else leastInState
                                }
                            }
                            ?: emptyList()
                }
    }

    open suspend fun ExplorationContext<*, *, *>.computeCandidates(widgets: List<Widget>): Collection<Widget> = debugT("blacklist computation", {
        val nonCrashing = widgets.nonCrashingWidgets()
        excludeBlacklisted(getCurrentState(), nonCrashing) { noBlacklistedInState, noBlacklisted ->
            when {
                noBlacklisted.isNotEmpty() -> noBlacklisted
                noBlacklistedInState.isNotEmpty() -> noBlacklistedInState
                else -> nonCrashing
            }
        }
    }, inMillis = true)
            .filter { it.clickable || it.longClickable || it.checked != null } // the other actions are currently not supported

    /** use this function to filter potential candidates against previously blacklisted widgets
     * @param block your function determining the ExplorationAction based on the filtered candidates
     * @param tInState the threshold to consider the widget blacklisted within the current state eContext
     * @param tOverall the threshold to consider the widget blacklisted over all states
     */
    protected open suspend fun<S: State<*>> excludeBlacklisted(
            currentState: S,
            candidates: List<Widget>,
            tInState: Int = 1,
            tOverall: Int = 2,
            block: (listedInsState: List<Widget>, blacklisted: List<Widget>) -> List<Widget>
    ): List<Widget> =
            candidates.filterNot { blackList.isBlacklistedInState(it.uid, currentState.uid, tInState) }.let { noBlacklistedInState ->
                block(noBlacklistedInState, noBlacklistedInState.filterNot { blackList.isBlacklisted(it.uid, tOverall) })
            }

    protected val extraTasks = ArrayList<AbstractStrategyTask>()
    protected var currentExtraTask: AbstractStrategyTask? =null
    open protected fun executeExtraTasks(currentState: State<*>): List<Widget>{
        var widgets: List<Widget>
        if (currentExtraTask==null)
        {
            if (extraTasks.size > 0)
            {
                currentExtraTask = extraTasks.first()
                extraTasks.remove(currentExtraTask!!)
            }
        }
        while (currentExtraTask!=null)
        {
            if (currentExtraTask!!.isAvailable(currentState)) {
                widgets = currentExtraTask!!.chooseWidgets(currentState)
                if (widgets.size > 0)
                    return widgets
                while (currentExtraTask!!.hasAnotherOption(currentState)) {
                    widgets = currentExtraTask!!.chooseWidgets(currentState)
                    if (widgets.size > 0)
                        return widgets
                }
            }
            if (extraTasks.size > 0)
            {
                currentExtraTask = extraTasks.first()
                extraTasks.remove(currentExtraTask!!)
            }
            else
            {
                currentExtraTask = null
            }
        }
        return emptyList()
    }

    open protected fun executeCurrentExtraTask(currentState: State<*>): List<Widget> {
        val widgets: List<Widget>
        if (currentExtraTask != null) {
            if (!currentExtraTask!!.isTaskEnd(currentState)) {
                widgets = currentExtraTask!!.chooseWidgets(currentState)
            } else {
                widgets = emptyList()
            }
        } else {
            widgets = emptyList()
        }
        return widgets
    }

     abstract fun isAvailable(currentState: State<*>): Boolean

    open fun chooseWidgets(currentState: State<*>): List<Widget>{
         val visibleWidgets = currentState.widgets.filter {
             it.isVisible && !it.isKeyboard
                     && !it.isInputField && (it.clickable || it.longClickable || it.scrollable )}
         return visibleWidgets
     }

     abstract fun chooseAction(currentState:State<*>): ExplorationAction

     abstract fun reset()

    abstract fun initialize(currentState: State<*>)

    //Some task can have many options to execute
     abstract fun hasAnotherOption(currentState: State<*>): Boolean
     abstract fun chooseRandomOption (currentState: State<*>)

    abstract fun isTaskEnd(currentState: State<*>): Boolean

    internal fun chooseActionWithName(action: AbstractActionType, data: Any?, widget: Widget?, currentState: State<*>, abstractAction: AbstractAction?): ExplorationAction? {
        //special operating for list view
        if (widget == null)
        {
            return when (action){
                AbstractActionType.WAIT -> GlobalAction(ActionType.FetchGUI)
                AbstractActionType.ENABLE_DATA -> GlobalAction(ActionType.EnableData)
                AbstractActionType.DISABLE_DATA -> GlobalAction(ActionType.DisableData)
                AbstractActionType.PRESS_MENU -> pressMenuOrClickMoreOption(currentState)
                AbstractActionType.PRESS_BACK -> ExplorationAction.pressBack()
                AbstractActionType.PRESS_HOME -> ExplorationAction.minimizeMaximize()
                AbstractActionType.MINIMIZE_MAXIMIZE -> ExplorationAction.minimizeMaximize()
                AbstractActionType.ROTATE_UI -> {
                    if (autautMF.currentRotation==Rotation.PORTRAIT) {
                        ExplorationAction.rotate(90)
                    }
                    else
                    {
                        ExplorationAction.rotate(-90)
                    }

                }
                AbstractActionType.SEND_INTENT -> callIntent(data)
                AbstractActionType.SWIPE -> doSwipe(currentState,data as String)
                AbstractActionType.LAUNCH_APP -> autautStrategy.eContext.launchApp()
                AbstractActionType.RESET_APP -> autautStrategy.eContext.resetApp()
                AbstractActionType.RANDOM_KEYBOARD -> doRandomKeyboard(currentState)
                AbstractActionType.CLOSE_KEYBOARD -> GlobalAction(ActionType.CloseKeyboard)
                AbstractActionType.CLICK_OUTBOUND -> doClickOutbound(currentState)
                AbstractActionType.ACTION_QUEUE -> doActionQueue(data,currentState)
                AbstractActionType.CLICK -> doClickWithoutTarget(data,currentState)
                else -> ExplorationAction.pressBack()
            }
        }
        val chosenWidget: Widget = widget
        val isItemEvent = when (action) {
            AbstractActionType.ITEM_CLICK, AbstractActionType.ITEM_LONGCLICK, AbstractActionType.ITEM_SELECTED -> true
            else -> false
        }

        if (isItemEvent) {
            return chooseActionForItemEvent(chosenWidget, currentState, action,abstractAction)
        } else {
            return chooseActionForNonItemEvent(action, chosenWidget, currentState, data,abstractAction)
        }
    }

    private fun doClickOutbound(currentState: State<*>): ExplorationAction? {
        val guiDimension = Helper.computeGuiTreeDimension(currentState)
        return Click (guiDimension.leftX-100,y=guiDimension.topY-100)
    }

    private fun doClickWithoutTarget(data: Any?, currentState: State<*>): ExplorationAction? {
        if (data is String && data.isNotBlank()) {
            val point = Helper.parseCoordinationData(data)
            return Click(x = point.first,y=point.second)
        }
        val guiDimension = Helper.computeGuiTreeDimension(currentState)
        return Click(x = guiDimension.width/2,y=guiDimension.height/2)

    }

    private fun doActionQueue(data: Any?, currentState: State<*>): ExplorationAction? {
        if (data == null)
            return ExplorationAction.pressBack()
        try {
            val interactions = data as List<Interaction<Widget>>
            val actionList = ArrayList<ExplorationAction>()
            interactions.forEach {interaction ->
                if (interaction.targetWidget==null) {
                    val action = when (interaction.actionType) {
                        "PressBack" -> ExplorationAction.pressBack()
                        "PressHome" -> ExplorationAction.pressMenu()
                        "PressEnter" -> ExplorationAction.pressEnter()
                        "Swipe" -> {
                            val swipeData = Helper.parseSwipeData(interaction.data)
                            ExplorationAction.swipe(swipeData[0], swipeData[1],25)
                        }
                        else -> ExplorationAction.pressEnter()
                    }
                    actionList.add(action)
                } else {
                    val widget = currentState.widgets.find { it.uid == interaction.targetWidget!!.uid }
                    if (widget!=null) {
                        val action = when (interaction.actionType) {
                            "Click" -> widget.availableActions(25,useCoordinateClicks).filter {
                                it.name.isClick() }.singleOrNull()
                            "LongClick" -> widget.availableActions(25,useCoordinateClicks).filter {
                                it.name.isLongClick()}.singleOrNull()
                            "Swipe" -> {
                                val swipeData = Helper.parseSwipeData(interaction.data)
                                ExplorationTrace.widgetTargets.add(widget)
                                Swipe(swipeData[0],swipeData[1],25,true)
                            }
                            else -> widget.availableActions(0,useCoordinateClicks).random()
                        }
                        if (action!=null) {
                            actionList.add(action)
                        } else {
                            if (interaction.actionType == "Click" || interaction.actionType == "LongClick") {
                                ExplorationTrace.widgetTargets.removeLast()
                            }
                        }
                    }
                }
            }
            if (actionList.isEmpty())
                return null
            return ActionQueue(actionList,0)
        }
        catch (e : Exception)
        {
            log.debug("$e")
            return ExplorationAction.pressBack()
        }
    }

    private fun doRandomKeyboard(currentState: State<*>): ExplorationAction? {
        var childWidgets = currentState.widgets.filter { it.isKeyboard }
        val allAvailableActions = childWidgets.map { it.click()}
        return allAvailableActions.random()
    }

    private fun doSwipe(currentState: State<*>, data: String): ExplorationAction? {
        var outBoundLayout = currentState.widgets.find { it.resourceId == "android.id/content" }
        if (outBoundLayout == null) {
            outBoundLayout = currentState.widgets.find { !it.hasParent }
        }
        if (outBoundLayout == null) {
            return ExplorationAction.pressBack()
        }
        val screenHeight =
                if (outBoundLayout.visibleBounds.height == 0)
                    outBoundLayout.boundaries.height
        else
                    outBoundLayout.visibleBounds.height
        val screenWidth =  if (outBoundLayout.visibleBounds.width == 0)
            outBoundLayout.boundaries.width
        else
            outBoundLayout.visibleBounds.width
        val swipeAction = when (data) {
            "SwipeUp" -> {
                val startY = outBoundLayout.visibleBounds.bottomY-screenHeight/10
                ExplorationAction.swipe(Pair(screenWidth / 2, startY ), Pair(screenWidth / 2, startY - screenHeight))
            }
            "SwipeDown" -> {
                val startY = outBoundLayout.visibleBounds.topY+screenHeight/10
                ExplorationAction.swipe(Pair(screenWidth / 2, startY), Pair(screenWidth / 2, startY + screenHeight))
            }
            "SwipeLeft" -> {
                val startX = outBoundLayout.visibleBounds.rightX
                ExplorationAction.swipe(Pair(startX, screenHeight / 2), Pair(startX - screenWidth, screenHeight / 2))}
            "SwipeRight" -> {
                val startX = outBoundLayout.visibleBounds.leftX
                ExplorationAction.swipe(Pair(startX, screenHeight / 2), Pair(startX + screenWidth, screenHeight / 2))}
            else -> {
                if (data.isNotBlank()) {
                    val swipeData = Helper.parseSwipeData(data)
                    if (swipeData.size == 2) {
                        return ExplorationAction.swipe(swipeData[0], swipeData[1],25)
                    }
                }
                if (random.nextBoolean()) {
                    //Swipe up
                    return ExplorationAction.swipe(Pair(screenWidth / 2, outBoundLayout.visibleBounds.bottomY), Pair(screenWidth / 2, outBoundLayout.visibleBounds.bottomY - screenHeight))
                } else {
                    //Swipe right
                    return ExplorationAction.swipe(Pair(outBoundLayout.visibleBounds.leftX, screenHeight / 2), Pair(outBoundLayout.visibleBounds.leftX + screenWidth, screenHeight / 2))
                }

            }
        }
        return swipeAction
    }

    private fun chooseActionForNonItemEvent(action: AbstractActionType, chosenWidget: Widget, currentState: State<*>, data: Any?, abstractAction: AbstractAction?): ExplorationAction? {
        if (action == AbstractActionType.TEXT_INSERT && chosenWidget.isInputField) {
            return chooseActionForTextInput(chosenWidget, currentState)
        }
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        if (action == AbstractActionType.SWIPE && data is String) {
            val swipeAction =
             when (data) {
                "SwipeUp" -> chosenWidget.swipeUp()
                "SwipeDown" -> chosenWidget.swipeDown()
                "SwipeLeft" -> chosenWidget.swipeLeft()
                "SwipeRight" -> chosenWidget.swipeRight()
                 "SwipeTillEnd" -> doDeepSwipeUp(chosenWidget,currentState).also {
                     if (abstractAction!=null)
                        currentAbstractState.increaseActionCount(abstractAction,true)
                 }
                else -> {
                    if (data.isNotBlank()) {
                        val swipeInfo: List<Pair<Int,Int>> = Helper.parseSwipeData(data)
                        Swipe(swipeInfo[0],swipeInfo[1],25,true)
                    } else {
                        arrayListOf(chosenWidget.swipeUp(), chosenWidget.swipeDown(),chosenWidget.swipeLeft(),chosenWidget.swipeRight()).random()
                    }
                }
            }
            ExplorationTrace.widgetTargets.clear()
            ExplorationTrace.widgetTargets.add(chosenWidget)
            return swipeAction
        }
        if (chosenWidget.className == "android.webkit.WebView") {
            val explorationAction: ExplorationAction?;
            var childWidgets = Helper.getAllChild(currentState.widgets,chosenWidget)
            //val allAvailableActions = childWidgets.plus(chosenWidget).map { it.availableActions(delay, true)}.flatten()
            val actionList: ArrayList<ExplorationAction>  = ArrayList<ExplorationAction>()
            if (childWidgets.isEmpty()) {
                if (abstractAction!=null) {
                    abstractAction.attributeValuationSet!!.actionCount.remove(abstractAction)
                }
                return null
            }
            if (action == AbstractActionType.CLICK) {
                /*val swipeActions = allAvailableActions.filter { it is Swipe }
                for (i in 0..10) {
                    actionList.add(swipeActions.random())
                }
                val clickActions = allAvailableActions.filter { it is ClickEvent || it is Click }
                for (i in 0..10) {
                    actionList.add(clickActions.random())
                }*/
                if (data == "RandomMultiple") {
                    for (i in 0..10) {
                        actionList.add(childWidgets.random().click())
                    }
                    explorationAction =  ActionQueue(actionList,50)
                } else {
                    explorationAction = childWidgets.random().click()
                }
            }
            else if (action == AbstractActionType.LONGCLICK) {
                /*val swipeActions = allAvailableActions.filter { it is Swipe }
                for (i in 0..10) {
                    actionList.add(swipeActions.random())
                }
                val longClickActions = allAvailableActions.filter { it is LongClickEvent || it is LongClick }
                for (i in 0..10) {
                    actionList.add(longClickActions.random())
                }*/
                if (data == "RandomMultiple") {
                    //val actions = allAvailableActions.filter {it is LongClickEvent || it is LongClick }
                    for (i in 0..5) {
                        actionList.add(childWidgets.random().longClick())
                    }
                    explorationAction = ActionQueue(actionList,50)
                } else {
                    explorationAction = childWidgets.random().longClick()
                }

            }  else {
                if (abstractAction!=null) {
                    abstractAction.attributeValuationSet!!.actionCount.remove(abstractAction)
                }
                return null
            }
            if (abstractAction!=null){
                currentAbstractState.increaseActionCount(abstractAction,updateSimilarAbstractState = true)
            }
            return explorationAction
        }
        val actionList = getAvailableActionsForWidget(chosenWidget, currentState)

        val widgetActions = actionList.filter {
            when (action) {
                AbstractActionType.CLICK -> (it.name == "Click" || it.name == "ClickEvent")
                AbstractActionType.LONGCLICK -> it.name == "LongClick" || it.name == "LongClickEvent"
                AbstractActionType.SWIPE -> it.name == "Swipe"
                else -> it.name == "Click" || it.name == "ClickEvent"
            }
        }
        if (widgetActions.isNotEmpty()) {

            return widgetActions.random()
        }
        ExplorationTrace.widgetTargets.clear()
        val hardAction = when (action) {
            AbstractActionType.CLICK -> chosenWidget.clickEvent(delay=delay, ignoreClickable = true)
            AbstractActionType.LONGCLICK -> chosenWidget.longClickEvent(delay=delay, ignoreVisibility = true)
            else -> chosenWidget.click(ignoreClickable = true)
        }
        return hardAction
    }

    fun doRandomActionOnWidget(chosenWidget: Widget, currentState: State<*>): ExplorationAction {
        var actionList = getAvailableActionsForWidget(chosenWidget, currentState)
        if (actionList.isNotEmpty()) {
            val maxVal = actionList.size

            assert(maxVal > 0) { "No actions can be performed on the widget $chosenWidget" }

            val randomIdx = random.nextInt(maxVal)
            //val randomAction = chooseActionWithName(AbstractActionType.values().find { it.actionName.equals(actionList[randomIdx].name) }!!, "", chosenWidget, currentState, null)
            val randomAction =actionList.random()
            log.info("$randomAction")
            return randomAction ?: ExplorationAction.pressBack().also { log.info("Action null -> PressBack") }
        } else {
            ExplorationTrace.widgetTargets.clear()
            if (!chosenWidget.hasClickableDescendant && chosenWidget.selected.isEnabled()) {
                return chosenWidget.longClick()
            } else {
                return ExplorationAction.pressBack()
            }

        }
    }

    private fun getAvailableActionsForWidget(chosenWidget: Widget, currentState: State<*>): ArrayList<ExplorationAction> {
        var actionList = if (Helper.isScrollableWidget(chosenWidget)) {
            val availableActions = ArrayList(chosenWidget.availableActions(delay, useCoordinateClicks).filterNot { it is Swipe })
            when (Helper.getViewsChildrenLayout(chosenWidget, currentState)) {
                DescendantLayoutDirection.HORIZONTAL -> {
                    availableActions.add(chosenWidget.swipeLeft())
                    availableActions.add(chosenWidget.swipeRight())
                }
                DescendantLayoutDirection.VERTICAL -> {
                    availableActions.add(chosenWidget.swipeUp())
                    availableActions.add(chosenWidget.swipeDown())
                }
                else -> {
                    availableActions.add(chosenWidget.swipeUp())
                    availableActions.add(chosenWidget.swipeDown())
                    availableActions.add(chosenWidget.swipeLeft())
                    availableActions.add(chosenWidget.swipeRight())
                }
            }
            availableActions
        } else {
            ArrayList(chosenWidget.availableActions(delay, useCoordinateClicks).filterNot { it is Swipe })
        }
        ExplorationTrace.widgetTargets.clear()
        if (actionList.isNotEmpty())
            ExplorationTrace.widgetTargets.add(chosenWidget)
        return actionList
    }

    private fun doDeepSwipeUp(chosenWidget: Widget,currentState: State<*>): ExplorationAction? {
        val actionList = ArrayList<ExplorationAction>()
        if (chosenWidget.className == "android.webkit.WebView") {
            var childWidgets = getChildWidgets(currentState, chosenWidget)
            val swipeActions = childWidgets.filter { it.scrollable }.map { it.swipeUp(stepSize = 5) }
            if (swipeActions.isNotEmpty()) {
                for (i in 0..5) {
                    actionList.add(swipeActions.random())
                }
            } else {
                val randomForcedSwipeActions = childWidgets.map { it.swipeUp(stepSize = 5) }
                for (i in 0..5) {
                    actionList.add(randomForcedSwipeActions.random())
                }
            }

        } else {
            for (i in 0..5) {
                actionList.add(chosenWidget.swipeUp(stepSize = 5))
            }
        }
        return ActionQueue(actionList,0)
    }


    private fun chooseActionForTextInput(chosenWidget: Widget, currentState: State<*>): ExplorationAction {
        val inputValue = TextInput.getSetTextInputValue(chosenWidget, currentState,true)
        val explorationAction = chosenWidget.setText(inputValue, delay = delay, sendEnter = true)
        return explorationAction
    }

    private fun chooseActionForItemEvent(chosenWidget: Widget, currentState: State<*>, action: AbstractActionType, abstractAction: AbstractAction?): ExplorationAction? {
        if (chosenWidget.childHashes.size == 0)
            return null
        autautMF.isRecentItemAction = true
        var childWidgets = getChildWidgets(currentState, chosenWidget)
        if (childWidgets.isEmpty()) {
            if (abstractAction!=null) {
                abstractAction.attributeValuationSet!!.actionCount.remove(abstractAction)
            }
            return null
        }
        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)!!
        if (abstractAction!=null && action==AbstractActionType.ITEM_CLICK)
            abstractState.increaseActionCount(abstractAction,updateSimilarAbstractState = true)
        if (chosenWidget.className == "android.webkit.WebView") {
           val actionList: ArrayList<ExplorationAction>  = ArrayList<ExplorationAction>()
            if (action==AbstractActionType.ITEM_CLICK) {
                for (i in 0..10) {
                    actionList.add(childWidgets.random().click())
                }
                return ActionQueue(actionList,50)
            }
            if (action == AbstractActionType.ITEM_LONGCLICK)
                return childWidgets.random().longClick()
        }

        val actionableWidgets = childWidgets.filter {
            when (action) {
                AbstractActionType.ITEM_CLICK -> it.clickable
                AbstractActionType.ITEM_LONGCLICK -> it.longClickable
                AbstractActionType.ITEM_SELECTED -> it.clickable && it.selected?.let { it == false } ?: false && it.checked != null
                else -> it.clickable
            }
        }
        if (actionableWidgets.size > 0) {
            val randomWidgets =
                    runBlocking {
                        getCandidates(actionableWidgets)
                    }
            val randomWidget = if (randomWidgets.isEmpty())
                actionableWidgets.random()
            else
                randomWidgets.random()
            log.info("Item widget: $randomWidget")
            val chosenAction = randomWidget.availableActions(delay, useCoordinateClicks).find {
                when (action) {
                    AbstractActionType.ITEM_CLICK -> it.name == ClickEvent.name || it.name == Click.name
                    AbstractActionType.ITEM_LONGCLICK -> it.name == LongClickEvent.name || it.name == LongClick.name
                    AbstractActionType.ITEM_SELECTED -> it.name == Tick.name
                    else -> it.name == ClickEvent.name || it.name == Click.name
                }
            }
            return chosenAction
        }

        val randomWidget = childWidgets[random.nextInt(childWidgets.size)]
        log.info("Item widget: $randomWidget")
        val hardAction = when (action) {
            AbstractActionType.ITEM_CLICK -> randomWidget.clickEvent(delay=delay, ignoreClickable = true)
            AbstractActionType.ITEM_LONGCLICK -> randomWidget.longClickEvent(delay=delay, ignoreVisibility = true)
            AbstractActionType.ITEM_SELECTED -> randomWidget.clickEvent(delay=delay, ignoreClickable = true)
            else -> randomWidget.clickEvent(delay=delay, ignoreClickable = true)
        }
        return hardAction
    }

    private fun getChildWidgets(currentState: State<*>, chosenWidget: Widget): List<Widget> {
        var childWidgets = Helper.getAllInteractiveChild(currentState.widgets, chosenWidget)
        if (childWidgets.isEmpty()) {
            childWidgets = Helper.getAllInteractiveChild2(currentState.widgets, chosenWidget)
        }
        if (childWidgets.isEmpty()) {
            childWidgets = Helper.getAllChild(currentState.widgets, chosenWidget)
        }
        return childWidgets
    }

    private fun callIntent(data: Any?): CallIntent {
        if (data is IntentFilter) {
            val intentFilter = data as IntentFilter
            return autautStrategy.eContext.callIntent(intentFilter.getActions().random(),
                    intentFilter.getCategories().random(), intentFilter.getDatas().random().testData.random(), intentFilter.activity)

        } else {
            val intentData: HashMap<String,String> = parseIntentData(data as String)
            return autautStrategy.eContext.callIntent(
                    action = intentData["action"]?:"",
                    category = intentData["category"]?:"",
                    activity = intentData["activity"]?:"",
                    uriString = intentData["uriString"]?:""
            )
        }
    }

    private fun parseIntentData(s: String): HashMap<String, String> {
        val data = HashMap<String,String>()
        val splits = s.split(';')
        splits.forEach {
            val parts = it.split(" = ")
            val key = parts[0]
            val value = parts[1]
            data.put(key,value)
        }
        return data
    }

    private fun hasTextInput(currentState: State<*>): Boolean {
        return currentState.actionableWidgets.find { it.isInputField } != null
    }



    internal fun hardClick(chosenWidget: Widget, delay: Long): Click {
        val coordinate: Pair<Int, Int> = chosenWidget.let {
            if (it.visibleAreas.firstCenter() != null)
                it.visibleAreas.firstCenter()!!
            else if (it.visibleBounds.width != 0 && it.visibleBounds.height != 0)
                it.visibleBounds.center
            else
                it.boundaries.center
        }
        val clickAction = Click(x = coordinate.first, y = coordinate.second, delay = delay, hasWidgetTarget = false)
        return clickAction
    }

    internal fun hardLongClick(chosenWidget: Widget, delay: Long): LongClick {
        val coordinate: Pair<Int, Int> = chosenWidget.let {
            if (it.visibleAreas.firstCenter() != null)
                it.visibleAreas.firstCenter()!!
            else if (it.visibleBounds.width != 0 && it.visibleBounds.height != 0)
                it.visibleBounds.center
            else
                it.boundaries.center
        }
        val longClickAction = LongClick(x = coordinate.first, y = coordinate.second, delay = delay, hasWidgetTarget = false)
        return longClickAction
    }

    internal fun randomSwipe(chosenWidget: Widget, delay: Long, useCoordinateClicks: Boolean, actionList: ArrayList<ExplorationAction>) {
        val swipeActions = chosenWidget.availableActions(delay, useCoordinateClicks).filter { it is Swipe }
        val choseSwipe = swipeActions[random.nextInt(swipeActions.size)]
        actionList.add(choseSwipe)
    }
    internal fun haveOpenNavigationBar(currentState: State<*>): Boolean {
        if (currentState.widgets.filter { it.isVisible }.find { it.contentDesc.contains("Open navigation") } != null)
        {
            return true
        }
        return false
    }

    internal fun pressMenuOrClickMoreOption(currentState: State<*>): ExplorationAction {
        return ExplorationAction.pressMenu()
        val moreOptionWidget = autautMF.getToolBarMoreOptions(currentState)
        if (moreOptionWidget != null) {
            log.info("Widget: $moreOptionWidget")
            return moreOptionWidget.click()

        } else {
            if (haveOpenNavigationBar(currentState))
            {
                return clickOnOpenNavigation(currentState)
            }
            autautMF.isRecentPressMenu = true
            return ExplorationAction.pressMenu()
        }
    }

     fun clickOnOpenNavigation(currentState: State<*>): ExplorationAction {
        val openNavigationWidget = currentState.widgets.filter { it.isVisible }.find { it.contentDesc.contains("Open navigation") }!!
         log.info("Widget: $openNavigationWidget")
        return chooseActionWithName(AbstractActionType.CLICK, null, openNavigationWidget, currentState,null)!!
    }

    fun isCameraOpening(currentState: State<*>): Boolean {
        return currentState.widgets.any{it.packageName == "com.android.camera2"}
    }
    /** filters out all crashing marked widgets from the actionable widgets of the current state **/
    suspend fun Collection<Widget>.nonCrashingWidgets() = filterNot { autautStrategy.eContext.crashlist.isBlacklistedInState(it.uid,autautStrategy.eContext.getCurrentState().uid) }
    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
    }

}