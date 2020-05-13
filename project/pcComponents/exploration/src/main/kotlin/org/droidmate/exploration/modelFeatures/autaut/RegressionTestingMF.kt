package org.droidmate.exploration.modelFeatures.autaut

import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.doubleType
import com.natpryce.konfig.getValue

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.droidmate.exploration.strategy.autaut.task.GoToAnotherWindow
import org.droidmate.exploration.strategy.autaut.task.RandomExplorationTask
import org.droidmate.exploration.strategy.autaut.task.GoToTargetWindowTask
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext

class RegressionTestingMF(private val appName: String,
                          private val resourceDir: Path,
                          private val getCurrentActivity: suspend () -> String,
                          private val getDeviceRotation: suspend () -> Int) : ModelFeature() {
    val textFilledValues = ArrayList<String>()
    private val targetWidgetFileName = "autaut-report.txt"
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
    var inputConfiguration: InputConfiguration?=null
    var currentRotation: Rotation = Rotation.PORTRAIT
    var phase: Int = 1
    private val widgetProbability = mutableMapOf<UUID, Double>() // probability of each widget invoking modified methods
    private val runtimeWidgetInfos = mutableMapOf<Pair<WTGNode,UUID>, Triple<State<*>, StaticWidget,HashMap<String, Any>>>()//Key: widget id
    private val widgets_modMethodInvocation = mutableMapOf<String, Widget_MethodInvocations>()
    private val allDialogOwners = mutableMapOf<String, ArrayList<String>>() // window -> listof (Dialog)
    private val allMeaningfulWidgets = hashSetOf<StaticWidget>() //widgetId -> idWidget
    val allTargetStaticWidgets = hashSetOf<StaticWidget>() //widgetId -> idWidget
    val allTargetStaticEvents = hashSetOf<StaticEvent>()
    val allTargetWindows = hashSetOf<WTGNode>()
    val allTargetHandlers = hashSetOf<String>()
    val allEventHandlers = hashSetOf<String>()
    private val allActivityOptionMenuItems = mutableMapOf<String,ArrayList<StaticWidget> >()  //idWidget
    private val allContextMenuItems = arrayListOf<StaticWidget>()
    private val activityTransitionWidget = mutableMapOf<String, ArrayList<StaticWidget>>() // window -> Listof<StaticWidget>
    private val activity_TargetComponent_Map = mutableMapOf<String, ArrayList<StaticEvent>>() // window -> Listof<StaticWidget>

    val targetItemEvents = HashMap<StaticEvent, HashMap<String,Int>>()
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
    var appRotationSupport = true
    val abstractStateVisitCount = HashMap<AbstractState, Int>()
    val windowVisitCount = HashMap<WTGNode, Int>()
    var appPrevState: State<*>? =null
    var windowStack: Stack<WTGNode> = Stack<WTGNode>()
    var prevWindowState: State<*>? = null
    val allAvailableTransitionPaths =  HashMap<Pair<AbstractState,AbstractState>,ArrayList<TransitionPath>>()

    var modifiedMethodCoverageFromLastChangeCount: Int = 0
    var methodCoverageFromLastChangeCount: Int = 0
    var lastModifiedMethodCoverage: Double = 0.0
    var lastMethodCoverage: Double = 0.0
    val unreachableModifiedMethods = ArrayList<String>()

    val intentFilters = HashMap<String, ArrayList<IntentFilter>>()
    val targetIntFilters = HashMap<IntentFilter,Int>()

    val staticEventWindowCorrelation = HashMap<StaticEvent, HashMap<WTGNode,Double>>()
    val untriggeredTargetHandlers = hashSetOf<String>()

    var stage1MethodCoverage: Double = 0.0
    var stage2MethodCoverage: Double = 0.0
    var stage1ModifiedMethodCoverage: Double = 0.0
    var stage2ModifiedCoverage: Double = 0.0
    var stage1StatementCoverage: Double = 0.0
    var stage2StatementCoverage: Double =0.0
    var stage1ModifiedStatementCoverage: Double = 0.0
    var stage2ModifiedStatementCoverage: Double =0.0
    var stage1Actions: Int = 0
    var stage2Actions: Int = 0
    var stage3Actions: Int = 0

    fun getMethodCoverage(): Double{
        return statementMF!!.getCurrentMethodCoverage()
    }

    fun getStatementCoverage(): Double {
        return statementMF!!.getCurrentCoverage()
    }

    fun getModifiedMethodCoverage(): Double {
        return statementMF!!.getCurrentModifiedMethodCoverage()
    }

    fun getModifiedMethodStatementCoverage(): Double {
        return statementMF!!.getCurrentModifiedMethodStatementCoverage()
    }
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
        val textInputFile = getTextInputFile()
        if (textInputFile!=null)
        {
            inputConfiguration = InputConfigurationFileHelper.readInputConfigurationFile(textInputFile)
            TextInput.inputConfiguration = inputConfiguration

        }
        AbstractStateManager.instance.init(this, appName)
        appPrevState = null

    }

    override suspend fun onContextUpdate(context: ExplorationContext<*, *, *>) {
        //this.join()
        mutex.lock()
        try {
            log.info("RegressionTestingMF: Start OnContextUpdate")
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
                }
                else
                {
                    context.explorationTrace.getActions()
                            .takeLast(context.explorationTrace.getActions().lastIndex - lastQueueStartIndex+ 1)
                            .filterNot { it.actionType.isQueueStart() || it.actionType.isQueueEnd() || it.actionType.isFetch() }.let {
                                interactions.addAll(it)
                            }
                }
            }
            else
            {
                if (!context.getLastActionType().isFetch()) {
                    interactions.add(context.getLastAction())
                }
            }
            isModelUpdated = false
            val prevState = context.getState(context.getLastAction().prevState) ?: context.model.emptyState
            val newState = context.getCurrentState()
            if (prevState == context.model.emptyState) {
                if (windowStack.isEmpty()) {
                    windowStack.push(WTGLauncherNode.getOrCreateNode())
                }
            }
            if (prevState != context.model.emptyState) {
                //check this state outside of application
                appPrevState = prevState
                if (prevState.isHomeScreen)
                {
                    //reset window stack
                    windowStack.clear()
                    windowStack.push(WTGLauncherNode.getOrCreateNode())
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

                    //getCurrentEventCoverage()
                    val currentCov = statementMF!!.getCurrentCoverage()
                    if (currentCov > lastMethodCoverage)
                    {
                        methodCoverageFromLastChangeCount = 0
                        lastMethodCoverage = currentCov
                    }
                    else
                    {
                        methodCoverageFromLastChangeCount += 1
                    }
                    val currentModifiedMethodStmtCov = statementMF!!.getCurrentModifiedMethodStatementCoverage()
                    if (currentModifiedMethodStmtCov > lastModifiedMethodCoverage)
                    {
                        modifiedMethodCoverageFromLastChangeCount = 0
                        lastModifiedMethodCoverage = currentModifiedMethodStmtCov
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
            else {
                windowStack.clear()
                windowStack.push(WTGLauncherNode.getOrCreateNode())
            }

            if (newState != context.model.emptyState) {
                if (newState.isAppHasStoppedDialogBox) {
                    log.debug("Encountering Crash state.")
                }

                currentRotation = computeRotation(newState)
                computeAbstractState(newState, interactions, context)
                var prevAbstractState = getAbstractState(prevState)
                if (prevAbstractState == null && prevState != context.model.emptyState) {
                   computeAbstractState(prevState, emptyList(),context)
                }
                val currentAbstractState = getAbstractState(newState)
                if (prevAbstractState != null && currentAbstractState != null) {

                    if (windowStack.contains(currentAbstractState.window) && windowStack.size>1) {
                        // Return to the prev window
                        // Pop the window
                        while (windowStack.pop()!=currentAbstractState.window) {

                        }
                    } else {
                        if (currentAbstractState.window != prevAbstractState.window) {
                            windowStack.push(prevAbstractState.window)
                        } else if (currentAbstractState.isOpeningKeyboard) {
                            windowStack.push(currentAbstractState.window)
                        }
                    }
                    if (windowStack.isEmpty()) {
                        windowStack.push(prevAbstractState.window)
                    }
                    if (interactions.isNotEmpty()) {
                        computeAbstractInteraction(interactions, prevState, newState, windowStack.peek())
                        updateAppModel(prevState, newState, interactions)
                    }

                }
            }
        } finally {
                mutex.unlock()
        }
    }

    private fun computeRotation(newState: State<*>): Rotation {
        /*val roots = newState.widgets.filter { !it.hasParent || it.resourceId=="android.id/content"}
        if (roots.isEmpty())
            return Rotation.PORTRAIT
        val root = roots.sortedBy { it.boundaries.height+it.boundaries.width }.last()
        val height = root.boundaries.height
        val width = root.boundaries.width
        if (height > width) {
            return Rotation.PORTRAIT
        }
        else
            return Rotation.LANDSCAPE*/
        var rotation: Int = 0
        runBlocking {
            rotation = getDeviceRotation()
        }
        if (rotation == 0 || rotation == 2)
            return Rotation.PORTRAIT
        return Rotation.LANDSCAPE


    }

    val guiInteractionList = ArrayList<Interaction<Widget>>()

    private fun computeAbstractInteraction(interactions: ArrayList<Interaction<Widget>>, prevState: State<*>, currentState: State<*>, prevprevWindow: WTGNode) {
        log.info("Computing Abstract Interaction.")
        if (interactions.isEmpty())
            return
        val interaction = interactions.first()!!
        if (guiInteractionList.contains(interaction))
        {
            log.info("This interaction is encounter before.")
        }
        else
        {
            guiInteractionList.add(interaction)
        }

        val prevAbstractState = AbstractStateManager.instance.getAbstractState(prevState)
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)!!
        if (prevAbstractState == null)
            return
        if (interaction.actionType == "RotateUI") {
            val prevStateRotation = computeRotation(prevState)
            if (prevStateRotation == currentRotation) {
                appRotationSupport = false
            }
        }
        if (isRecentPressMenu) {
            if (prevAbstractState != currentAbstractState) {
                if (prevAbstractState.hasOptionsMenu)
                    currentAbstractState.hasOptionsMenu = false
                else
                    currentAbstractState.hasOptionsMenu = true
            } else {
                prevAbstractState.hasOptionsMenu = false
            }
            isRecentPressMenu = false
        }
        val actionType: String = normalizeActionType(interaction.actionType)
        if (interaction.targetWidget==null)
        {
            val allAbstractTransitions = abstractTransitionGraph.edges(prevAbstractState)
            val abstractTransitions = allAbstractTransitions.filter {
                it.label.abstractAction.actionName == interaction.actionType
                        && compareDataOrNot(it.label, interaction)
                        && it.label.prevWindow == windowStack.peek()}
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
                    addImplicitAbstractInteraction(prevAbstractState,currentAbstractState,lastExecutedInteraction!!,windowStack.peek())
                }
            }
            else
            {
                //No recored abstract interaction before
                //Or the abstractInteraction is implicit
                //Record new AbstractInteraction

                lastExecutedAction = AbstractAction(
                            actionName = interaction.actionType,
                            extra = interaction.data
                    )

                val newAbstractInteraction = AbstractInteraction(
                        abstractAction = lastExecutedAction!!,
                        interactions = ArrayList(),
                        isImplicit = false,
                        prevWindow = windowStack.peek(),
                        data = interaction.data)
                newAbstractInteraction.interactions.add(interaction)
                abstractTransitionGraph.add(prevAbstractState,currentAbstractState,newAbstractInteraction)
                lastExecutedInteraction = newAbstractInteraction
                addImplicitAbstractInteraction(prevAbstractState,currentAbstractState,lastExecutedInteraction!!,windowStack.peek())
            }
        }
        else
        {
            val widgetGroup = prevAbstractState.getWidgetGroup(interaction.targetWidget!!, prevState)
            assert(widgetGroup!=null, {"cannot get widgetgroup from ${interaction.targetWidget.toString()}"})
            if (widgetGroup!=null)
            {
                widgetGroup.exerciseCount+=1
                AbstractStateManager.instance.ABSTRACT_STATES.filter{ it.window == prevAbstractState.window }. filter { it.widgets.contains(widgetGroup!!) }.forEach {
                    val sameWidgetGroup = it.widgets.find { it == widgetGroup }!!
                    sameWidgetGroup.exerciseCount++
                }

                val allAbstractTransitions = abstractTransitionGraph.edges(prevAbstractState)
                val abstractTransitions = allAbstractTransitions.filter {
                    it.label.abstractAction.actionName == interaction.actionType
                        && it.label.abstractAction.widgetGroup?.equals(widgetGroup)?:false
                            && compareDataOrNot(it.label, interaction)
                            && it.label.prevWindow == windowStack.peek()}
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
                        addImplicitAbstractInteraction(prevAbstractState,currentAbstractState,lastExecutedInteraction!!,windowStack.peek())
                    }
                }
                else
                {
                    //No recored abstract interaction before
                    //Or the abstractInteraction is implicit
                    //Record new AbstractInteraction

                    lastExecutedAction = AbstractAction(
                            actionName = interaction.actionType,
                            widgetGroup = widgetGroup,
                            extra = interaction.data
                    )


                    val newAbstractInteraction = AbstractInteraction(
                            abstractAction = lastExecutedAction!!,
                            interactions = ArrayList(),
                            isImplicit = false,
                            prevWindow = windowStack.peek(),
                            data = interaction.data)
                    newAbstractInteraction.interactions.add(interaction)
                    abstractTransitionGraph.add(prevAbstractState,currentAbstractState,newAbstractInteraction)
                    lastExecutedInteraction = newAbstractInteraction
                    addImplicitAbstractInteraction(prevAbstractState,currentAbstractState,lastExecutedInteraction!!,windowStack.peek())
                }
            }
        }
        if (lastExecutedInteraction ==null) {
            log.info("Not processed interaction: ${interaction.toString()}")
            return
        }
        log.info("Computing Abstract Interaction. - DONE")
        if (interaction.targetWidget==null || interaction.actionType.isTextInsert())
            return
        log.info("Refining Abstract Interaction.")
        AbstractStateManager.instance.refineModel(interaction, prevState,lastExecutedInteraction!!)
        log.info("Refining Abstract Interaction. - DONE")
    }

    private fun normalizeActionType(actionType: String): String {
        return when (actionType) {
            "ClickEvent" -> "Click"
            "LongClickEvent" -> "Click"
            else -> actionType
        }
    }

    private fun compareDataOrNot(abstractInteraction: AbstractInteraction, interaction: Interaction<Widget>): Boolean {
        if (interaction.actionType == "CallIntent" || interaction.actionType == "Swipe") {
            return abstractInteraction.data == interaction.data
        }
        return true
    }

    private fun addImplicitAbstractInteraction(prevAbstractState: AbstractState, currentAbstractState: AbstractState, abstractInteraction: AbstractInteraction, prevprevWindow: WTGNode) {
        log.debug("Add implicit abstract interaction")
        var addedCount = 0
        val otherSameStaticNodeAbStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == prevAbstractState.window
                && it != prevAbstractState && it.isOpeningKeyboard == prevAbstractState.isOpeningKeyboard}
        var processedStateCount = 0
        otherSameStaticNodeAbStates.forEach {
            processedStateCount+=1
            val abstractEdge = abstractTransitionGraph.edges(it).filter { it.label.abstractAction == abstractInteraction.abstractAction
                    && it.label.prevWindow == prevprevWindow}
            if (abstractEdge.isEmpty())
            {
                if (abstractInteraction.abstractAction.widgetGroup==null || it is VirtualAbstractState)
                {
                    val implicitAbstractInteraction = AbstractInteraction(
                            abstractAction = abstractInteraction.abstractAction,
                            isImplicit = true,
                            prevWindow = prevprevWindow
                    )
                    abstractTransitionGraph.add(it,currentAbstractState,implicitAbstractInteraction)
                    addedCount+=1
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
                                    && it.label.prevWindow == prevprevWindow
                                    && it.label.abstractAction.widgetGroup == widgetGroup}
                        if (widgetAbstractEdge.isEmpty())
                        {
                            val implicitAbstractInteraction = AbstractInteraction(
                                    abstractAction = AbstractAction(
                                            actionName = abstractInteraction.abstractAction.actionName,
                                            widgetGroup = widgetGroup),
                                    isImplicit = true,
                                    prevWindow = prevprevWindow
                            )
                            abstractTransitionGraph.add(it,currentAbstractState,implicitAbstractInteraction)
                            addedCount+=1
                        }
                    } else {
                        //TODO create new WidgetGroup
                    }
                }
            }
        }
        log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")
        log.debug("Add implicit back interaction.")
        // add implicit back events
        addedCount = 0
        processedStateCount = 0
        val implicitBackWindow = if (currentAbstractState.isOpeningKeyboard) {
            currentAbstractState.window
        } else if (prevAbstractState.window == currentAbstractState.window) {
            prevprevWindow
        } else{
            prevprevWindow
        }

        val abstractAction = AbstractAction(actionName = ActionType.PressBack.name,
                widgetGroup = null)
        val processingAbstractStates = ArrayList<AbstractState>()
        val similarCurrentAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == currentAbstractState.window
                && it.isOpeningKeyboard == currentAbstractState.isOpeningKeyboard}
        // if current abstract state have a pressback to a different window, don't add implicit back event
        similarCurrentAbstractStates.forEach { abstractState ->
            val exisitingExplicitBackEvents = abstractTransitionGraph.edges(abstractState).filter {
                        it.label.abstractAction == abstractAction
                        && it.label.prevWindow == implicitBackWindow
                        && !it.label.isImplicit
            }
            if (exisitingExplicitBackEvents.isEmpty()) {
                processingAbstractStates.add(abstractState)
            }

        }

        val backAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter {
            it.window == implicitBackWindow
                    && !it.isOpeningKeyboard
        }

        backAbstractState.forEach { abstractState ->
            processedStateCount += 1

            val implicitAbstractInteraction = AbstractInteraction(
                    abstractAction = abstractAction,
                    isImplicit = true,
                    prevWindow = implicitBackWindow
            )
            processingAbstractStates.forEach {
                abstractTransitionGraph.add(it, abstractState, implicitAbstractInteraction)
                addedCount += 1
            }

        }
        log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")
    }
    //endregion


    fun addDisablePathFromState(transitionPath: TransitionPath, edge: Edge<AbstractState,AbstractInteraction>){
        val abstractInteraction = edge.label
        if (abstractInteraction.abstractAction.actionName == "ItemClick" || abstractInteraction.abstractAction.actionName == "ItemLongClick"
                || abstractInteraction.abstractAction.actionName == "ItemSelected")
        {
            return
        }
        abstractTransitionGraph.remove(edge)
        // find all similar edge
        val similarEdges = abstractTransitionGraph.edges().filter { it.source.data.window == edge.source.data.window
                && it.label.isImplicit && it.label.abstractAction == edge.label.abstractAction
                && it.destination!!.data == edge.destination!!.data
                && edge.label.prevWindow == it.label.prevWindow }
        similarEdges.forEach {
            abstractTransitionGraph.remove(it)
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



    private fun computeAbstractState(newState: State<*>, lastInteractions: List<Interaction<*>>, explorationContext: ExplorationContext<*, *, *>) {
        log.info("Computing Abstract State.")
        var currentActivity: String = ""
        runBlocking {
            currentActivity = getCurrentActivity()
        }
        if (activityAlias.containsKey(currentActivity))
            currentActivity = activityAlias[currentActivity]!!
        if (mainActivity == "") {
            mainActivity = explorationContext.apk.launchableMainActivityName
        }
        stateActivityMapping[newState] = currentActivity
        val isFromLaunchApp = lastInteractions.find { it.actionType.isLaunchApp() }!=null
        val newAbstractState = AbstractStateManager.instance.getOrCreateNewTestState(newState,currentActivity,appName,isFromLaunchApp,currentRotation)
        increaseNodeVisit(abstractState = newAbstractState)
        log.info("Computing Abstract State. - DONE")
    }

    val appStatesMap = HashMap<WTGNode, ArrayList<AbstractState>>()
    private fun saveAppState(newAbstractState: AbstractState ) {
        if (!appStatesMap.containsKey(newAbstractState.window)) {
            appStatesMap.put(newAbstractState.window, ArrayList())
        }
        appStatesMap[newAbstractState.window]!!.add(newAbstractState)
    }


    private fun increaseNodeVisit(abstractState: AbstractState) {
        if (!windowVisitCount.containsKey(abstractState.window))
        {
            windowVisitCount[abstractState.window] = 1

        }
        else
        {
            windowVisitCount[abstractState.window] = windowVisitCount[abstractState.window]!! + 1
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
            it.window == abstractState.window
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
                    sourceWindow = prevAbstractState.window,
                    eventHandlers = ArrayList()
            )
            newStaticEvent.data = abstractInteraction.abstractAction.extra
            newStaticEvent.modifiedMethods.putAll(abstractInteraction.modifiedMethods)
            newStaticEvent.modifiedMethodStatement.putAll(abstractInteraction.modifiedMethodStatement)
            newStaticEvent.eventHandlers.addAll(abstractInteraction.handlers.map { it.key })
            allTargetStaticEvents.add(newStaticEvent)
            transitionGraph.add(prevAbstractState.window,newAbstractState.window,newStaticEvent)
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
                              sourceWindow = prevAbstractState.window,
                              eventHandlers = ArrayList()
                )
                    newStaticEvent.data = abstractInteraction.abstractAction.extra
                    newStaticEvent.modifiedMethods.putAll(abstractInteraction.modifiedMethods)
                    newStaticEvent.modifiedMethodStatement.putAll(abstractInteraction.modifiedMethodStatement)
                    newStaticEvent.eventHandlers.addAll(abstractInteraction.handlers.map { it.key })
                    allTargetStaticEvents.add(newStaticEvent)
                    transitionGraph.add(prevAbstractState.window,newAbstractState.window,newStaticEvent)
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
                        wtgNode = prevAbstractState.window,
                        className = widgetGroup.getLocalAttributes()[AttributeType.className]?:"",
                        contentDesc = widgetGroup.getLocalAttributes()[AttributeType.contentDesc]?:"",
                        text = widgetGroup.getLocalAttributes()[AttributeType.text]?:""
                )
                val newStaticEvent = StaticEvent(
                        eventType = eventType,
                        widget = staticWidget,
                        activity = prevAbstractState.activity,
                        sourceWindow = prevAbstractState.window,
                        eventHandlers = ArrayList()
                )
                newStaticEvent.modifiedMethods.putAll(abstractInteraction.modifiedMethods)
                newStaticEvent.modifiedMethodStatement.putAll(abstractInteraction.modifiedMethodStatement)
                newStaticEvent.eventHandlers.addAll(abstractInteraction.handlers.map { it.key })
                allTargetStaticEvents.add(newStaticEvent)
                transitionGraph.add(prevAbstractState.window,newAbstractState.window,newStaticEvent)
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

//                        log.info("New modified method covered:")
//                        log.info(statementMF!!.getMethodName(it))
                    }
                } else {
                    if (statementMF!!.isModifiedMethod(it)) {
                        abstractInteraction.modifiedMethods.put(it, true)
                        if (!allTargetStaticEvents.contains(event) && event !=null) {
                            allTargetStaticEvents.add(event)
                        }
//                        log.info("New modified method covered:")
//                        log.info(statementMF!!.getMethodName(it))
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
                }

                if (untriggeredTargetHandlers.contains(it)) {
                    untriggeredTargetHandlers.remove(it)
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

/*    private fun getCurrentEventCoverage() {
        val triggeredEventCount = allTargetStaticEvents.size - untriggeredTargetEvents.size
        // log.debug("Current target widget coverage: ${triggeredWidgets}/${allTargetStaticWidgets.size}=${triggeredWidgets / allTargetStaticWidgets.size.toDouble()}")
        log.debug("Current target event coverage: $triggeredEventCount/${allTargetStaticEvents.size} = ${triggeredEventCount/allTargetStaticEvents.size.toDouble()}")
    }*/

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
        if (e.eventType == EventType.implicit_rotate_event && !appRotationSupport) {
            if (allTargetStaticEvents.contains(e)) {
                allTargetStaticEvents.remove(e)
            }
            return emptyList()
        }
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

    fun getRuntimeWidgets(widgetGroup: WidgetGroup, widgetAbstractState: AbstractState, currentState: State<*>): List<Widget> {
        val allGUIWidgets = widgetGroup.getGUIWidgets(currentState)
        if (allGUIWidgets.isEmpty()) {
            //try get the same static widget
            val abstractState = getAbstractState(currentState)
            if (widgetAbstractState.staticWidgetMapping.containsKey(widgetGroup) && abstractState!=null) {
                val staticWidgets = widgetAbstractState.staticWidgetMapping[widgetGroup]!!
                val similarWidgetGroups = abstractState.staticWidgetMapping.filter { it.value.intersect(staticWidgets).isNotEmpty() }.map { it.key }
                return similarWidgetGroups.flatMap { it.guiWidgets}.filter { currentState.widgets.contains(it) }
            }
        }
        return allGUIWidgets
    }

    //endregion
    init {

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
            readActivityAlias(jObj)
            readWindowWidgets(jObj)
            readMenuItemTexts(jObj)
            readActivityDialogs(jObj)
            readWindowHandlers(jObj)
            log.debug("Reading modified method invocation")
            readModifiedMethodTopCallers(jObj)
            readModifiedMethodInvocation(jObj)
            readUnreachableModfiedMethods(jObj)
            log.debug("Reading all strings")
            //readAllStrings(jObj)
            readEventCorrelation(jObj)
            readMethodDependency(jObj)
            readWindowDependency(jObj)
        }
        readIntentModel()
    }

    val methodTermsHashMap = HashMap<String, HashMap<String, Long>>()
    val windowTermsHashMap = HashMap<WTGNode, HashMap<String,Long>>()
    val windowHandlersHashMap = HashMap<WTGNode, Set<String>>()
    val activityAlias = HashMap<String, String> ()

    private fun readActivityAlias (jObj: JSONObject) {
        val jsonActivityAlias = jObj.getJSONObject("activityAlias")
        if (jsonActivityAlias != null) {
            activityAlias.putAll(StaticAnalysisJSONFileHelper.readActivityAlias(jsonActivityAlias,this))
        }
    }

    private fun readWindowDependency(jObj: JSONObject) {
        val jsonWindowTerm = jObj.getJSONObject("windowsDependency")
        if (jsonWindowTerm != null)
        {
            windowTermsHashMap.putAll(StaticAnalysisJSONFileHelper.readWindowTerms(jsonWindowTerm, transitionGraph))
        }
    }

    private fun readWindowHandlers (jObj: JSONObject) {
        val jsonWindowHandlers = jObj.getJSONObject("windowHandlers")
        if (jsonWindowHandlers != null) {
            windowHandlersHashMap.putAll(StaticAnalysisJSONFileHelper.readWindowHandlers(jsonWindowHandlers,transitionGraph, statementMF!!))
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
                    for (meaningNode in WTGNode.allMeaningNodes) {
                        val intentEvent = StaticEvent(activity = qualifiedActivityName, eventType = EventType.callIntent,
                                eventHandlers = ArrayList(), widget = null,sourceWindow = meaningNode)
                        intentEvent.data = it
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

/*    private fun readAllStrings(jObj: JSONObject) {
        var jMap = jObj.getJSONArray("allStrings")
        StaticAnalysisJSONFileHelper.readAllStrings(jMap,generalDictionary)
    }*/

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

        //Add windows containing top caller to allTargetWindows list
        modifiedMethodTopCallersMap.forEach { _, topCallers ->
            allTargetHandlers.addAll(topCallers)
            topCallers.forEach { caller ->
                val windows = windowHandlersHashMap.filter { it.value.contains(caller) }.map { it.key }
                windows.forEach {
                    if (!allTargetWindows.contains(it)) {
                        allTargetWindows.add(it)
                    }
                }
            }
        }

        untriggeredTargetHandlers.addAll(allTargetHandlers)
    }

    private fun readModifiedMethodInvocation(jObj: JSONObject) {
        var jMap = jObj.getJSONObject("modiMethodInvocation")
        StaticAnalysisJSONFileHelper.readModifiedMethodInvocation(jsonObj = jMap,
                transitionGraph = transitionGraph,
                allTargetStaticEvents = allTargetStaticEvents,
                allTargetStaticWidgets = allTargetStaticWidgets,
                statementCoverageMF = statementMF!!)
        allTargetStaticEvents.forEach {
            val sourceWindow = it.sourceWindow
            if (!allTargetWindows.contains(sourceWindow))
                allTargetWindows.add(sourceWindow)
        }
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
        sb.appendln("Statements;${statementMF!!.statementInstrumentationMap.size}")
        sb.appendln("Methods;${statementMF!!.methodInstrumentationMap.size}")
        sb.appendln("ModifiedMethods;${statementMF!!.modMethodInstrumentationMap.size}")
        sb.appendln("ModifiedMethodsStatements;${
        statementMF!!.methodStatementInstrumentationMap.filter { statementMF!!.modMethodInstrumentationMap.contains(it.value) }.size
        } ")
        sb.appendln("CoveredStatements;${statementMF!!.executedStatementsMap.size}")
        sb.appendln("CoveredMethods;${statementMF!!.executedMethodsMap.size}")
        sb.appendln("CoveredModifiedMethods;${statementMF!!.executedModifiedMethodsMap.size}")
        val executedModifiedMethodStatement = statementMF!!.executedStatementsMap.filter { statementMF!!.modMethodInstrumentationMap.contains(statementMF!!.methodStatementInstrumentationMap[it.key]) }
        sb.appendln("CoveredModifiedMethodsStatements;${statementMF!!.executedModifiedMethodStatementsMap.size}")
        sb.appendln("ListCoveredModifiedMethods;")
        if (statementMF!!.executedModifiedMethodsMap.isNotEmpty()) {
            val sortedMethods = statementMF!!.executedModifiedMethodsMap.entries
                    .sortedBy { it.value }
            val initialDate = sortedMethods.first().value
            sortedMethods
                    .forEach {
                        sb.appendln("${it.key};${statementMF!!.modMethodInstrumentationMap[it.key]};${Duration.between(initialDate.toInstant(), it.value.toInstant()).toMillis() / 1000}")
                    }

        }
        sb.appendln("EndOfList")
        sb.appendln("ListUnCoveredModifiedMethods;")
        statementMF!!.modMethodInstrumentationMap.filterNot {statementMF!!.executedModifiedMethodsMap.containsKey(it.key) }.forEach{
            sb.appendln("${it.key};${it.value}")
        }
        sb.appendln("EndOfList")
        sb.appendln("Phase1StatementMethodCoverage;$stage1StatementCoverage")
        sb.appendln("Phase1MethodCoverage;$stage1MethodCoverage")
        sb.appendln("Phase1ModifiedStatementCoverage;$stage1ModifiedStatementCoverage")
        sb.appendln("Phase1ModifiedMethodCoverage;$stage1ModifiedMethodCoverage")
        sb.appendln("Phase1ActionCount;$stage1Actions")
        sb.appendln("Phase2StatementMethodCoverage;$stage2StatementCoverage")
        sb.appendln("Phase2MethodCoverage;$stage2MethodCoverage")
        sb.appendln("Phase2ModifiedMethodCoverage;$stage2ModifiedCoverage")
        sb.appendln("Phase2ModifiedStatementCoverage;$stage2ModifiedStatementCoverage")
        sb.appendln("Phase2ActionCount;$stage2Actions")
        sb.appendln("StrategyStatistics;")
        sb.appendln("ExerciseTargetComponent;${ExerciseTargetComponentTask.executedCount}")
        sb.appendln("GoToTargetNode;${GoToTargetWindowTask.executedCount}")
        sb.appendln("GoToAnotherNode;${GoToAnotherWindow.executedCount}")
        sb.appendln("RandomExploration;${RandomExplorationTask.executedCount}")
        var totalEvents = allTargetStaticEvents.size
        /*sb.appendln("TotalTargetEvents;$totalEvents")
        sb.appendln("TriggeredEvents;")
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
        sb.appendln("MissEvents;${totalEvents - triggeredEvents.size}")
        untriggeredTargetEvents
                .forEach {
                    if (it.widget!=null){
                        sb.append("In ${it.activity}:Widget ${it.widget.className}:${it.widget.resourceIdName}:${it.eventType}\n")
                    }
                    else
                    {
                        sb.append("Widget null:${it.eventType}\n")
                    }
                }*/
        sb.appendln("Unreached node;")
        WTGNode.allNodes.filterNot {
            it is WTGAppStateNode || it is WTGLauncherNode
                || it is WTGOutScopeNode || it is WTGFakeNode
        }. filter { node -> AbstractStateManager.instance.ABSTRACT_STATES.find { it.window == node } == null} .forEach {
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
        sb.appendln("NumberOfAppStates;$numberOfAppStates")
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
        val interactiveWidgetGroups = abstractState.widgets.filter {it.attributePath.isClickable() || it.attributePath.isLongClickable()
                || it.attributePath.isScrollable()}
                .filterNot{ it.attributePath.isCheckable() }
        if (interactiveWidgetGroups.isNotEmpty())
        {
            /*val interestingWidgets = abstractTransitionGraph.edges()
                    .filter {
                            it.destination?.data !is VirtualAbstractState
                                    && it.source.data.window == abstractState.window
                                    && interactiveWidgetGroups.contains(it.label.abstractAction.widgetGroup)
                                    && it.label.abstractAction.actionName.isClick()
                    }.groupBy { it.label }.filter { it.value.size > 1 }.map { it.key.abstractAction.widgetGroup!! }
            if (interestingWidgets.isNotEmpty()) {
                //try this first
                if (Random.nextBoolean()) {
                    val result = HashMap<Widget, WidgetGroup>()
                    interestingWidgets.forEach {
                        val widgets = it.getGUIWidgets(currentState)
                        widgets.forEach { w ->
                            result.put(w, it)
                        }
                    }
                    return result

                }
            }*/
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



    fun accumulateEventsDependency(): HashMap<StaticEvent, HashMap<String, Long>> {
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

    fun updateStage1Info(eContext: ExplorationContext<*,*,*>) {
        stage1ModifiedMethodCoverage = statementMF!!.getCurrentModifiedMethodCoverage()
        stage1StatementCoverage = statementMF!!.getCurrentCoverage()
        stage1MethodCoverage = statementMF!!.getCurrentMethodCoverage()
        stage1ModifiedStatementCoverage = statementMF!!.getCurrentModifiedMethodStatementCoverage()
        stage1Actions = eContext.explorationTrace.getActions().size
    }

    fun updateStage2Info(eContext: ExplorationContext<*,*,*>) {
        stage2ModifiedCoverage = statementMF!!.getCurrentModifiedMethodCoverage()
        stage2StatementCoverage = statementMF!!.getCurrentCoverage()
        stage2MethodCoverage = statementMF!!.getCurrentMethodCoverage()
        stage2ModifiedStatementCoverage = statementMF!!.getCurrentModifiedMethodStatementCoverage()
        stage2Actions = eContext.explorationTrace.getActions().size
    }

    companion object {

        @JvmStatic
        val log: Logger by lazy { LoggerFactory.getLogger(RegressionTestingMF::class.java) }
        object RegressionStrategy: PropertyGroup() {
            val use by booleanType
            val budgetScale by doubleType
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

enum class Rotation {
    LANDSCAPE,
    PORTRAIT
}