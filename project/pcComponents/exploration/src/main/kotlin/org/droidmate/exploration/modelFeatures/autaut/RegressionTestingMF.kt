package org.droidmate.exploration.modelFeatures.autaut

import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.getValue

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.exploration.modelFeatures.explorationWatchers.CrashListMF
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
import org.droidmate.exploration.modelFeatures.autaut.textInput.InputConfiguration
import org.droidmate.exploration.modelFeatures.autaut.textInput.InputConfigurationFileHelper
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.*
import org.droidmate.exploration.modelFeatures.autaut.intent.IntentFilter
import org.droidmate.exploration.modelFeatures.autaut.staticModel.EventType
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticEvent
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticWidget
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.autaut.task.TextInput
import org.droidmate.exploration.strategy.autaut.task.ExerciseTargetComponentTask
import org.droidmate.exploration.strategy.autaut.task.GoToAnotherNode
import org.droidmate.exploration.strategy.autaut.task.RandomExplorationTask
import org.droidmate.exploration.strategy.autaut.task.GoToTargetNodeTask
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import presto.android.gui.clients.regression.informationRetrieval.InformationRetrieval
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext

class RegressionTestingMF(private val appName: String,
                          private val resourceDir: Path,
                          private val getCurrentActivity: suspend () -> String) : ModelFeature() {
    val textFilledValues = ArrayList<String>()
    private val targetWidgetFileName = "targetWidgetReport.txt"
    override val coroutineContext: CoroutineContext = CoroutineName("RegressionTestingModelFeature") + Job()
    private var statementMF: StatementCoverageMF?=null
    private var crashlist: CrashListMF?=null
    var transitionGraph: TransitionGraph = TransitionGraph()
    lateinit var abstractTransitionGraph: AbstractTransitionGraph
    var stateGraph: StateGraphMF? = null
    private val abandonnedWTGNodes = arrayListOf<WTGNode>()
    private val disableEdges = HashMap<Edge<AbstractState, AbstractInteraction>, Int>()
    val interestingInteraction = HashMap<State<*>, ArrayList<Interaction<*>>>()
    val blackListWidgets = HashMap<AbstractState, Widget>()


    var isRecentItemAction: Boolean = false
    var isRecentPressMenu: Boolean = false
    val generalDictionary = ArrayList<String>()
    var inputConfiguration: InputConfiguration?=null
    var currentRotation: Int = 0
    var phase: Int = 1
    private val widgetProbability = mutableMapOf<UUID, Double>() // probability of each widget invoking modified methods
    private val runtimeWidgetInfos = mutableMapOf<Pair<WTGNode,UUID>, Triple<State<*>, StaticWidget,HashMap<String, Any>>>()//Key: widget id
    private val widgets_modMethodInvocation = mutableMapOf<String, Widget_MethodInvocations>()
    private val allDialogOwners = mutableMapOf<String, ArrayList<String>>() // window -> listof (Dialog)
    private val allMeaningfulWidgets =   arrayListOf<StaticWidget>() //widgetId -> idWidget
    val allTargetStaticWidgets = arrayListOf<StaticWidget>() //widgetId -> idWidget
    val allTargetStaticEvents = arrayListOf<StaticEvent>()
    val allEventHandlers = hashSetOf<String>()
    private val allActivityOptionMenuItems = mutableMapOf<String,ArrayList<StaticWidget> >()  //idWidget
    private val allContextMenuItems = arrayListOf<StaticWidget>()
    private val activityTransitionWidget = mutableMapOf<String, ArrayList<StaticWidget>>() // window -> Listof<StaticWidget>
    private val activity_TargetComponent_Map = mutableMapOf<String, ArrayList<StaticEvent>>() // window -> Listof<StaticWidget>
     val untriggeredWidgets = arrayListOf<StaticWidget>()
     val untriggeredTargetEvents = arrayListOf<StaticEvent>()
    private val targetItemEvents = HashMap<StaticEvent, HashMap<String,Int>>()
    var isAlreadyRegisteringEvent = false
    private val stateActivityMapping = mutableMapOf<State<*>,String>()

    private val child_parentTargetWidgetMapping = mutableMapOf<Pair<WTGNode,UUID>, Pair<WTGNode,UUID>>() // child_widget.uid -> parent_widget.uid
    private val dateFormater = SimpleDateFormat("yyyy:MM:dd:HH:mm:ss")

    var lastExecutedInteraction: AbstractInteraction? = null
    var lastExecutedAction: AbstractAction? = null
    private var lastChildExecutedEvent: AbstractInteraction? = null
    private var lastTargetAbState: AbstractState? = null
    private var necessaryCheckModel: Boolean = false
    public var isModelUpdated: Boolean = false
        private set

    val optionsMenuCheck = ArrayList<AbstractState>()
    val openNavigationCheck = ArrayList<AbstractState>()
    val triedBlankInputCheck = ArrayList<AbstractState>()
    var isFisrtVisitedNode: Boolean = false
    var isRecentlyFillText = false
    val abstractStateVisitCount = HashMap<AbstractState, Int>()
    val activityVisitCount = HashMap<String, Int>()
    var appPrevState: State<*>? =null

    val allAvailableTransitionPaths =  HashMap<Pair<AbstractState,AbstractState>,ArrayList<TransitionPath>>()

    var modifiedMethodCoverageFromLastChangeCount: Int = 0
    var methodCoverageFromLastChangeCount: Int = 0
    var lastModifiedMethodCoverage: Double = 0.0
    var lastMethodCoverage: Double = 0.0
    val unreachableModifiedMethods = ArrayList<String>()

    val intentFilters = HashMap<String, ArrayList<IntentFilter>>()
    val targetIntFilters = HashMap<IntentFilter,Int>()

    val staticEventWindowCorrelation = HashMap<StaticEvent, HashMap<WTGNode,Double>>()
    val eventWindowCorrelation = HashMap<StaticEvent, HashMap<WTGNode,Double>>()

    fun getTargetIntentFilters_P1(): List<IntentFilter>{
        return targetIntFilters.filter { it.value < 1 }.map { it.key }
    }
    private var mainActivity = ""
    fun isMainActivity(currentState: State<*>):Boolean = (stateActivityMapping[currentState] == mainActivity)


    /**
     * Mutex for synchronization
     *
     *
     */
    val mutex = Mutex()
    private var trace: ExplorationTrace<*,*>? = null
    var fromLaunch = true

    //region Model feature override
    override suspend fun onAppExplorationFinished(context: ExplorationContext<*,*,*>) {
        this.join()
        produceTargetWidgetReport(context)
    }

    override fun onAppExplorationStarted(context: ExplorationContext<*, *, *>) {
        this.trace = context.explorationTrace
        this.stateGraph = context.getOrCreateWatcher<StateGraphMF>()
        this.statementMF = context.getOrCreateWatcher<StatementCoverageMF>()
        this.crashlist = context.getOrCreateWatcher<CrashListMF>()
        readAppModel()
        addCommonWordToDicitonary()
        val textInputFile = getTextInputFile()
        if (textInputFile!=null)
        {
            inputConfiguration = InputConfigurationFileHelper.readInputConfigurationFile(textInputFile)
            TextInput.inputConfiguration = inputConfiguration

        }
        TextInput.generalDictionary = generalDictionary
        AbstractStateManager.instance.init(this,appName)
        abstractTransitionGraph = AbstractTransitionGraph()
    }

    override suspend fun onContextUpdate(context: ExplorationContext<*, *, *>) {
        //this.join()
        try {
            mutex.lock()
            val interactions = ArrayList<Interaction<Widget>>()
            val lastAction = context.getLastAction()
            if (lastAction.actionType.isQueueEnd())
            {
                val lastQueueStart = context.explorationTrace.getActions().last { it.actionType.isQueueStart() }
                val lastQueueStartIndex = context.explorationTrace.getActions().lastIndexOf(lastQueueStart)
                val lastLaunchAction = context.explorationTrace.getActions().last { it.actionType.isLaunchApp() }
                val lastLauchActionIndex = context.explorationTrace.getActions().lastIndexOf(lastLaunchAction)
                if (lastLauchActionIndex>lastQueueStartIndex)
                {
                    interactions.add(lastLaunchAction)
                    currentRotation=0
                }
                else
                {
                    context.explorationTrace.getActions()
                            .takeLast(context.explorationTrace.getActions().lastIndex - lastQueueStartIndex+ 1)
                            .filterNot { it.actionType.isQueueStart() || it.actionType.isQueueEnd() }.let {
                                interactions.addAll(it)
                            }
                }
            }
            else
            {
                interactions.add(context.getLastAction())
            }
            isModelUpdated = false
            val prevState = context.getState(context.getLastAction().prevState) ?: context.model.emptyState
            val newState = context.getCurrentState()
            appPrevState = null
            if (prevState != context.model.emptyState) {
                //check this state outside of application
                appPrevState = prevState
                if (prevState.isHomeScreen)
                {
                    fromLaunch = true
                }
                else
                {
                    fromLaunch = false
                }
                if (!prevState.isHomeScreen && prevState.widgets.find { it.packageName == appName } != null)
                {

                    //update previous actions
                    lastExecutedInteraction = null

                    getCurrentEventCoverage()
                    val currentMethodCov = statementMF!!.getCurrentMethodCoverage()
                    if (currentMethodCov > lastMethodCoverage)
                    {
                        methodCoverageFromLastChangeCount = 0
                        lastMethodCoverage = currentMethodCov
                    }
                    else
                    {
                        methodCoverageFromLastChangeCount += 1
                    }
                    val currentModifiedMethodCov = statementMF!!.getCurrentModifiedMethodStatementCoverage()
                    if (currentModifiedMethodCov > lastModifiedMethodCoverage)
                    {
                        modifiedMethodCoverageFromLastChangeCount = 0
                        lastModifiedMethodCoverage = currentModifiedMethodCov
                    }
                    else
                    {
                        modifiedMethodCoverageFromLastChangeCount += 1
                    }
                }
                else if (prevState.isHomeScreen)
                {
                    if (getAbstractState(prevState)==null)
                    {
                        WTGLauncherNode.instance?.mappedStates?.add(prevState)
                    }
                }
            }

            if(newState.isAppHasStoppedDialogBox)
            {
                log.debug("Encountering Crash state.")
            }
            computeAbstractState(newState,prevState,interactions, context)
            if (appPrevState!=null) {
                computeAbstractInteraction(interactions, prevState, newState)
                updateAppModel(prevState, newState, interactions)
            }
        } finally {
            mutex.unlock()
        }

    }

    val guiInteractionList = ArrayList<Interaction<Widget>>()
    private fun computeAbstractInteraction(interactions: ArrayList<Interaction<Widget>>, prevState: State<*>, currentState: State<*>) {
        log.info("Computing Abstract Interaction.")
        if (interactions.isEmpty())
            return
        val interaction = interactions.first()
        if (guiInteractionList.contains(interaction))
        {
            log.info("This interaction is encounter before.")
        }
        else
        {
            guiInteractionList.add(interaction)
        }
        if (interaction.actionType == ActionType.CloseKeyboard.name)
            return
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(prevState)!!
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)!!
        if (interaction.targetWidget==null)
        {
            val allAbstractTransitions = abstractTransitionGraph.edges(prevAbstractState)
            val abstractTransitions = allAbstractTransitions.filter { it.label.abstractAction.actionName == interaction.actionType }
            if (abstractTransitions.isNotEmpty())
            {
                //Find the transition to current Abstract State
                val transition =  abstractTransitions.find { it.destination!!.data == currentAbstractState }
                if (transition != null)
                {
                    //if transition is existing, update interaction
                    lastExecutedInteraction = transition.label
                    lastExecutedInteraction!!.interactions.add(interaction)
                    if (transition.label.abstractAction.widgetGroup!=null)
                    {
                        transition.label.abstractAction.widgetGroup!!.exerciseCount+=1
                    }
                }
                else
                {
                    //if the transition does not exist
                    //create new transition
                    val transition = abstractTransitions.first()
                    lastExecutedInteraction = transition.label
                    lastExecutedInteraction!!.interactions.add(interaction)
                    abstractTransitionGraph.add(prevAbstractState,currentAbstractState,lastExecutedInteraction!!)
                    addImplicitAbstractInteraction(prevAbstractState,currentAbstractState,lastExecutedInteraction!!)
                }
            }
            else
            {
                //No recored abstract interaction before
                //Or the abstractInteraction is implicit
                //Record new AbstractInteraction

                lastExecutedAction = AbstractAction(
                            actionName = interaction.actionType
                    )

                val newAbstractInteraction = AbstractInteraction(
                        abstractAction = lastExecutedAction!!,
                        interactions = ArrayList(),
                        isImplicit = false)
                newAbstractInteraction.interactions.add(interaction)
                abstractTransitionGraph.add(prevAbstractState,currentAbstractState,newAbstractInteraction)
                lastExecutedInteraction = newAbstractInteraction
                addImplicitAbstractInteraction(prevAbstractState,currentAbstractState,lastExecutedInteraction!!)
            }
        }
        else
        {
            val widgetGroup = prevAbstractState.getWidgetGroup(interaction.targetWidget!!, prevState)
            assert(widgetGroup!=null, {"cannot get widgetgroup from ${interaction.targetWidget.toString()}"})
            if (widgetGroup!=null)
            {
                widgetGroup!!.exerciseCount+=1
                AbstractStateManager.instance.ABSTRACT_STATES.filter{ it.staticNode == prevAbstractState.staticNode }. filter { it.widgets.contains(widgetGroup!!) }.forEach {
                    val sameWidgetGroup = it.widgets.find { it == widgetGroup }!!
                    sameWidgetGroup.exerciseCount++
                }

                val allAbstractTransitions = abstractTransitionGraph.edges(prevAbstractState)
                val abstractTransitions = allAbstractTransitions.filter { it.label.abstractAction.actionName == interaction.actionType
                        && it.label.abstractAction.widgetGroup?.equals(widgetGroup)?:false }
                if (abstractTransitions.isNotEmpty())
                {
                    //Find the transition to current Abstract State
                    val transition =  abstractTransitions.find { it.destination!!.data == currentAbstractState }
                    if (transition != null)
                    {
                        //if transition is existing, update interaction
                        lastExecutedInteraction = transition.label
                        lastExecutedInteraction!!.interactions.add(interaction)
                        //transition.label.abstractAction.widgetGroup!!.exerciseCount+=1
                    }
                    else
                    {
                        //if the transition does not exist
                        //create new transition
                        val transition = abstractTransitions.first()
                        lastExecutedInteraction = transition.label
                        lastExecutedInteraction!!.interactions.add(interaction)
                        abstractTransitionGraph.add(prevAbstractState,currentAbstractState,lastExecutedInteraction!!)
                        addImplicitAbstractInteraction(prevAbstractState,currentAbstractState,lastExecutedInteraction!!)

                    }
                }
                else
                {
                    //No recored abstract interaction before
                    //Or the abstractInteraction is implicit
                    //Record new AbstractInteraction

                    lastExecutedAction = AbstractAction(
                            actionName = interaction.actionType,
                            widgetGroup = widgetGroup
                    )


                    val newAbstractInteraction = AbstractInteraction(
                            abstractAction = lastExecutedAction!!,
                            interactions = ArrayList(),
                            isImplicit = false)
                    newAbstractInteraction.interactions.add(interaction)
                    abstractTransitionGraph.add(prevAbstractState,currentAbstractState,newAbstractInteraction)
                    lastExecutedInteraction = newAbstractInteraction
                    addImplicitAbstractInteraction(prevAbstractState,currentAbstractState,lastExecutedInteraction!!)
                }
            }
        }
        log.info("Computing Abstract Interaction. - DONE")
        if (interaction.targetWidget==null || interaction.actionType.isTextInsert())
            return
        log.info("Refining Abstract Interaction.")
        AbstractStateManager.instance.refineModel(interaction, prevState)
        log.info("Refining Abstract Interaction. - DONE")
    }

    private fun addImplicitAbstractInteraction(prevAbstractState: AbstractState, currentAbstractState: AbstractState, abstractInteraction: AbstractInteraction) {
        val otherSameStaticNodeAbStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.staticNode == prevAbstractState.staticNode
                && it != prevAbstractState}
        otherSameStaticNodeAbStates.forEach {
            val abstractEdge = abstractTransitionGraph.edges(it).filter { it.label.abstractAction == abstractInteraction.abstractAction }
            if (abstractEdge.isEmpty())
            {
                if (abstractInteraction.abstractAction.widgetGroup==null || it is VirtualAbstractState)
                {
                    val implicitAbstractInteraction = AbstractInteraction(
                            abstractAction = abstractInteraction.abstractAction,
                            isImplicit = true
                    )
                    abstractTransitionGraph.add(it,currentAbstractState,implicitAbstractInteraction)
                }
                else
                {
                    //find Widgetgroup
                    val widgetGroup = it.widgets.find { it.attributePath.equals(abstractInteraction.abstractAction.widgetGroup.attributePath) }
                    if (widgetGroup!=null)
                    {
                        //find existing interaction again
                        val widgetAbstractEdge = abstractTransitionGraph.edges(it).filter {
                            it.label.abstractAction.actionName == abstractInteraction.abstractAction.actionName
                                    && it.label.abstractAction.widgetGroup == widgetGroup}
                        if (widgetAbstractEdge.isEmpty())
                        {
                            val implicitAbstractInteraction = AbstractInteraction(
                                    abstractAction = AbstractAction(actionName = abstractInteraction.abstractAction.actionName,
                                            widgetGroup = widgetGroup),
                                    isImplicit = true
                            )
                            abstractTransitionGraph.add(it,currentAbstractState,implicitAbstractInteraction)
                        }
                    }
                }
            }
        }
    }
    //endregion


    fun addDisablePathFromState(transitionPath: TransitionPath, edge: Edge<AbstractState,AbstractInteraction>){
        val abstractInteraction = edge.label
        if (abstractInteraction.abstractAction.actionName == "ItemClick" || abstractInteraction.abstractAction.actionName == "ItemLongClick"
                || abstractInteraction.abstractAction.actionName == "ItemSelected")
        {
            return
        }
        if (disableEdges.containsKey(edge))
        {
            disableEdges[edge] = disableEdges[edge]!! + 1
        }
        else
        {
            disableEdges[edge] = 1
        }
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

    private val stateFailedDialogs = arrayListOf<Pair<State<*>,String>>()
    fun addFailedDialog(state: State<*>, dialogName: String){
        stateFailedDialogs.add(Pair(state,dialogName))
    }

    private val unreachableTargetComponentState = arrayListOf<State<*>>()
    fun addUnreachableTargetComponentState (state: State<*>){
        log.debug("Add unreachable target component activity: ${stateActivityMapping[state]}")
        if (unreachableTargetComponentState.find { it.equals(state) }==null)
            unreachableTargetComponentState.add(state)
    }

    fun registerTriggeredEvents( abstractAction: AbstractAction, guiState: State<*>)
    {
        val abstractState = getAbstractState(guiState)!!
        val abstractInteraction = abstractTransitionGraph.edges(abstractState).find { it.label.abstractAction.equals(abstractAction) }?.label
        if (abstractInteraction!=null)
        {
            val staticEvent = abstractState.staticEventMapping[abstractInteraction]!!
            if (targetItemEvents.containsKey(staticEvent))
            {
                targetItemEvents[staticEvent]!!["count"] = targetItemEvents[staticEvent]!!["count"]!!+1
                if (targetItemEvents[staticEvent]!!["count"] == targetItemEvents[staticEvent]!!["max"]!!)
                {
                    untriggeredTargetEvents.remove(staticEvent)
                }
            }
            else
            {
                untriggeredTargetEvents.remove(staticEvent)
            }
        }

    }

    private fun computeAbstractState(newState: State<*>, prevState: State<*>, lastInteractions: List<Interaction<*>>, explorationContext: ExplorationContext<*, *, *>) {
        log.info("Computing Abstract State.")
        var currentActivity: String = ""
        runBlocking {
            currentActivity = getCurrentActivity()
        }
        if (mainActivity == "") {
            mainActivity = explorationContext.apk.launchableMainActivityName
        }
        stateActivityMapping[newState] = currentActivity
        val isFromLaunchApp = lastInteractions.find { it.actionType.isLaunchApp() }!=null
        val newAbstractState = AbstractStateManager.instance.getOrCreateNewTestState(newState,currentActivity,appName,isFromLaunchApp)
        increaseNodeVisit(abstractState = newAbstractState)
        if (isRecentPressMenu)
        {
            val prevAbstractState = AbstractStateManager.instance.getAbstractState(prevState)!!
            if (prevAbstractState!=newAbstractState)
            {
                if (prevAbstractState.hasOptionsMenu)
                    newAbstractState.hasOptionsMenu = false
                else
                    newAbstractState.hasOptionsMenu = true
            }
            else
            {
                prevAbstractState.hasOptionsMenu = false
            }
        }
        log.info("Computing Abstract State. - DONE")
    }

    val appStatesMap = HashMap<WTGNode, ArrayList<AbstractState>>()
    private fun saveAppState(newAbstractState: AbstractState ) {
        if (!appStatesMap.containsKey(newAbstractState.staticNode)) {
            appStatesMap.put(newAbstractState.staticNode, ArrayList())
        }
        appStatesMap[newAbstractState.staticNode]!!.add(newAbstractState)
    }


    private fun increaseNodeVisit(abstractState: AbstractState) {
        if (!activityVisitCount.containsKey(abstractState.activity))
        {
            activityVisitCount[abstractState.activity] = 1

        }
        else
        {
            activityVisitCount[abstractState.activity] = activityVisitCount[abstractState.activity]!! + 1
        }
        if (!abstractStateVisitCount.contains(abstractState)) {
            abstractStateVisitCount[abstractState] = 1
            saveAppState(abstractState)
        } else {
            abstractStateVisitCount[abstractState] = abstractStateVisitCount[abstractState]!! + 1
        }
        increaseVirtualAbstractStateVisitCount(abstractState)
    }

    private fun increaseVirtualAbstractStateVisitCount(abstractState: AbstractState) {
        val virtualAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find {
            it.staticNode == abstractState.staticNode
                    && it is VirtualAbstractState
        }
        if (virtualAbstractState!=null)
        {
            if (!abstractStateVisitCount.contains(virtualAbstractState)) {
                abstractStateVisitCount[virtualAbstractState] = 1
                increaseVirtualAbstractStateVisitCount(virtualAbstractState)
            } else {
                abstractStateVisitCount[virtualAbstractState] = abstractStateVisitCount[virtualAbstractState]!! + 1
            }
        }
    }

    private fun tryGetChildHavingResourceId(widget: Widget, currentState: State<*>): Widget {
        var childWidget: Widget? = widget
        while (childWidget!=null) {
            if (childWidget.childHashes.size != 1)
            {
                return widget
            }
            childWidget = currentState.widgets.find {
                it.idHash == childWidget!!.childHashes.first() }!!
            if (childWidget.resourceId.isNotBlank())
                return childWidget
        }
        return widget
    }

    /**
     * Return: True if model is updated, otherwise False
     */
    private suspend fun updateAppModel(prevState: State<*>, newState: State<*>, lastInteractions: List<Interaction<*>>): Boolean {
        //update lastChildExecutedEvent
        log.info("Updating App Model")
        while(!statementMF!!.statementRead)
        {
            delay(50)
        }
        statementMF!!.statementRead = false
        //update lastExecutedEvent
        if (lastExecutedInteraction == null)
        {
            log.debug("lastExecutedEvent is null")
            return false
        }
        return updateAppModelWithLastExecutedEvent(prevState, newState, lastInteractions)
    }

    private suspend fun updateAppModelWithLastExecutedEvent(prevState: State<*>, newState: State<*>, lastInteractions: List<Interaction<*>>): Boolean {
        assert(statementMF != null, { "StatementCoverageMF is null" })
        val event = lastExecutedInteraction!!
        val prevAbstractState = getAbstractState(prevState)!!
        val newAbstractState = getAbstractState(newState)!!

        //Extract text input widget data
        val inputText = HashMap<WidgetGroup, String>()
        prevState.visibleTargets.filter { it.isInputField }.forEach { widget ->
            if (widget.text.isNotBlank())
            {
                val widgetGroup = prevAbstractState.getWidgetGroup(widget,prevState)
                if (widgetGroup!=null)
                {
                    inputText.put(widgetGroup!!,widget.text)
                }


            }
        }
        val edge = abstractTransitionGraph.edge(prevAbstractState,newAbstractState,event)
        if (edge==null)
            return false
        abstractTransitionGraph.edgeConditions.put(edge!!,inputText)
        updateCoverage(prevAbstractState, newAbstractState, event)
        //create StaticEvent if it dose not exist in case this abstract Interaction triggered modified methods
        if(!event.modifiedMethods.isEmpty())
        {
            if(!prevAbstractState.staticEventMapping.containsKey(event))
            {
                createStaticEventFromAbstractInteraction(prevAbstractState,newAbstractState,event)
            }
        }
        return true
    }

    private fun createStaticEventFromAbstractInteraction(prevAbstractState: AbstractState, newAbstractState: AbstractState, abstractInteraction: AbstractInteraction) {
        val eventType = StaticEvent.getEventTypeFromActionName(abstractInteraction.abstractAction.actionName)
        if (eventType == EventType.fake_action)
            return
        if (abstractInteraction.abstractAction.widgetGroup == null)
        {
            val newStaticEvent = StaticEvent(
                    eventType = eventType,
                    widget = null,
                    activity = prevAbstractState.activity,
                    eventHandlers = ArrayList()
            )
            newStaticEvent.modifiedMethods.putAll(abstractInteraction.modifiedMethods)
            newStaticEvent.modifiedMethodStatement.putAll(abstractInteraction.modifiedMethodStatement)
            newStaticEvent.eventHandlers.addAll(abstractInteraction.handlers.map { it.key })
            allTargetStaticEvents.add(newStaticEvent)
            transitionGraph.add(prevAbstractState.staticNode,newAbstractState.staticNode,newStaticEvent)
            prevAbstractState.staticEventMapping.put(abstractInteraction,newStaticEvent)
        }
        else
        {
            val widgetGroup = abstractInteraction.abstractAction.widgetGroup
            if (prevAbstractState.staticWidgetMapping.contains(widgetGroup))
            {
                prevAbstractState.staticWidgetMapping[widgetGroup]!!.forEach { staticWidget->
                      val newStaticEvent = StaticEvent(
                        eventType = eventType,
                        widget = staticWidget,
                        activity = prevAbstractState.activity,
                        eventHandlers = ArrayList()
                )
                    newStaticEvent.modifiedMethods.putAll(abstractInteraction.modifiedMethods)
                    newStaticEvent.modifiedMethodStatement.putAll(abstractInteraction.modifiedMethodStatement)
                    newStaticEvent.eventHandlers.addAll(abstractInteraction.handlers.map { it.key })
                    allTargetStaticEvents.add(newStaticEvent)
                    transitionGraph.add(prevAbstractState.staticNode,newAbstractState.staticNode,newStaticEvent)
                    prevAbstractState.staticEventMapping.put(abstractInteraction,newStaticEvent)
                }
            }
            else
            {
                val staticWidget = StaticWidget(
                        widgetId = widgetGroup.hashCode().toString(),
                        resourceIdName = widgetGroup.getLocalAttributes()[AttributeType.resourceId]?:"",
                        resourceId = "",
                        activity = prevAbstractState.activity,
                        wtgNode = prevAbstractState.staticNode,
                        className = widgetGroup.getLocalAttributes()[AttributeType.className]?:"",
                        contentDesc = widgetGroup.getLocalAttributes()[AttributeType.contentDesc]?:"",
                        text = widgetGroup.getLocalAttributes()[AttributeType.text]?:""
                )
                val newStaticEvent = StaticEvent(
                        eventType = eventType,
                        widget = staticWidget,
                        activity = prevAbstractState.activity,
                        eventHandlers = ArrayList()
                )
                newStaticEvent.modifiedMethods.putAll(abstractInteraction.modifiedMethods)
                newStaticEvent.modifiedMethodStatement.putAll(abstractInteraction.modifiedMethodStatement)
                newStaticEvent.eventHandlers.addAll(abstractInteraction.handlers.map { it.key })
                allTargetStaticEvents.add(newStaticEvent)
                transitionGraph.add(prevAbstractState.staticNode,newAbstractState.staticNode,newStaticEvent)
                prevAbstractState.staticEventMapping.put(abstractInteraction,newStaticEvent)
            }
        }
    }

    private suspend fun updateCoverage(sourceAbsState: AbstractState, currentAbsState: AbstractState, abstractInteraction: AbstractInteraction) {
        val edge = abstractTransitionGraph.edge(sourceAbsState, currentAbsState, abstractInteraction)
        if (edge == null)
            return
        val edgeStatementCoverage = abstractTransitionGraph.statementCoverageInfo[edge]!!
        val edgeMethodCoverage = abstractTransitionGraph.methodCoverageInfo[edge]!!
        val event = sourceAbsState.staticEventMapping[abstractInteraction]
        statementMF!!.mutex.withLock {
            val recentExecutedStatementSize = statementMF!!.recentExecutedStatements.size
            if (recentExecutedStatementSize == 0)
            {

            }

            statementMF!!.recentExecutedStatements.forEach {
                if (!edgeStatementCoverage.contains(it)) {
                    edgeStatementCoverage.add(it)
                }
                if (abstractInteraction.modifiedMethodStatement.containsKey(it)) {
                    abstractInteraction.modifiedMethodStatement[it] = true
                } else {
                    if (statementMF!!.isModifiedMethodStatement(it)) {
                        abstractInteraction.modifiedMethodStatement.put(it, true)
                    }
                }
            }
            statementMF!!.recentExecutedMethods.forEach {
                val methodName = statementMF!!.getMethodName(it)
                if (!edgeMethodCoverage.contains(it)) {
                    edgeMethodCoverage.add(it)
                }
                if (unreachableModifiedMethods.contains(methodName))
                {
                    unreachableModifiedMethods.remove(methodName)
                }
                if (abstractInteraction.modifiedMethods.containsKey(it)) {
                    if (abstractInteraction.modifiedMethods[it] == false) {
                        abstractInteraction.modifiedMethods[it] = true

                        log.info("New modified method covered:")
                        log.info(statementMF!!.getMethodName(it))
                    }
                } else {
                    if (statementMF!!.isModifiedMethod(it)) {
                        abstractInteraction.modifiedMethods.put(it, true)
                        if (!allTargetStaticEvents.contains(event) && event !=null) {
                            allTargetStaticEvents.add(event)
                        }
                        log.info("New modified method covered:")
                        log.info(statementMF!!.getMethodName(it))
                    }
                }
                if (allEventHandlers.contains(it) || modifiedMethodTopCallersMap.containsKey(it))
                {
                    if (abstractInteraction.handlers.containsKey(it)){
                        abstractInteraction.handlers[it] = true
                    }
                    else
                    {
                        abstractInteraction.handlers.put(it,true)
                    }
                    log.info("Handlers logged: ${statementMF!!.getMethodName(it)}")
                }
            }
            statementMF!!.recentExecutedMethods.clear()
            statementMF!!.recentExecutedStatements.clear()
        }
        AbstractStateManager.instance.ABSTRACT_STATES.filter { it.staticEventMapping.containsKey(abstractInteraction) }.forEach {
            val staticEvent = it.staticEventMapping[abstractInteraction]!!
            val eventhandlers = ArrayList(staticEvent.eventHandlers)
            eventhandlers.forEach {
                if (!staticEvent.verifiedEventHandlers.contains(it))
                {
                    if (!abstractInteraction.handlers.containsKey(it))
                    {
                        staticEvent.eventHandlers.remove(it)
                    }
                    else if (abstractInteraction.handlers[it] == false)
                    {
                        staticEvent.eventHandlers.remove(it)
                    } else
                    {
                        if (!staticEvent.eventHandlers.contains(it))
                        {
                            staticEvent.eventHandlers.add(it)
                        }
                        staticEvent.verifiedEventHandlers.add(it)
                    }
                }
            }

        }
    }

    private fun updateSimilarAppStateAndInitialState(sourceNode: WTGNode, currentNode: WTGNode, inputTextData: HashMap<StaticWidget, String>,
                                                     event: StaticEvent) {
        if (sourceNode.classType!=currentNode.classType)
        {

        }
        if (sourceNode is WTGAppStateNode) {
            WTGAppStateNode.allNodes.filter { it.wtgRelatedNode == sourceNode.wtgRelatedNode }.forEach {
                if (transitionGraph.edges(it).filter {
                            it.label == event
                        }.isEmpty()) {//If there isn't same event and lead to another node --> we add this edge
                    if ((event.widget!=null && it.widgets.contains(event.widget))
                        || (event.widget==null))
                    {
                        transitionGraph.add(it, currentNode, event).also {
                            transitionGraph.edgeConditions.put(it, inputTextData)
                        }
                    }

                }
                if (it.wtgRelatedNode !is WTGAppStateNode)
                {
                    val initialNode = it.wtgRelatedNode
                    if ((event.widget!=null && initialNode.widgets.contains(event.widget))
                            || (event.widget==null))
                    {
                        transitionGraph.add(initialNode, currentNode, event).also {
                            transitionGraph.edgeConditions.put(it, inputTextData)
                        }
                    }
                }
            }

        }
    }

    private fun extractTextInputWidgetData(sourceNode: WTGNode, prevState: State<*>): HashMap<StaticWidget, String> {
        val inputTextData = HashMap<StaticWidget, String>()
        sourceNode.widgets.filter { it.isInputField }.forEach {
            val textInputWidget = prevState.widgets.find { w ->
                it.containGUIWidget(w)
            }
            if (textInputWidget != null) {
                inputTextData.put(it, textInputWidget.text)
            }
        }
        return inputTextData
    }

    //endregion

    private fun getCurrentEventCoverage() {
        val triggeredEventCount = allTargetStaticEvents.size - untriggeredTargetEvents.size
        // log.debug("Current target widget coverage: ${triggeredWidgets}/${allTargetStaticWidgets.size}=${triggeredWidgets / allTargetStaticWidgets.size.toDouble()}")
        log.debug("Current target event coverage: $triggeredEventCount/${allTargetStaticEvents.size} = ${triggeredEventCount/allTargetStaticEvents.size.toDouble()}")
    }

    //region state checking
    fun hasTargetWidget(currentState: State<*>): Boolean {
//        val currentActivity = stateActivityMapping[currentState]
//        assert(currentActivity != null)
//        if (unreachableTargetComponentState.contains(currentActivity))
//            return false
//        if (activity_TargetComponent_Map[currentActivity]?.size?:0 > 0)
//            return true
        if (unreachableTargetComponentState.find { it.equals(currentState)}!=null)
            return false
        val wtgNode = WTGNode.getWTGNodeByState(currentState)
        if (wtgNode!=null)
        {
            val events = untriggeredTargetEvents.filter {
                wtgNode.widgets.filter{ !it.mappedRuntimeWidgets.isEmpty() }.contains(it.widget)}
            if (events.size > 0)
            {
                return true
            }
        }
        return false
    }

    fun hasTargetWidget(currentNode: WTGNode): Boolean{
        currentNode.mappedStates.forEach {
            if (hasTargetWidget(it))
                return true
        }
        return false
    }
    val hasOptionsMenu = ArrayList<AbstractState>()
    fun hasOptionMenuItem(currentState: State<*>): Boolean {
        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (abstractState == null)
            return false
        if (hasOptionsMenu.contains(abstractState))
            return true
        return false
    }

    fun isOptionMenuOpen(currentState: State<*>): Boolean {
        val wtgNode = WTGNode.getWTGNodeByState(currentState)
        if (wtgNode is WTGOptionsMenuNode)
            return true
        return false
    }
    //endregion

    //region compute
    fun setTargetNode(targetAbstractState: AbstractState){
        lastTargetAbState = targetAbstractState
    }

    fun getProbabilities(state: State<*>): Map<Widget, Double> {
        try {
            runBlocking { mutex.lock() }
            val data = state.actionableWidgets
                    .map { it to (widgetProbability[it.uid] ?: 0.0) }
                    .toMap()

            assert(data.isNotEmpty()) { "No actionable widgets to be interacted with" }

            return data
        } finally {
            mutex.unlock()
        }
    }

    val unableFindTargetNodes = HashMap<WTGNode, Int>()



    fun getCandidateActivity_P1(currentActivity: String): List<String> {
        val candidates = ArrayList<String>()
        //get possible target widgets
        activity_TargetComponent_Map.filter { it.key!=currentActivity }.forEach {
            if (it.value.size>0)
                candidates.add(it.key)

        }
        return candidates
    }

    fun getNearestTargetActivityPaths_P1(currentState: State<*>): List<LinkedList<WindowTransition>> {
        //val activitiesWeights = arrayListOf<Pair<WindowTransition, Double>>()
        //get list of activities containing untriggered target widget
        val currentActivity = stateActivityMapping[currentState]!!
        val candidateActivities = getCandidateActivity_P1(currentActivity)
        val possibleTransitions = ArrayList<LinkedList<WindowTransition>>()
        candidateActivities.forEach {
            //findPathActivityToActivty(currentActivity, it, LinkedList(), possibleTransitions)
        }
        if (possibleTransitions.isEmpty())
            return emptyList()
        val sortedTransitions = possibleTransitions.sortedBy { it.size }
        val minTran = sortedTransitions.first()
        val nearestTransitions = sortedTransitions.filter { it.size == minTran.size }
        return nearestTransitions
        //calculate weight of each window transition to untriggered target widget
        //        allActivityActivityTransitions.forEach {
        //            if (it.source == currentActivity)
        //            {
        //                val target = it.target
        //                if (activity_TargetComponent_Map.containsKey(target))
        //                {
        //                    val targetWidgets = activity_TargetComponent_Map[target]
        //                    val untriggeredWidgets = targetWidgets!!.filter { untriggeredWidgets.contains(it) }
        //                    val weight = 1 - 1/(untriggeredWidgets.size.toDouble())
        //                    activitiesWeights.add(Pair(it,weight))
        //                }
        //
        //            }
        //        }
        //        //sort descendent by weight
        //        val candidate = activitiesWeights.sortedByDescending { it.second }.firstOrNull()
        //
        //        return candidate?.first?:null
    }

    //endregion

    //region phase2
    var remainPhaseStateCount: Int = 0
    val notFullyCoveredTargetEvents = HashMap<StaticEvent,Int>() //Event - number of exercise

    fun resetIneffectiveActionCounter(){
        modifiedMethodCoverageFromLastChangeCount = 0
    }




    fun validateEvent(e: StaticEvent, currentState: State<*>): List<AbstractAction> {
        val abstractStateManager = AbstractStateManager.instance
        val currentAbstractState = getAbstractState(currentState)!!

        val availableAbstractActions = currentAbstractState.staticEventMapping.filter { it.value == e }.map { it.key.abstractAction }
        return availableAbstractActions
    }

    private fun isNotFullCoveredEvent(event: StaticEvent): Boolean {
        val uncoveredMethods = event.modifiedMethods.filter { it.value==false }.size
        if (uncoveredMethods>0)
            return true
        val uncoveredStatements = event.modifiedMethodStatement.filter { it.value==false }.size
        if (uncoveredStatements>0)
            return true
        return false
    }


    var numberOfContinuousRandomAction: Int =0
    fun canExerciseTargetActivty(): Boolean {
        //TODO: Implement it before using
        return true
    }


    //endregion



    fun registerTransitionPath(source: AbstractState, destination: AbstractState, fullPath: TransitionPath) {
        if (!allAvailableTransitionPaths.containsKey(Pair(source, destination)))
            allAvailableTransitionPaths.put(Pair(source, destination), ArrayList())
        allAvailableTransitionPaths[Pair(source, destination)]!!.add(fullPath)
    }

    fun checkIsDisableEdge(edge: Edge<AbstractState, AbstractInteraction>): Boolean {
        if (disableEdges.containsKey(edge)) {
                return true
        } else
           return false
    }

    fun isPressBackCanGoToHomescreen(currentAbstractState: AbstractState): Boolean {

        val pressBackEdges = abstractTransitionGraph.edges(currentAbstractState).filter {
                    it.label.abstractAction.actionName.isPressBack()
        }
        val backToHomeScreen = pressBackEdges.find { it.destination!=null && it.destination!!.data.isHomeScreen }
        return (backToHomeScreen!=null)
    }

    fun isPressBackCanGoToHomescreen(currentState: State<*>): Boolean {
        val currentAbstractState = getAbstractState(currentState)
        if (currentAbstractState == null)
            return false
        val pressBackEdges = abstractTransitionGraph.edges(currentAbstractState).filter {
            it.label.abstractAction.actionName.isPressBack()
        }
        val backToHomeScreen = pressBackEdges.find { it.destination!=null && it.destination!!.data.isHomeScreen }
        return (backToHomeScreen!=null)
    }

    fun getRuntimeWidgets(widgetGroup: WidgetGroup, currentState: State<*>): List<Widget> {
        val allGUIWidgets = widgetGroup.getGUIWidgets(currentState)
        return allGUIWidgets
    }

    //endregion
    init {

    }

    private fun addCommonWordToDicitonary() {
        generalDictionary.add("love")
        generalDictionary.add("weather")
        generalDictionary.add("school")
        generalDictionary.add("city")
        generalDictionary.add("theater")
    }

    //region statical analysis helper
    private fun isContextMenu(source: String): Boolean {
        if (source == "android.view.ContextMenu")
            return true
        return false
    }

    private fun isOptionMenu(source: String): Boolean {
        if (source == "android.view.Menu")
            return true
        return false
    }


    private fun getOptionMenuActivity(staticWidget: StaticWidget): String{
        allActivityOptionMenuItems.forEach {
            if(it.value.contains(staticWidget))
            {
                return it.key
            }
        }
        return ""
    }


    private fun isDialog(source: String) = allDialogOwners.filter { it.value.contains(source) }.size > 0
    //endregion







    fun getAppName() = appName

    fun getStateActivity( state: State<*>): String
    {
        if (stateActivityMapping.contains(state))
            return stateActivityMapping[state]!!
        else
            return ""
    }

    fun getAbstractState(state: State<*>): AbstractState?
    {
        return AbstractStateManager.instance.getAbstractState(state)
    }



    //region readJSONFile




    fun readAppModel() {
        val appModelFile = getAppModelFile()
        if (appModelFile != null) {
            //val activityEventList = List<ActivityEvent>()
            val jsonData = String(Files.readAllBytes(appModelFile))
            val jObj = JSONObject(jsonData)
            log.debug("Reading Window Transition Graph")
            transitionGraph.constructFromJson(jObj)
            readWindowWidgets(jObj)
            readMenuItemTexts(jObj)
            readActivityDialogs(jObj)
            log.debug("Reading modified method invocation")
            readModifiedMethodTopCallers(jObj)
            readModifiedMethodInvocation(jObj)
            readUnreachableModfiedMethods(jObj)
            log.debug("Reading all strings")
            readAllStrings(jObj)
            readEventCorrelation(jObj)
            readMethodDependency(jObj)
            readWindowDependency(jObj)
        }
        readIntentModel()
    }

    val methodTermsHashMap = HashMap<String, HashMap<String, Long>>()
    val windowTermsHashMap = HashMap<WTGNode, HashMap<String,Long>>()

    private fun readWindowDependency(jObj: JSONObject) {
        val jsonWindowTerm = jObj.getJSONObject("windowsDependency")
        if (jsonWindowTerm != null)
        {
            windowTermsHashMap.putAll(StaticAnalysisJSONFileHelper.readWindowTerms(jsonWindowTerm, transitionGraph))
        }
    }

    private fun readMethodDependency(jObj: JSONObject) {
        var jsonMethodDepedency = jObj.getJSONObject("methodDependency")
        if (jsonMethodDepedency != null)
        {
            methodTermsHashMap.putAll(StaticAnalysisJSONFileHelper.readMethodTerms(jsonMethodDepedency,statementMF!!))
        }
    }
    private fun readEventCorrelation(jObj: JSONObject) {
        var eventCorrelationJson = jObj.getJSONObject("event_window_Correlation")
        if (eventCorrelationJson!=null)
        {
            staticEventWindowCorrelation.putAll(StaticAnalysisJSONFileHelper.readEventWindowCorrelation(eventCorrelationJson,transitionGraph))
        }
    }

    private fun readIntentModel() {
        val intentModelFile = getIntentModelFile()
        if (intentModelFile != null) {
            val jsonData = String(Files.readAllBytes(intentModelFile))
            val jObj = JSONObject(jsonData)
            val activitiesJson = jObj.getJSONArray("activities")
            activitiesJson.forEach {
                StaticAnalysisJSONFileHelper.readActivityIntentFilter(it as JSONObject, intentFilters, appName)
            }
            intentFilters.forEach { t, u ->
                val activityName = t
                val qualifiedActivityName = activityName
                var intentActivityNode = WTGNode.allNodes.find { it.classType == qualifiedActivityName }
                if (intentActivityNode == null) {
                    intentActivityNode = WTGActivityNode.getOrCreateNode(WTGActivityNode.getNodeId(), qualifiedActivityName)
                }
                u.forEach {
                    val intentEvent = StaticEvent(activity = qualifiedActivityName, eventType = EventType.callIntent,
                            eventHandlers = ArrayList(), widget = null)
                    intentEvent.data = it
                    for (meaningNode in WTGNode.allMeaningNodes) {
                        transitionGraph.add(meaningNode, intentActivityNode!!, intentEvent)
                    }

                }

                if (allTargetStaticEvents.filter {
                            it.activity.contains(activityName)
                                    && (it.eventType == EventType.implicit_rotate_event
                                    || it.eventType == EventType.implicit_lifecycle_event
                                    || it.eventType == EventType.implicit_power_event)
                        }.isNotEmpty()) {
                    u.forEach {
                        targetIntFilters.put(it, 0)
                    }
                }
            }
        }
    }

    private fun getIntentModelFile(): Path? {
        if (!Files.exists(resourceDir)) {
            RegressionTestingMF.log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val intentModelFile = getIntentModelFile(appName, resourceDir)
            if (intentModelFile != null)
                return intentModelFile
            else {
                RegressionTestingMF.log.warn("Provided directory ($resourceDir) does not contain " +
                        "the corresponding intent model file.")
                return null
            }
        }
    }

    private fun getIntentModelFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-intent.json")
                }
                .findFirst()
                .orElse(null)
    }

    private fun readMenuItemTexts(jObj: JSONObject) {
        var jMap = jObj.getJSONObject("menuItemTexts")
        StaticAnalysisJSONFileHelper.readMenuItemText(jMap,WTGOptionsMenuNode.allNodes)
    }

    private fun readAllStrings(jObj: JSONObject) {
        var jMap = jObj.getJSONArray("allStrings")
        StaticAnalysisJSONFileHelper.readAllStrings(jMap,generalDictionary)
    }
    private fun readUnreachableModfiedMethods(jObj: JSONObject) {
        var jMap = jObj.getJSONArray("unreachableModifiedMethods")
        StaticAnalysisJSONFileHelper.readUnreachableModifiedMethods(jsonArray = jMap, methodList = unreachableModifiedMethods)
    }

    private fun readWindowWidgets(jObj: JSONObject) {
        //var jMap = jObj.getJSONObject("allWindow_Widgets")
        var jMap = jObj.getJSONObject("allWidgetEvent")
        StaticAnalysisJSONFileHelper.readAllWidgetEvents(jMap, transitionGraph, allEventHandlers, statementMF!!)

    }

    val modifiedMethodTopCallersMap = HashMap<String, Set<String>>()
    private fun readModifiedMethodTopCallers(jObj: JSONObject){
        var jMap = jObj.getJSONObject("modiMethodTopCaller")
        StaticAnalysisJSONFileHelper.readModifiedMethodTopCallers(jMap,modifiedMethodTopCallersMap,statementMF!!)
    }

    private fun readModifiedMethodInvocation(jObj: JSONObject) {
        var jMap = jObj.getJSONObject("modiMethodInvocation")
        StaticAnalysisJSONFileHelper.readModifiedMethodInvocation(jsonObj = jMap,
                transitionGraph = transitionGraph,
                allTargetStaticEvents = allTargetStaticEvents,
                allTargetStaticWidgets = allTargetStaticWidgets,
                statementCoverageMF = statementMF!!)
        untriggeredWidgets.addAll(allTargetStaticWidgets)
        untriggeredTargetEvents.addAll(allTargetStaticEvents)
        allTargetStaticEvents.filter { listOf<EventType>(EventType.item_click, EventType.item_long_click,
                EventType.item_selected).contains(it.eventType)}.forEach {
            val eventInfo = HashMap<String,Int>()
            targetItemEvents.put(it,eventInfo)
            eventInfo["max"] = 3
            eventInfo["count"] = 0

        }
    }


    fun readActivityDialogs(jObj: JSONObject) {
        val jMap = jObj.getJSONObject("allActivityDialogs")
        //for each Activity transition
        jMap.keys().asSequence().forEach { key ->
            val activity = key as String
            if (!allDialogOwners.containsKey(activity)) {
                allDialogOwners[activity] = ArrayList()
            }
            val dialogs = jMap[key] as JSONArray
            dialogs.forEach {
                allDialogOwners[activity]!!.add(it as String)
            }
        }
    }

    fun readActivityOptionMenuItem(jObj: JSONObject){
        val jMap = jObj.getJSONObject("allActivityOptionMenuItems")
        //for each Activity transition
        jMap.keys().asSequence().forEach { key ->
            val activity = key as String
            if (!allActivityOptionMenuItems.contains(activity))
            {
                allActivityOptionMenuItems[activity] = ArrayList()
            }
//            val menuItemsJson = jMap[key] as JSONArray
//            menuItemsJson.forEach {
//                val widgetInfo = StaticAnalysisJSONFileHelper.widgetParser(it as String)
//                val menuItemWidget = StaticWidget.getOrCreateStaticWidget(widgetInfo["id"]!!,"android.view.menu",false)
//                allActivityOptionMenuItems[activity]!!.add(menuItemWidget)
//            }

        }
    }


    fun getAppModelFile(): Path? {
        if (!Files.exists(resourceDir)) {
            RegressionTestingMF.log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val instrumentationFile = getAppModelFile(appName, resourceDir)
            if (instrumentationFile != null)
                return instrumentationFile
            else {
                RegressionTestingMF.log.warn("Provided directory ($resourceDir) does not contain " +
                        "the corresponding instrumentation file.")
                return null
            }
        }
    }

    private fun getAppModelFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-AppModel.json")
                }
                .findFirst()
                .orElse(null)
    }

    private fun getTextInputFile(): Path?{
        if (!Files.exists(resourceDir)) {
            RegressionTestingMF.log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val textInputFile = getTextInputFile(appName, resourceDir)
            if (textInputFile != null)
                return textInputFile
            else {
                RegressionTestingMF.log.warn("Provided directory ($resourceDir) does not contain " +
                        "the corresponding text input file.")
                return null
            }
        }
    }

    private fun getTextInputFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-input.json")
                }
                .findFirst()
                .orElse(null)
    }

    private fun addWidgetToActivtity_TargetWidget_Map(activity: String, event: StaticEvent) {
        if (!activity_TargetComponent_Map.containsKey(activity)) {
            activity_TargetComponent_Map[activity] = ArrayList()
        }
        if (!activity_TargetComponent_Map[activity]!!.contains(event)) {
            activity_TargetComponent_Map[activity]!!.add(event)
        }
    }

    private fun haveContextMenuOnItsWidget(currentActivity: String?): Boolean{
        val wtgNode = WTGNode.allNodes.find { it.classType == currentActivity }
        if (wtgNode == null)
            return false
        return transitionGraph.haveContextMenu(wtgNode)
    }

    //endregion
    fun produceTargetWidgetReport(context: ExplorationContext<*,*,*>) {
        val sb = StringBuilder()
        sb.appendln("Strategy statistics:")
        sb.appendln("ExerciseTargetComponent: ${ExerciseTargetComponentTask.executedCount} ")
        sb.appendln("GoToTargetNode: ${GoToTargetNodeTask.executedCount}")
        sb.appendln("GoToAnotherNode: ${GoToAnotherNode.executedCount}")
        sb.appendln("RandomExploration: ${RandomExplorationTask.executedCount}")
        var totalEvents = allTargetStaticEvents.size
        sb.append("Total target events: $totalEvents\n")
        sb.append("Triggered events:\n")
        val triggeredEvents = allTargetStaticEvents.filter { !untriggeredTargetEvents.contains(it) }
        triggeredEvents.forEach {
            if (it.widget!=null){
                sb.append("In ${it.activity}: Widget ${it.widget.className}:${it.widget.resourceIdName}:${it.eventType}\n")
            }
            else
            {
                sb.append("In ${it.activity}: Widget null:${it.eventType}\n")
            }

        }
        sb.append("Miss events: ${totalEvents - triggeredEvents.size}\n")

        untriggeredTargetEvents
                .forEach {
                    if (it.widget!=null){
                        sb.append("In ${it.activity}:Widget ${it.widget.className}:${it.widget.resourceIdName}:${it.eventType}\n")
                    }
                    else
                    {
                        sb.append("Widget null:${it.eventType}\n")
                    }
                }

        sb.appendln("Unreached node: ")
        WTGNode.allNodes.filterNot {
            it is WTGAppStateNode || it is WTGLauncherNode
                || it is WTGOutScopeNode || it is WTGFakeNode
        }. filter { node -> AbstractStateManager.instance.ABSTRACT_STATES.find { it.staticNode == node } == null} .forEach {
                sb.appendln(it.toString())
        }
      /*  sb.appendln("Unmatched widget: ${allTargetStaticWidgets.filter { it.mappedRuntimeWidgets.isEmpty() }.size}")
        allTargetStaticWidgets.forEach {
            if (it.mappedRuntimeWidgets.isEmpty())
            {
                sb.appendln("${it.resourceIdName}-${it.className}-${it.widgetId} in ${it.activity}")
            }
        }*/

        val numberOfAppStates = AbstractStateManager.instance.ABSTRACT_STATES.size
        sb.appendln("Number of App states: $numberOfAppStates")
//        disablePaths.forEach {
//            sb.append("State ${it.first.uid}-${it.second.getNextRoot()!!.toString()}\n")
//            sb.append("**Path details:\n")
//            var node = it.second.getNextRoot()
//            while (node != null)
//            {
//                sb.append(node.toString())
//                val edge = it.second.edges(node).firstOrNull()
//                if (edge==null)
//                {
//                    node = null
//                }
//                else
//                {
//                    node = edge.destination?.data
//                    sb.append("do ${edge.label.eventType} on ${edge.label.widget?.className}:${edge.label.widget?.resourceIdName}-->")
//                }
//            }
//
//            sb.append("end\n")
//
//        }


        val outputFile = context.model.config.baseDir.resolve(targetWidgetFileName)
        RegressionTestingMF.log.info("Prepare writing triggered widgets report file: " +
                "\n- File name: ${outputFile.fileName}" +
                "\n- Absolute path: ${outputFile.toAbsolutePath().fileName}")

        Files.write(outputFile, sb.lines())
        RegressionTestingMF.log.info("Finished writing report in ${outputFile.fileName}")
    }




    fun getCurrentActivity(interactions: List<Interaction<*>>, explorationContext: ExplorationContext<*,*,*>):String {
        var lastEncounteredActivity = ""
        interactions.forEach { interaction ->
            if (interaction.actionType.isLaunchApp())
                return explorationContext.apk.launchableMainActivityName

            val loggedInteractoion =  explorationContext.explorationTrace.getActions().find { it.actionId == interaction.actionId }
            if (loggedInteractoion != null)
            {
                val logs = loggedInteractoion.deviceLogs.map { ApiLogcatMessage.from(it) }

                logs.filter { it.methodName.toLowerCase().startsWith("startactivit") }
                        .forEach { log ->
                            val intent = log.getIntents()
                            // format is: [ '[data=, component=<HERE>]', 'package ]
                            if (intent.isNotEmpty()) {
                                lastEncounteredActivity =  intent[0].substring(intent[0].indexOf("component=") + 10).replace("]", "")
                            }
                        }
            }

        }

        return lastEncounteredActivity
    }

    //Widget override
    fun Widget.isInteractable(): Boolean = enabled && ( isInputField || clickable || checked != null || longClickable || scrollable)

    fun getToolBarMoreOptions(currentState: State<*>): Widget? {
        currentState.widgets.filter{it.isVisible && it.contentDesc.contains("More options")}.forEach {
            if (Helper.hasParentWithType(it,currentState,"LinearLayoutCompat"))
            {
                return it
            }
        }
        return null
    }

    fun getLeastExerciseWidgets(currentState: State<*>): Map<Widget, WidgetGroup> {
        val abstractState = getAbstractState(currentState)
        if (abstractState == null)
            return emptyMap()
        val interactiveWidgetGroups = abstractState.widgets
        if (interactiveWidgetGroups.isNotEmpty())
        {
            val leastExerciseCount = interactiveWidgetGroups.minBy { it.exerciseCount }!!.exerciseCount
            val leastExerciseWidgetGroups = interactiveWidgetGroups.filter { it.exerciseCount == leastExerciseCount
                    && !it.attributePath.isInputField()}
            val result = HashMap<Widget,WidgetGroup>()
            leastExerciseWidgetGroups.forEach {
                val widgets = it.getGUIWidgets(currentState)
                widgets.forEach { w ->
                    result.put(w,it)
                }
            }
            return result
        }
        return emptyMap()
    }

    fun computeEventWindowCorrelation() {
        eventWindowCorrelation.clear()
        val eventsTerms = accumulateEventsDependency()
        val ir = InformationRetrieval<WTGNode,String>(windowTermsHashMap)
        eventsTerms.forEach {
            val result = ir.searchSimilarDocuments(it.value,10)
            val correlation = HashMap<WTGNode, Double>()
            result.forEach {
                correlation.put(it.first,it.second)
            }
            eventWindowCorrelation.put(it.key,correlation)
        }
    }

    private fun accumulateEventsDependency(): HashMap<StaticEvent, HashMap<String, Long>> {
        val result = HashMap<StaticEvent, HashMap<String,Long>>()
        allTargetStaticEvents.forEach { event ->
            val eventDependency = HashMap<String,Long>()
            event.eventHandlers.forEach {
                if (methodTermsHashMap.containsKey(it))
                {
                    if(methodTermsHashMap[it]!!.isNotEmpty())
                    {

                        methodTermsHashMap[it]!!.forEach { term, count ->
                            if (!eventDependency.containsKey(term))
                                eventDependency.put(term,count)
                            else
                                eventDependency[term] = eventDependency[term]!! + count
                        }
                    }
                }

            }
            result.put(event,eventDependency)
        }
        return result
    }

    companion object {

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(RegressionTestingMF::class.java) }
        object RegressionStrategy: PropertyGroup() {
            val use by booleanType
        }


    }
}

enum class MyStrategy {
    INITIALISATION,
    RANDOM_TARGET_WIDGET_SELECTION,
    SEARCH_FOR_TARGET_WIDGET,
    RANDOM_EXPLORATION,
    REACH_MORE_MODIFIED_METHOD
}
