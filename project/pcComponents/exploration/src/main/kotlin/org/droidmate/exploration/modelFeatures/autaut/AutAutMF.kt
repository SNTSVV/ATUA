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
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.InputConfiguration
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.InputConfigurationFileHelper
import org.droidmate.exploration.modelFeatures.autaut.DSTG.*
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.deviceEnvironment.DeviceEnvironmentConfiguration
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.deviceEnvironment.DeviceEnvironmentConfigurationFileHelper
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.intent.IntentFilter
import org.droidmate.exploration.modelFeatures.autaut.WTG.EventType
import org.droidmate.exploration.modelFeatures.autaut.WTG.Input
import org.droidmate.exploration.modelFeatures.autaut.WTG.StaticWidget
import org.droidmate.exploration.modelFeatures.autaut.WTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Activity
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.FakeWindow
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.TextInput
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.emptyUUID
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
import kotlin.collections.HashSet
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

class AutAutMF(private val appName: String,
               private val resourceDir: Path,
               private val manualInput: Boolean,
               private val manualIntent: Boolean,
               private val getCurrentActivity: suspend () -> String,
               private val getDeviceRotation: suspend () -> Int,
               private val getDeviceScreenSurface: suspend () -> Rectangle) : ModelFeature() {
    val packageName = appName
    var portraitScreenSurface = Rectangle.empty()
    var portraitVisibleScreenSurface = Rectangle.empty()
    var landscapeScreenSurface = Rectangle.empty()
    var landscapeVisibleScreenSurface = Rectangle.empty()
    val textFilledValues = ArrayList<String>()
    private val targetWidgetFileName = "autaut-report.txt"
    override val coroutineContext: CoroutineContext = CoroutineName("RegressionTestingModelFeature") + Job()
    var statementMF: StatementCoverageMF?=null
    var crashlist: CrashListMF?=null
    var wtg: WindowTransitionGraph = WindowTransitionGraph()
    lateinit var abstractTransitionGraph: AbstractTransitionGraph
    var stateGraph: StateGraphMF? = null
    private val abandonnedWTGNodes = arrayListOf<Window>()

    val interestingInteraction = HashMap<State<*>, ArrayList<Interaction<*>>>()
    val blackListWidgets = HashMap<AbstractState, Widget>()


    var isRecentItemAction: Boolean = false
    var isRecentPressMenu: Boolean = false

    var currentRotation: Rotation = Rotation.PORTRAIT
    var phase: Int = 1
    private val widgetProbability = mutableMapOf<UUID, Double>() // probability of each widget invoking modified methods
    private val runtimeWidgetInfos = mutableMapOf<Pair<Window,UUID>, Triple<State<*>, StaticWidget,HashMap<String, Any>>>()//Key: widget id
    private val widgets_modMethodInvocation = mutableMapOf<String, Widget_MethodInvocations>()
    private val allDialogOwners = mutableMapOf<String, ArrayList<String>>() // window -> listof (Dialog)
    private val allMeaningfulWidgets = hashSetOf<StaticWidget>() //widgetId -> idWidget
    val allTargetStaticWidgets = hashSetOf<StaticWidget>() //widgetId -> idWidget
    val allTargetStaticEvents = hashSetOf<Input>()
    val allTargetWindow_ModifiedMethods = hashMapOf<Window, HashSet<String>>()
    val allTargetHandlers = hashSetOf<String>()
    val allEventHandlers = hashSetOf<String>()
    val allModifiedMethod = hashMapOf<String,Boolean>()

    private val allActivityOptionMenuItems = mutableMapOf<String,ArrayList<StaticWidget> >()  //idWidget
    private val allContextMenuItems = arrayListOf<StaticWidget>()
    private val activityTransitionWidget = mutableMapOf<String, ArrayList<StaticWidget>>() // window -> Listof<StaticWidget>
    private val activity_TargetComponent_Map = mutableMapOf<String, ArrayList<Input>>() // window -> Listof<StaticWidget>

    val targetItemEvents = HashMap<Input, HashMap<String,Int>>()
    var isAlreadyRegisteringEvent = false
    private val stateActivityMapping = mutableMapOf<State<*>,String>()

    private val child_parentTargetWidgetMapping = mutableMapOf<Pair<Window,UUID>, Pair<Window,UUID>>() // child_widget.uid -> parent_widget.uid
    private val dateFormater = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    var lastExecutedTransition: AbstractTransition? = null
    private var lastChildExecutedEvent: AbstractTransition? = null
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
    var internetStatus = true
    val abstractStateVisitCount = HashMap<AbstractState, Int>()
    val windowVisitCount = HashMap<Window, Int>()
    var appPrevState: State<*>? =null
    var windowStack: Stack<Window> = Stack<Window>()
    val stateList: ArrayList<State<*>> = ArrayList()
    val guiState_AbstractStateMap = HashMap<State<*>,AbstractState>()

    var prevWindowState: State<*>? = null

    var lastOpeningAnotherAppInteraction: Interaction<Widget>? = null

    var updateMethodCovFromLastChangeCount: Int = 0
    var updateStmtCovFromLastChangeCount: Int = 0
    var methodCovFromLastChangeCount: Int = 0
    var stmtCovFromLastChangeCount: Int = 0
    var lastUpdatedMethodCoverage: Double = 0.0
    var lastMethodCoverage: Double = 0.0
    var lastUpdatedStatementCoverage: Double = 0.0



    val unreachableModifiedMethods = ArrayList<String>()

    val intentFilters = HashMap<String, ArrayList<IntentFilter>>()
    val targetIntFilters = HashMap<IntentFilter,Int>()
    var inputConfiguration: InputConfiguration?=null
    var deviceEnvironmentConfiguration: DeviceEnvironmentConfiguration? = null

    val staticEventWindowCorrelation = HashMap<Input, HashMap<Window,Double>>()
    val untriggeredTargetHandlers = hashSetOf<String>()


    var phase1MethodCoverage: Double = 0.0
    var phase2MethodCoverage: Double = 0.0
    var phase1ModifiedMethodCoverage: Double = 0.0
    var phase2ModifiedCoverage: Double = 0.0
    var phase1StatementCoverage: Double = 0.0
    var phase2StatementCoverage: Double =0.0
    var phase1ModifiedStatementCoverage: Double = 0.0
    var phase2ModifiedStatementCoverage: Double =0.0
    var phase1Actions: Int = 0
    var phase2Actions: Int = 0
    var phase3Actions: Int = 0
    var phase2StartTime: String = ""
    var phase3StartTime: String = ""

    fun setPhase2StartTime() {
        phase2StartTime = dateFormater.format(System.currentTimeMillis())
    }
    fun setPhase3StartTime() {
        phase3StartTime = dateFormater.format(System.currentTimeMillis())
    }
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
    var firstRun = true

    //region Model feature override
    override suspend fun onAppExplorationFinished(context: ExplorationContext<*,*,*>) {
        this.join()
        produceTargetWidgetReport(context)
        AutAutModelOutput.dumpModel(context.model.config,this)
    }

    override fun onAppExplorationStarted(context: ExplorationContext<*, *, *>) {
        this.trace = context.explorationTrace
        this.stateGraph = context.getOrCreateWatcher<StateGraphMF>()
        this.statementMF = context.getOrCreateWatcher<StatementCoverageMF>()
        this.crashlist = context.getOrCreateWatcher<CrashListMF>()
        readAppModel()
        AbstractStateManager.instance.init(this, appName)
        //AutAutModelLoader.loadModel(resourceDir.resolve(appName),this)
        //val dstgModelFolderPath = resourceDir.resolve(appName)
        //val dstgModelParser = DSTGModelParser(dstgModelFolderPath,this)
        //dstgModelParser.loadModel()
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
                val lastLaunchAction = context.explorationTrace.getActions().last { it.actionType.isLaunchApp() || it.actionType == "ResetApp" }
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
                interactions.add(context.getLastAction())
            }
            if (interactions.any { it.actionType.isLaunchApp() || it.actionType == "ResetApp" }) {
                fromLaunch = true
                windowStack.clear()
                windowStack.push(Launcher.getOrCreateNode())
            } else {
                fromLaunch = false
            }
            isModelUpdated = false
            val prevState = context.getState(context.getLastAction().prevState) ?: context.model.emptyState
            val newState = context.getCurrentState()
            if (prevState == context.model.emptyState) {
                if (windowStack.isEmpty()) {
                    windowStack.push(Launcher.getOrCreateNode())
                }
            } else {
                //check this state outside of application
                appPrevState = prevState
                if (prevState.isHomeScreen )
                {
                    if (retrieveScreenDimension(prevState)) {
                        AbstractStateManager.instance.ABSTRACT_STATES.removeIf { it !is VirtualAbstractState }
                    }
                }
                if (!prevState.isHomeScreen && prevState.widgets.find { it.packageName == appName } != null)
                {
                    //update previous actions
                    lastExecutedTransition = null

                    //getCurrentEventCoverage()
                    //val currentCov = statementMF!!.getCurrentCoverage()
                    val currentCov = statementMF!!.getCurrentMethodCoverage()
                    if (currentCov > lastMethodCoverage)
                    {
                        methodCovFromLastChangeCount = 0
                        lastMethodCoverage = currentCov
                    }
                    else
                    {
                        methodCovFromLastChangeCount += 1
                    }
                    //val currentModifiedMethodStmtCov = statementMF!!.getCurrentModifiedMethodStatementCoverage()
                    val currentUpdatedMethodCov = statementMF!!.getCurrentModifiedMethodCoverage()
                    if (currentUpdatedMethodCov > lastUpdatedMethodCoverage)
                    {
                        updateMethodCovFromLastChangeCount = 0
                        lastUpdatedMethodCoverage = currentUpdatedMethodCov
                    }
                    else
                    {
                        updateMethodCovFromLastChangeCount += 1
                    }
                    val currentUpdatedStmtCov = statementMF!!.getCurrentModifiedMethodStatementCoverage()
                    if (currentUpdatedStmtCov > lastUpdatedStatementCoverage)
                    {
                        updateStmtCovFromLastChangeCount = 0
                        lastUpdatedStatementCoverage = currentUpdatedStmtCov
                    }
                    else
                    {
                        updateStmtCovFromLastChangeCount += 1
                    }
                }
            }
            if (windowStack.isEmpty()) {
                windowStack.push(Launcher.getOrCreateNode())
            }
            if (newState != context.model.emptyState) {
                if (newState.isAppHasStoppedDialogBox) {
                    log.debug("Encountering Crash state.")
                }
                if (newState.isHomeScreen ) {

                    if (retrieveScreenDimension(newState)) {
                        AbstractStateManager.instance.ABSTRACT_STATES.removeIf { it !is VirtualAbstractState }
                    }
                }
                currentRotation = computeRotation()


                updateAppModel(prevState, newState, interactions,context)

                //validateModel(newState)
            }

        } finally {
                mutex.unlock()
        }
    }

    private fun validateModel(currentState: State<*>) {
        val currentAbstractState = getAbstractState(currentState)!!
        val runtimeAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
                .filter {
                    it !is VirtualAbstractState
                            && it != currentAbstractState
                }
        var pathStatus = HashMap<AbstractState,Boolean>()

        runtimeAbstractStates.forEach { dest ->
            val paths = ArrayList<TransitionPath>()
            PathFindingHelper.findPathToTargetComponent(
                    autautMF = this,
                    currentState = currentState,
                    root = currentAbstractState,
                    includingWTG = false,
                    stopWhenHavingUnexercisedAction = false,
                    allPaths = paths,
                    finalTarget = dest,
                    includeBackEvent = true,
                    includeReset = true,
                    pathCountLimitation = 10,
                    shortest = true,
                    traversingNodes = listOf(Pair(windowStack.clone() as Stack<Window>, currentAbstractState))
            )
            if (paths.size > 0)
                pathStatus.put(dest,true)
            else
                pathStatus.put(dest,false)
        }
        if (pathStatus.any { it.value == false }) {
            log.debug("Unreachable abstract states.")
            pathStatus.filter { it.value == false }.forEach { abstrateState, _ ->
                val inEdges = abstractTransitionGraph.edges().filter {
                    it.destination?.data == abstrateState
                            && it.source != it.destination
                            && it.source.data !is VirtualAbstractState
                }
                log.debug("${inEdges.size} go to $abstrateState")
            }
        }
    }

    private fun retrieveScreenDimension(state: State<*>): Boolean {
        //get fullscreen app resolution
        val rotation = computeRotation()
        if (rotation == Rotation.PORTRAIT && portraitScreenSurface == Rectangle.empty()) {
            val fullDimension = Helper.computeGuiTreeDimension(state)
            val fullVisbleDimension = Helper.computeGuiTreeVisibleDimension(state)
            portraitScreenSurface = fullDimension
            portraitVisibleScreenSurface = fullVisbleDimension
            landscapeScreenSurface = Rectangle.create(fullDimension.topY, fullDimension.leftX, fullDimension.bottomY, fullDimension.rightX)
            landscapeVisibleScreenSurface = Rectangle.create(fullVisbleDimension.topY, fullVisbleDimension.leftX, fullVisbleDimension.bottomY, fullVisbleDimension.rightX)
            log.debug("Screen resolution: $portraitScreenSurface")
            return true
        } else if (rotation == Rotation.LANDSCAPE && landscapeScreenSurface == Rectangle.empty()) {
            val fullDimension = Helper.computeGuiTreeDimension(state)
            val fullVisbleDimension = Helper.computeGuiTreeVisibleDimension(state)
            landscapeScreenSurface = fullDimension
            landscapeVisibleScreenSurface = fullVisbleDimension
            portraitScreenSurface = Rectangle.create(fullDimension.topY, fullDimension.leftX, fullDimension.bottomY, fullDimension.rightX)
            portraitVisibleScreenSurface = Rectangle.create(fullVisbleDimension.topY, fullVisbleDimension.leftX, fullVisbleDimension.bottomY, fullVisbleDimension.rightX)
            log.debug("Screen resolution: $portraitScreenSurface")
            return true
        }
        return false
    }

    private fun updateWindowStack(prevAbstractState: AbstractState?, currentAbstractState: AbstractState, isLaunch: Boolean) {
        if (isLaunch) {
            windowStack.clear()
            windowStack.push(Launcher.getOrCreateNode())
            if (prevAbstractState!=null && !prevAbstractState.isHomeScreen) {
                val homeScreenState = stateList.findLast { it.isHomeScreen }
                if (homeScreenState != null) {
                    stateList.add(homeScreenState)
                }
            }
            return
        }
        if (currentAbstractState.window !is OutOfApp) {
            if (prevAbstractState != null) {

                if (windowStack.contains(currentAbstractState.window) && windowStack.size > 1) {
                    // Return to the prev window
                    // Pop the window
                    while (windowStack.pop() != currentAbstractState.window) {
                    }
                } else {
                    if (currentAbstractState.window is Launcher) {
                        windowStack.clear()
                        windowStack.push(Launcher.getOrCreateNode())

                    }
                    else if (currentAbstractState.window != prevAbstractState.window) {
                        necessaryCheckModel = true
                        if (prevAbstractState.window is Activity) {
                            windowStack.push(prevAbstractState.window)
                        }
                    } else if (currentAbstractState.isOpeningKeyboard) {
                        windowStack.push(currentAbstractState.window)
                    }
                }
            }
        }
        if (windowStack.isEmpty()) {
            windowStack.push(Launcher.getOrCreateNode())
            return
        }
        necessaryCheckModel = true
    }

    private fun computeRotation(): Rotation {
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

    private fun computeAbstractInteraction(interactions: ArrayList<Interaction<Widget>>, prevState: State<*>, currentState: State<*>,  @Suppress prevprevWindow: Window) {
        log.info("Computing Abstract Interaction.")
        if (interactions.isEmpty())
            return
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(prevState)
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)!!
        if (prevAbstractState == null)
            return
        if (interactions.size == 1) {
            val interaction = interactions.first()
            computeSingleInteraction(prevAbstractState, interaction, currentAbstractState, prevState, currentState)
            if (lastExecutedTransition == null) {
                log.debug("Last executed Interaction is null")
            }
            else if (necessaryCheckModel) {
                log.info("Refining Abstract Interaction.")
                prevAbstractStateRefinement = AbstractStateManager.instance.refineModel(interaction, prevState, lastExecutedTransition!!)
                log.info("Refining Abstract Interaction. - DONE")
            } else {
                log.debug("Return to a previous state. Do not need refine model.")
            }
        } else {
            val actionType = AbstractActionType.ACTION_QUEUE
            val data = interactions
            val abstractAction = AbstractAction(
                    actionType = actionType,
                    attributeValuationSet = null,
                    extra = interactions
            )
            val abstractInteraction = AbstractTransition(
                    abstractAction = abstractAction,
                    interactions = ArrayList(),
                    isImplicit = false,
                    prevWindow = windowStack.peek(),
                    data = data,
                    source = prevAbstractState,
                    dest = currentAbstractState)
            abstractTransitionGraph.add(prevAbstractState, currentAbstractState, abstractInteraction)
            lastExecutedTransition = abstractInteraction
        }

        if (lastExecutedTransition ==null) {
            log.info("Not processed interaction: ${interactions.toString()}")
            return
        }

        log.info("Computing Abstract Interaction. - DONE")

    }

    private fun computeSingleInteraction(prevAbstractState: AbstractState, interaction: Interaction<Widget>, currentAbstractState: AbstractState, prevState: State<*>, currentState: State<*>) {
        if (!prevAbstractState.guiStates.any { it.stateId == interaction.prevState }) {
            log.debug("Prev Abstract State does not contain interaction's prev state.")
            log.debug("Abstract state: " + prevAbstractState)
            log.debug("Gui state: " + interaction.prevState)
        }
        if (!currentAbstractState.guiStates.any { it.stateId == interaction.resState }) {
            log.debug("Current Abstract State does not contain interaction' res state.")
            log.debug("Abstract state: " + currentAbstractState)
            log.debug("Gui state: " + interaction.resState)
        }
       /* val guiEdge = stateGraph!!.edge(prevState, currentState, interaction)
        if (guiEdge == null) {
            log.debug("GTG does not contain any transition from $prevState to $currentState via $interaction")
            stateGraph!!.add(prevState, currentState, interaction)
        }*/
        /*if (interaction.actionType == "RotateUI") {
            val prevStateRotation = pre
            if (prevStateRotation == currentRotation) {
                appRotationSupport = false
            }
        }*/
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
        var actionType: AbstractActionType = normalizeActionType(interaction.actionType)
        val actionData = AbstractAction.computeAbstractActionExtraData(actionType, interaction)
        if (actionType == AbstractActionType.CLICK && interaction.targetWidget == null) {
            val guiDimension = Helper.computeGuiTreeDimension(prevState)
            val clickCoordination = Helper.parseCoordinationData(interaction.data)
            if (clickCoordination.first<guiDimension.leftX || clickCoordination.second < guiDimension.topY) {
                actionType = AbstractActionType.CLICK_OUTBOUND
            }
        }
        when (actionType) {
            AbstractActionType.LAUNCH_APP -> {
                AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH] = currentState
            }
            AbstractActionType.RESET_APP -> {
                AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH] = currentState
                AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH] = currentState
            }
        }


        if (interaction.targetWidget == null) {
            val allAbstractTransitions = abstractTransitionGraph.edges(prevAbstractState)
            if (actionType == AbstractActionType.RESET_APP || actionType == AbstractActionType.LAUNCH_APP) {
                processLaunchOrResetInteraction(allAbstractTransitions, actionType, actionData, currentAbstractState, interaction, prevAbstractState)
            } else {
                processNonLaunchAndResetNullTargetInteraction(allAbstractTransitions, actionType, actionData, currentAbstractState, interaction, prevAbstractState)
            }

        } else {
            var widgetGroup = prevAbstractState.getAttributeValuationSet(interaction.targetWidget!!, prevState)
            if (widgetGroup != null) {
                val explicitInteractions = prevAbstractState.abstractTransitions.filter { it.isImplicit == false }
                val existingTransition = explicitInteractions.find {
                    it.abstractAction.actionType == actionType
                            && it.abstractAction.attributeValuationSet == widgetGroup
                            && it.abstractAction.extra == actionData
                            && it.prevWindow == windowStack.peek()
                            && it.isImplicit == false
                            && it.dest == currentAbstractState
                }
                if (existingTransition != null) {
                    lastExecutedTransition = existingTransition
                    lastExecutedTransition!!.interactions.add(interaction)
                } else {
                    //No recored abstract interaction before
                    //Or the abstractInteraction is implicit
                    //Record new AbstractInteraction

                    val lastExecutedAction = getOrCreateAbstractAction(actionType, interaction, prevAbstractState, widgetGroup)

                    val newAbstractInteraction = AbstractTransition(
                            abstractAction = lastExecutedAction,
                            interactions = ArrayList(),
                            isImplicit = false,
                            prevWindow = windowStack.peek(),
                            data = actionData,
                            source = prevAbstractState,
                            dest = currentAbstractState)
                    newAbstractInteraction.interactions.add(interaction)
                    abstractTransitionGraph.add(prevAbstractState, currentAbstractState, newAbstractInteraction)
                    lastExecutedTransition = newAbstractInteraction
                }
            }
        }
        if (!currentAbstractState.isRequestRuntimePermissionDialogBox && lastExecutedTransition!=null)
            prevAbstractState.increaseActionCount(lastExecutedTransition!!.abstractAction)
    }

    private fun processNonLaunchAndResetNullTargetInteraction(allAbstractTransitions: List<Edge<AbstractState, AbstractTransition>>, actionType: AbstractActionType, actionData: Any?, currentAbstractState: AbstractState, interaction: Interaction<Widget>, prevAbstractState: AbstractState) {
        val abstractTransition= allAbstractTransitions.find {
            it.label.abstractAction.actionType == actionType
                    && it.label.abstractAction.attributeValuationSet == null
                    && it.label.abstractAction.extra == actionData
                    && it.label.prevWindow == windowStack.peek()
                    && it.label.isImplicit == false
                    && it.label.dest == currentAbstractState
        }
        if (abstractTransition!=null) {
            lastExecutedTransition = abstractTransition.label
            lastExecutedTransition!!.interactions.add(interaction)

        } else {
            createNewAbstractTransition(actionType, interaction, prevAbstractState, null, actionData, currentAbstractState)
        }
    }

    private fun processLaunchOrResetInteraction(allAbstractTransitions: List<Edge<AbstractState, AbstractTransition>>, actionType: AbstractActionType, actionData: Any?, currentAbstractState: AbstractState, interaction: Interaction<Widget>, prevAbstractState: AbstractState) {
        val abstractTransition = allAbstractTransitions.find {
            it.label.abstractAction.actionType == actionType
                    && it.label.abstractAction.attributeValuationSet == null
                    && it.label.abstractAction.extra == actionData
                    && it.label.prevWindow == windowStack.peek()
                    && it.label.isImplicit == false
        }
        if (abstractTransition != null) {
            abstractTransitionGraph.update(abstractTransition.source.data, abstractTransition.destination?.data, currentAbstractState, abstractTransition.label, abstractTransition.label)
        } else {
            val attributeValuationSet: AttributeValuationSet? = null
            createNewAbstractTransition(actionType, interaction, prevAbstractState, attributeValuationSet, actionData, currentAbstractState)
        }
    }

    private fun createNewAbstractTransition(actionType: AbstractActionType, interaction: Interaction<Widget>, prevAbstractState: AbstractState, attributeValuationSet: AttributeValuationSet?, actionData: Any?, currentAbstractState: AbstractState) {
        val lastExecutedAction = getOrCreateAbstractAction(actionType, interaction, prevAbstractState, attributeValuationSet)

        val newAbstractInteraction = AbstractTransition(
                abstractAction = lastExecutedAction,
                interactions = ArrayList(),
                isImplicit = false,
                prevWindow = windowStack.peek(),
                data = actionData,
                source = prevAbstractState,
                dest = currentAbstractState)
        newAbstractInteraction.interactions.add(interaction)
        abstractTransitionGraph.add(prevAbstractState, currentAbstractState, newAbstractInteraction)
        lastExecutedTransition = newAbstractInteraction
    }

    var prevAbstractStateRefinement: Int = 0
    private fun getOrCreateAbstractAction(actionType: AbstractActionType, interaction: Interaction<Widget>, abstractState: AbstractState, attributeValuationSet: AttributeValuationSet?): AbstractAction {
        val actionData = AbstractAction.computeAbstractActionExtraData(actionType, interaction)
        val abstractAction: AbstractAction
        val availableAction = abstractState.getAvailableActions().find {
            it.actionType == actionType
                    && it.attributeValuationSet == attributeValuationSet
                    && it.extra == actionData
        }

        if (availableAction == null) {
            abstractAction = AbstractAction(
                    actionType = actionType,
                    attributeValuationSet = attributeValuationSet,
                    extra = actionData
            )
            abstractState.addAction(abstractAction)
        } else {
            abstractAction = availableAction
        }
        return abstractAction
    }

    private fun normalizeActionType(actionType: String): AbstractActionType {
        return when (actionType) {
            "ClickEvent" -> AbstractActionType.CLICK
            "LongClickEvent" -> AbstractActionType.LONGCLICK
            else -> AbstractActionType.values().find { it.actionName.equals(actionType)}!!
        }
    }

    private fun compareDataOrNot(abstractTransition: AbstractTransition, actionType: AbstractActionType, actionData: Any?): Boolean {
        if (actionType == AbstractActionType.SEND_INTENT || actionType == AbstractActionType.SWIPE) {
            return abstractTransition.data == actionType
        }
        return true
    }




    //endregion





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



    private fun computeAbstractState(newState: State<*>, lastInteractions: List<Interaction<*>>, explorationContext: ExplorationContext<*, *, *>): AbstractState {
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

        val newAbstractState = AbstractStateManager.instance.getOrCreateNewAbstractState(
                newState, currentActivity, currentRotation,internetStatus,null)
        AbstractStateManager.instance.updateLaunchAndResetAbstractTransitions(newAbstractState)
        assert(newAbstractState.guiStates.contains(newState))
        increaseNodeVisit(abstractState = newAbstractState)
        log.info("Computing Abstract State. - DONE")
        return newAbstractState
    }

    val appStatesMap = HashMap<Window, ArrayList<AbstractState>>()
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


    /**
     * Return: True if model is updated, otherwise False
     */
    private fun updateAppModel(prevState: State<*>, newState: State<*>, lastInteractions: List<Interaction<*>>, context: ExplorationContext<*, *, *>): Boolean {
        //update lastChildExecutedEvent
        log.info("Updating App Model")
        measureTimeMillis {
            runBlocking {
                while(!statementMF!!.statementRead) {
                    delay(1)
                }
            }
        }.let {
            log.debug("Wait for reading coverage took $it millis")
        }
        var updated: Boolean = false

        measureTimeMillis {
            statementMF!!.statementRead = false
            val currentAbstractState = computeAbstractState(newState, lastInteractions, context)
            stateList.add(newState)
            guiState_AbstractStateMap.put(newState,currentAbstractState)
            var prevAbstractState = getAbstractState(prevState)
            if (prevAbstractState == null && prevState != context.model.emptyState) {
                prevAbstractState = computeAbstractState(prevState, emptyList(),context)
                stateList.add(stateList.size-1,prevState)
                guiState_AbstractStateMap.put(prevState,prevAbstractState)
            }
            if (!newState.isHomeScreen && firstRun) {
                AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH] = newState
                AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH] = newState
                firstRun = false
            }


            if (lastInteractions.isNotEmpty()) {
                lastExecutedTransition = null
                computeAbstractInteraction(ArrayList(lastInteractions), prevState, newState, windowStack.peek())
                updateWindowStack(prevAbstractState, currentAbstractState,fromLaunch)
                //update lastExecutedEvent
                if (lastExecutedTransition == null) {
                    log.debug("lastExecutedEvent is null")
                    updated = false
                } else {
                    updated = updateAppModelWithLastExecutedEvent(prevState, newState, lastInteractions)
                    if (prevAbstractState!!.belongToAUT() && currentAbstractState.isOutOfApplication) {
                        lastOpeningAnotherAppInteraction = lastInteractions.single()
                    }
                    if (!prevAbstractState.belongToAUT() && currentAbstractState.belongToAUT()) {
                        lastOpeningAnotherAppInteraction = null
                    }
                }
            } else {
                updated = false
            }

        }.let {
            log.debug("Update model took $it  millis")
        }
        return updated
    }

    private fun updateAppModelWithLastExecutedEvent(prevState: State<*>, newState: State<*>, lastInteractions: List<Interaction<*>>): Boolean {
        assert(statementMF != null, { "StatementCoverageMF is null" })
        val abstractInteraction = lastExecutedTransition!!
        val prevAbstractState = getAbstractState(prevState)
        if (prevAbstractState == null) {
            return false
        }
        val newAbstractState = getAbstractState(newState)!!

        //Extract text input widget data
        val condition = HashMap(Helper.extractInputFieldAndCheckableWidget(prevState))
        val edge = abstractTransitionGraph.edge(prevAbstractState,newAbstractState,abstractInteraction)
        if (edge==null)
            return false
        if (condition.isNotEmpty()) {
            if (!abstractTransitionGraph.containsCondition(edge,condition))
                abstractTransitionGraph.addNewCondition(edge,condition)
        }
        //if (!abstractInteraction.abstractAction.isActionQueue())
        updateCoverage(prevAbstractState, newAbstractState, abstractInteraction,lastInteractions.first())
        //create StaticEvent if it dose not exist in case this abstract Interaction triggered modified methods
        if(!prevAbstractState.inputMapping.containsKey(abstractInteraction.abstractAction) && !abstractInteraction.abstractAction.isActionQueue() && !abstractInteraction.abstractAction.isLaunchOrReset() )
        {
            createStaticEventFromAbstractInteraction(prevAbstractState,newAbstractState,abstractInteraction,lastInteractions.first())
        }
        if(!prevAbstractState.isRequestRuntimePermissionDialogBox)
        {
            val coverageIncreased = statementMF!!.executedModifiedMethodStatementsMap.size - statementMF!!.prevUpdateCoverage
            if (prevAbstractState.isOutOfApplication && newAbstractState.belongToAUT() && !abstractInteraction.abstractAction.isLaunchOrReset() && lastOpeningAnotherAppInteraction!=null) {
                val lastAppState = stateList.find { it.stateId == lastOpeningAnotherAppInteraction!!.prevState }!!
                val lastAppAbstractState = getAbstractState(lastAppState)!!
                val lastOpenningAnotherAppAbstractInteraction = findAbstractInteraction(lastOpeningAnotherAppInteraction)
                updateWindowTransitionCoverage(lastAppAbstractState,lastOpenningAnotherAppAbstractInteraction!!,coverageIncreased)
            } else
                updateWindowTransitionCoverage(prevAbstractState, abstractInteraction, coverageIncreased)

        }

       /* if (allTargetWindow_ModifiedMethods.containsKey(prevAbstractState.window) &&
                newAbstractState.window.activityClass == prevAbstractState.window.activityClass
                ) {
            if (!allTargetWindow_ModifiedMethods.containsKey(newAbstractState.window)) {
                allTargetWindow_ModifiedMethods.put(newAbstractState.window, allTargetWindow_ModifiedMethods[prevAbstractState.window]!!)
                if (windowHandlersHashMap.containsKey(prevAbstractState.window)) {
                    windowHandlersHashMap.put(newAbstractState.window, windowHandlersHashMap[prevAbstractState.window]!!)
                }
            }
        }*/

        if (abstractInteraction.abstractAction.actionType != AbstractActionType.ACTION_QUEUE) {
            measureTimeMillis {
                AbstractStateManager.instance.addImplicitAbstractInteraction(newState,prevAbstractState,newAbstractState,abstractInteraction,windowStack.peek(),condition)
            }.let {
                log.debug("Add implicit abstracty interaction took $it millis")
            }

        }
        return true
    }

    private fun updateWindowTransitionCoverage(prevAbstractState: AbstractState, abstractTransition: AbstractTransition, coverageIncreased: Int) {
        val event = prevAbstractState.inputMapping[abstractTransition.abstractAction]
        if (event != null) {
            event.forEach {
                if (it.eventType != EventType.resetApp) {
                    if (abstractTransition.modifiedMethods.isNotEmpty()) {
                        if (!allTargetStaticEvents.contains(it))
                            allTargetStaticEvents.add(it)
                    }
                    updateStaticEventHandlersAndModifiedMethods(it, abstractTransition, coverageIncreased)
                }
            }
        }
        if (abstractTransition.modifiedMethods.isNotEmpty()) {
            if (!allTargetWindow_ModifiedMethods.contains(prevAbstractState.window)
                    && prevAbstractState.window !is Launcher
                    && prevAbstractState.window !is OutOfApp) {
                if (!prevAbstractState.isRequestRuntimePermissionDialogBox) {
                    allTargetWindow_ModifiedMethods.put(prevAbstractState.window, hashSetOf())
                    allTargetWindow_ModifiedMethods[prevAbstractState.window]!!.addAll(abstractTransition.modifiedMethods.map { it.key })
                }
            }
            if (!prevAbstractState.targetActions.contains(abstractTransition.abstractAction)) {
                prevAbstractState.targetActions.add(abstractTransition.abstractAction)
            }
            val virtualAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == prevAbstractState.window }.firstOrNull()
            if (virtualAbstractState != null && virtualAbstractState.targetActions.contains(abstractTransition.abstractAction)) {
                virtualAbstractState.targetActions.add(abstractTransition.abstractAction)
            }
        }
    }


    private fun createStaticEventFromAbstractInteraction(prevAbstractState: AbstractState, newAbstractState: AbstractState, abstractTransition: AbstractTransition, interaction: Interaction<Widget>?) {
        val eventType = Input.getEventTypeFromActionName(abstractTransition.abstractAction.actionType)
        if (eventType == EventType.fake_action || eventType == EventType.resetApp || eventType == EventType.implicit_launch_event)
            return
        if (interaction!=null && interaction.targetWidget!=null && interaction.targetWidget!!.isKeyboard)
            return
        var newInput: Input? = null
        if (abstractTransition.abstractAction.attributeValuationSet == null)
        {
            newInput = Input(
                    eventType = eventType,
                    widget = null,
                    sourceWindow = prevAbstractState.window,
                    eventHandlers = HashSet(),
                    createdAtRuntime = true
            )
            newInput.data = abstractTransition.abstractAction.extra
            newInput.eventHandlers.addAll(abstractTransition.handlers.map { it.key })

            wtg.add(prevAbstractState.window,newAbstractState.window,newInput)
            if (!prevAbstractState.inputMapping.containsKey(abstractTransition.abstractAction)) {
                prevAbstractState.inputMapping.put(abstractTransition.abstractAction, arrayListOf())
            }
            prevAbstractState.inputMapping.get(abstractTransition.abstractAction)!!.add(newInput)
            AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it == prevAbstractState }. filter { it.window == prevAbstractState.window }.forEach {
                val similarAbstractAction = it.getAvailableActions().find { it == abstractTransition.abstractAction }
                if (similarAbstractAction != null) {
                    it.inputMapping.put(similarAbstractAction, arrayListOf(newInput!!))
                }
            }
        }
        else
        {
            val attributeValuationSet = abstractTransition.abstractAction.attributeValuationSet
            if (!prevAbstractState.staticWidgetMapping.containsKey(attributeValuationSet)){
                val attributeValuationSetId = if (attributeValuationSet.getResourceId().isBlank())
                    emptyUUID
                else
                    attributeValuationSet.avsId
                // create new static widget and add to the abstract state
                val staticWidget = StaticWidget(
                        widgetId = attributeValuationSet.avsId.toString(),
                        resourceIdName = attributeValuationSet.getResourceId(),
                        resourceId = "",
                        activity = prevAbstractState.activity,
                        wtgNode = prevAbstractState.window,
                        className = attributeValuationSet.getClassName(),
                        contentDesc = attributeValuationSet.getContentDesc(),
                        text = attributeValuationSet.getText(),
                        createdAtRuntime = true,
                        attributeValuationSetId = attributeValuationSetId
                )
                prevAbstractState.staticWidgetMapping.put(attributeValuationSet, arrayListOf(staticWidget))
                AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it == prevAbstractState }. filter { it.window == prevAbstractState.window }.forEach {
                    val similarWidget = it.attributeValuationSets.find { it == attributeValuationSet }
                    if (similarWidget != null ) {
                        it.staticWidgetMapping.put(similarWidget,arrayListOf(staticWidget))
                    }
                }
            }
            if (prevAbstractState.staticWidgetMapping.contains(attributeValuationSet))
            {

                prevAbstractState.staticWidgetMapping[attributeValuationSet]!!.forEach { staticWidget->
                    allTargetStaticWidgets.add(staticWidget)
                    newInput = Input(
                              eventType = eventType,
                              widget = staticWidget,
                              sourceWindow = prevAbstractState.window,
                              eventHandlers = HashSet(),
                            createdAtRuntime = true
                    )
                    newInput!!.data = abstractTransition.abstractAction.extra
                    newInput!!.eventHandlers.addAll(abstractTransition.handlers.map { it.key })

                    wtg.add(prevAbstractState.window,newAbstractState.window,newInput!!)
                    if (!prevAbstractState.inputMapping.containsKey(abstractTransition.abstractAction)) {
                        prevAbstractState.inputMapping.put(abstractTransition.abstractAction, arrayListOf())
                    }
                    prevAbstractState.inputMapping.get(abstractTransition.abstractAction)!!.add(newInput!!)
                    AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it == prevAbstractState }. filter { it.window == prevAbstractState.window }.forEach {
                        val similarAbstractAction = it.getAvailableActions().find { it == abstractTransition.abstractAction }
                        if (similarAbstractAction != null) {
                            it.inputMapping.put(similarAbstractAction, arrayListOf(newInput!!))
                        }
                    }
                }
            }
        }
/*        if (newInput!=null && abstractInteraction.modifiedMethods.any { it.value == true }) {
            log.debug("New target Window Transition detected: $newInput")
            allTargetStaticEvents.add(newInput!!)
        }*/

    }

    val guiInteractionCoverage = HashMap<Interaction<*>, HashSet<String>>()



    private fun updateCoverage(sourceAbsState: AbstractState, currentAbsState: AbstractState, abstractTransition: AbstractTransition, interaction: Interaction<Widget>) {
        val edge = abstractTransitionGraph.edge(sourceAbsState, currentAbsState, abstractTransition)
        if (edge == null)
            return
        val edgeStatementCoverage = abstractTransitionGraph.statementCoverageInfo[edge]!!
        val edgeMethodCoverage = abstractTransitionGraph.methodCoverageInfo[edge]!!
        if (!guiInteractionCoverage.containsKey(interaction)) {
            guiInteractionCoverage.put(interaction, HashSet())
        }
        val interactionCoverage = guiInteractionCoverage.get(interaction)!!

        val lastOpeningAnotherAppAbstractInteraction = findAbstractInteraction(lastOpeningAnotherAppInteraction)

        runBlocking {
            statementMF!!.mutex.withLock {
                val recentExecutedStatementSize = statementMF!!.recentExecutedStatements.size
                if (recentExecutedStatementSize == 0) {
                    // This action does not trigger any application code

                }
                statementMF!!.recentExecutedStatements.forEach { statementId ->
                    if (!interactionCoverage.contains(statementId)) {
                        interactionCoverage.add(statementId)
                    }
                    if (!edgeStatementCoverage.contains(statementId)) {
                        edgeStatementCoverage.add(statementId)
                    }
                    if (sourceAbsState.isOutOfApplication && currentAbsState.belongToAUT() && lastOpeningAnotherAppAbstractInteraction != null) {
                        lastOpeningAnotherAppAbstractInteraction.updateUpdateStatementCoverage(statementId, this@AutAutMF)
                    } else {
                        abstractTransition.updateUpdateStatementCoverage(statementId,this@AutAutMF)
                    }

                }
                statementMF!!.recentExecutedMethods.forEach { methodId ->
                    val methodName = statementMF!!.getMethodName(methodId)
                    if (!edgeMethodCoverage.contains(methodId)) {
                        edgeMethodCoverage.add(methodId)
                    }
                    if (unreachableModifiedMethods.contains(methodName))
                    {
                        unreachableModifiedMethods.remove(methodName)
                    }
                    if (allEventHandlers.contains(methodId) || modifiedMethodTopCallersMap.filter { it.value.contains(methodId) }.isNotEmpty())
                    {

                        if (sourceAbsState.isOutOfApplication && currentAbsState.belongToAUT() && lastOpeningAnotherAppAbstractInteraction != null) {
                            if (lastOpeningAnotherAppAbstractInteraction.handlers.containsKey(methodId)){
                                lastOpeningAnotherAppAbstractInteraction.handlers[methodId] = true
                            }
                            else
                            {
                                lastOpeningAnotherAppAbstractInteraction.handlers.put(methodId,true)
                            }
                        } else {
                            if (abstractTransition.handlers.containsKey(methodId)){
                                abstractTransition.handlers[methodId] = true
                            }
                            else
                            {
                                abstractTransition.handlers.put(methodId,true)
                            }
                        }
                    }

                    if (untriggeredTargetHandlers.contains(methodId)) {
                        untriggeredTargetHandlers.remove(methodId)
                    }

                }
            }
        }
    }

    private fun findAbstractInteraction(interaction: Interaction<Widget>?): AbstractTransition? {
        return if (interaction != null) {
            val lastAppState = stateList.find { it.stateId == interaction.prevState }
            if (lastAppState == null)
                throw Exception()
            val lastAppAbstractState = getAbstractState(lastAppState)!!
            lastAppAbstractState.abstractTransitions.find { it.interactions.contains(interaction) }
        } else {
            null
        }
    }

    private fun updateStaticEventHandlersAndModifiedMethods(input: Input, abstractTransition: AbstractTransition, coverageIncreased: Int) {
        abstractTransition.modifiedMethods.filter { it.value == true }. forEach {
            input.modifiedMethods[it.key] = it.value
        }
        val newCoveredStatements = ArrayList<String>()
        abstractTransition.modifiedMethodStatement.filter { it.value==true }. forEach {
            if (!input.modifiedMethodStatement.containsKey(it.key) || input.modifiedMethodStatement.get(it.key) == false )
            {
                input.modifiedMethodStatement[it.key] = it.value
                newCoveredStatements.add(it.key)

            }
        }
        if (coverageIncreased>0) {
            log.debug("New $coverageIncreased updated statements covered by event: $input.")
            //log.debug(newCoveredStatements.toString())
        }
        input.coverage.put(dateFormater.format(System.currentTimeMillis()), input.modifiedMethodStatement.filterValues { it == true }.size)
        abstractTransition.handlers.filter { it.value == true }. forEach {
            input.verifiedEventHandlers.add(it.key)
            input.eventHandlers.add(it.key)
        }
        val eventhandlers = ArrayList(input.eventHandlers)
        eventhandlers.forEach {
            if (!input.verifiedEventHandlers.contains(it)) {
                if (!abstractTransition.handlers.containsKey(it)) {
                    input.eventHandlers.remove(it)
                } else if (abstractTransition.handlers[it] == false) {
                    input.eventHandlers.remove(it)
                } else {
                    if (!input.eventHandlers.contains(it)) {
                        input.eventHandlers.add(it)
                    }
                    input.verifiedEventHandlers.add(it)
                }
            }
        }
        removeUnreachableModifiedMethods(input)

    }

    private fun removeUnreachableModifiedMethods(input: Input) {
        val toRemovedModifiedMethods = ArrayList<String>()
        input.modifiedMethods.forEach {
            val methodName = it.key
            if (modifiedMethodTopCallersMap.containsKey(methodName)) {
                val handlers = modifiedMethodTopCallersMap[methodName]!!
                if (input.eventHandlers.intersect(handlers).isEmpty()) {
                    toRemovedModifiedMethods.add(methodName)
                }
            }
        }
        toRemovedModifiedMethods.forEach {
            input.modifiedMethods.remove(it)
        }
    }



    //endregion

/*    private fun getCurrentEventCoverage() {
        val triggeredEventCount = allTargetStaticEvents.size - untriggeredTargetEvents.size
        // log.debug("Current target widget coverage: ${triggeredWidgets}/${allTargetStaticWidgets.size}=${triggeredWidgets / allTargetStaticWidgets.size.toDouble()}")
        log.debug("Current target event coverage: $triggeredEventCount/${allTargetStaticEvents.size} = ${triggeredEventCount/allTargetStaticEvents.size.toDouble()}")
    }*/

    fun isOptionMenuOpen(currentState: State<*>): Boolean {
        val window = WindowManager.instance.getWindowByState(currentState)
        if (window is OptionsMenu)
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

    val unableFindTargetNodes = HashMap<Window, Int>()



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
    val notFullyCoveredTargetEvents = HashMap<Input,Int>() //Event - number of exercise

    fun resetIneffectiveActionCounter(){
        updateMethodCovFromLastChangeCount = 0
    }




    fun validateEvent(e: Input, currentState: State<*>): List<AbstractAction> {
        if (e.eventType == EventType.implicit_rotate_event && !appRotationSupport) {
            if (allTargetStaticEvents.contains(e)) {
                allTargetStaticEvents.remove(e)
            }
            return emptyList()
        }
        val currentAbstractState = getAbstractState(currentState)!!
        val availableAbstractActions = currentAbstractState.inputMapping.filter { it.value.contains(e) }.map { it.key }
        return availableAbstractActions
    }


    var numberOfContinuousRandomAction: Int =0
    fun canExerciseTargetActivty(): Boolean {
        //TODO: Implement it before using
        return true
    }


    //endregion







    fun isPressBackCanGoToHomescreen(currentAbstractState: AbstractState): Boolean {

        val pressBackEdges = abstractTransitionGraph.edges(currentAbstractState).filter {
                    it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK
        }
        val backToHomeScreen = pressBackEdges.find { it.destination!=null && it.destination!!.data.isHomeScreen }
        return (backToHomeScreen!=null)
    }

    fun isPressBackCanGoToHomescreen(currentState: State<*>): Boolean {
        val currentAbstractState = getAbstractState(currentState)
        if (currentAbstractState == null)
            return false
        val pressBackEdges = abstractTransitionGraph.edges(currentAbstractState).filter {
            it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK
        }
        val backToHomeScreen = pressBackEdges.find { it.destination!=null && it.destination!!.data.isHomeScreen }
        return (backToHomeScreen!=null)
    }

    fun getRuntimeWidgets(attributeValuationSet: AttributeValuationSet, widgetAbstractState: AbstractState, currentState: State<*>): List<Widget> {
        val allGUIWidgets = attributeValuationSet.getGUIWidgets(currentState)
        if (allGUIWidgets.isEmpty()) {
            //try get the same static widget
            val abstractState = getAbstractState(currentState)
            if (widgetAbstractState.staticWidgetMapping.containsKey(attributeValuationSet) && abstractState!=null) {
                val staticWidgets = widgetAbstractState.staticWidgetMapping[attributeValuationSet]!!
                val similarWidgetGroups = abstractState.staticWidgetMapping.filter { it.value.intersect(staticWidgets).isNotEmpty() }.map { it.key }
                return similarWidgetGroups.map { it.getGUIWidgets(currentState) }.flatten()
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
            wtg.constructFromJson(jObj)
            readActivityAlias(jObj)
            readWindowWidgets(jObj)
            readMenuItemTexts(jObj)
            readActivityDialogs(jObj)
            readWindowHandlers(jObj)
            log.debug("Reading modified method invocation")
            readModifiedMethodTopCallers(jObj)
            readModifiedMethodInvocation(jObj)
            readUnreachableModfiedMethods(jObj)
            //log.debug("Reading all strings")
            //readAllStrings(jObj)
            readEventCorrelation(jObj)
            readMethodDependency(jObj)
            readWindowDependency(jObj)
            if (manualIntent) {
                readIntentModel()
            }
            if (manualInput) {
                val textInputFile = getTextInputFile()
                if (textInputFile != null) {
                    inputConfiguration = InputConfigurationFileHelper.readInputConfigurationFile(textInputFile)
                    TextInput.inputConfiguration = inputConfiguration

                }
            }
            val deviceConfigurationFile = getDeviceConfigurationFile()
            if (deviceConfigurationFile != null) {
                deviceEnvironmentConfiguration = DeviceEnvironmentConfigurationFileHelper.readInputConfigurationFile(deviceConfigurationFile)

            }
        }

    }

    val methodTermsHashMap = HashMap<String, HashMap<String, Long>>()
    val windowTermsHashMap = HashMap<Window, HashMap<String,Long>>()
    val windowHandlersHashMap = HashMap<Window, Set<String>>()
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
            windowTermsHashMap.putAll(StaticAnalysisJSONFileHelper.readWindowTerms(jsonWindowTerm, wtg))
        }
    }

    private fun readWindowHandlers (jObj: JSONObject) {
        val jsonWindowHandlers = jObj.getJSONObject("windowHandlers")
        if (jsonWindowHandlers != null) {
            windowHandlersHashMap.putAll(StaticAnalysisJSONFileHelper.readWindowHandlers(jsonWindowHandlers,wtg, statementMF!!))
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
            staticEventWindowCorrelation.putAll(StaticAnalysisJSONFileHelper.readEventWindowCorrelation(eventCorrelationJson,wtg))
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
                var intentActivityNode = WindowManager.instance.allWindows.find { it.classType == qualifiedActivityName }
                if (intentActivityNode == null) {
                    intentActivityNode = Activity.getOrCreateNode(Activity.getNodeId(), qualifiedActivityName)
                }
                u.forEach {
                    for (meaningNode in WindowManager.instance.allMeaningWindows) {
                        val intentEvent = Input(eventType = EventType.callIntent,
                                eventHandlers = HashSet(), widget = null,sourceWindow = meaningNode)
                        intentEvent.data = it
                        wtg.add(meaningNode, intentActivityNode!!, intentEvent)
                    }

                }

                if (allTargetStaticEvents.filter {
                            it.sourceWindow.activityClass.contains(activityName)
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
            AutAutMF.log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val intentModelFile = getIntentModelFile(appName, resourceDir)
            if (intentModelFile != null)
                return intentModelFile
            else {
                AutAutMF.log.warn("Provided directory ($resourceDir) does not contain " +
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
        StaticAnalysisJSONFileHelper.readMenuItemText(jMap, OptionsMenu.allNodes)
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
        StaticAnalysisJSONFileHelper.readAllWidgetEvents(jMap, wtg, allEventHandlers, statementMF!!)

    }

    val modifiedMethodTopCallersMap = HashMap<String, Set<String>>()

    private fun readModifiedMethodTopCallers(jObj: JSONObject){
        var jMap = jObj.getJSONObject("modiMethodTopCaller")
        StaticAnalysisJSONFileHelper.readModifiedMethodTopCallers(jMap,modifiedMethodTopCallersMap,statementMF!!)

        //Add windows containing top caller to allTargetWindows list
        modifiedMethodTopCallersMap.forEach { modMethod, topCallers ->
            allTargetHandlers.addAll(topCallers)
            topCallers.forEach { caller ->
                val windows = windowHandlersHashMap.filter { it.value.contains(caller) }.map { it.key }
                if (windows.isNotEmpty()) {
                    windows.forEach {w ->
                        if (!allTargetWindow_ModifiedMethods.contains(w)) {
                            allTargetWindow_ModifiedMethods.put(w, hashSetOf())
                        }
                        allTargetWindow_ModifiedMethods[w]!!.add(modMethod)
                        val contextMenus = wtg.getContextMenus(w)
                        contextMenus.forEach {
                            if (!allTargetWindow_ModifiedMethods.containsKey(it))
                                allTargetWindow_ModifiedMethods.put(it, hashSetOf())
                            allTargetWindow_ModifiedMethods[it]!!.add(modMethod)
                            if (!windowHandlersHashMap.containsKey(it)){
                                windowHandlersHashMap.put(it, windowHandlersHashMap[w]!!)
                            }
                        }
                        val optionsMenu = wtg.getOptionsMenu(w)
                        if (optionsMenu != null) {
                            if (!allTargetWindow_ModifiedMethods.contains(optionsMenu))
                                allTargetWindow_ModifiedMethods.put(optionsMenu, hashSetOf())
                            allTargetWindow_ModifiedMethods[optionsMenu]!!.add(modMethod)
                            if (!windowHandlersHashMap.containsKey(optionsMenu)) {
                                windowHandlersHashMap.put(optionsMenu, windowHandlersHashMap[w]!!)
                            }
                        }
                    }
                }
            }
        }

        untriggeredTargetHandlers.addAll(allTargetHandlers)
    }

    private fun readModifiedMethodInvocation(jObj: JSONObject) {
        var jMap = jObj.getJSONObject("modiMethodInvocation")
        StaticAnalysisJSONFileHelper.readModifiedMethodInvocation(jsonObj = jMap,
                wtg = wtg,
                allTargetInputs = allTargetStaticEvents,
                allTargetStaticWidgets = allTargetStaticWidgets,
                statementCoverageMF = statementMF!!)
        allTargetStaticEvents.forEach {
            val sourceWindow = it.sourceWindow
            if (!allTargetWindow_ModifiedMethods.contains(sourceWindow) && sourceWindow !is OutOfApp) {
                allTargetWindow_ModifiedMethods.put(sourceWindow, hashSetOf())
            }
            allTargetWindow_ModifiedMethods[sourceWindow]!!.addAll(it.modifiedMethods.keys)
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
            AutAutMF.log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val instrumentationFile = getAppModelFile(appName, resourceDir)
            if (instrumentationFile != null)
                return instrumentationFile
            else {
                AutAutMF.log.warn("Provided directory ($resourceDir) does not contain " +
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
            AutAutMF.log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val textInputFile = getTextInputFile(appName, resourceDir)
            if (textInputFile != null)
                return textInputFile
            else {
                AutAutMF.log.warn("Provided directory ($resourceDir) does not contain " +
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

    private fun getDeviceConfigurationFile(): Path? {
        if (!Files.exists(resourceDir)) {
            AutAutMF.log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val configurationFile = getDeviceConfigurationFile(appName, resourceDir)
            if (configurationFile != null)
                return configurationFile
            else {
                AutAutMF.log.warn("Provided directory ($resourceDir) does not contain " +
                        "the corresponding configuration file.")
                return null
            }
        }
    }

    private fun getDeviceConfigurationFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-configuration.json")
                }
                .findFirst()
                .orElse(null)
    }

    private fun addWidgetToActivtity_TargetWidget_Map(activity: String, event: Input) {
        if (!activity_TargetComponent_Map.containsKey(activity)) {
            activity_TargetComponent_Map[activity] = ArrayList()
        }
        if (!activity_TargetComponent_Map[activity]!!.contains(event)) {
            activity_TargetComponent_Map[activity]!!.add(event)
        }
    }

    private fun haveContextMenuOnItsWidget(currentActivity: String?): Boolean{
        val wtgNode = WindowManager.instance.allWindows.find { it.classType == currentActivity }
        if (wtgNode == null)
            return false
        return wtg.haveContextMenu(wtgNode)
    }

    //endregion

    fun produceTargetWidgetReport(context: ExplorationContext<*,*,*>) {
        val sb = StringBuilder()
        sb.appendln("Statements;${statementMF!!.statementInstrumentationMap.size}")
        sb.appendln("Methods;${statementMF!!.methodInstrumentationMap.size}")
        sb.appendln("ModifiedMethods;${statementMF!!.modMethodInstrumentationMap.size}")
        sb.appendln("ModifiedMethodsStatements;${
        statementMF!!.statementMethodInstrumentationMap.filter { statementMF!!.modMethodInstrumentationMap.contains(it.value) }.size
        } ")
        sb.appendln("CoveredStatements;${statementMF!!.executedStatementsMap.size}")
        sb.appendln("CoveredMethods;${statementMF!!.executedMethodsMap.size}")
        sb.appendln("CoveredModifiedMethods;${statementMF!!.executedModifiedMethodsMap.size}")
        val executedModifiedMethodStatement = statementMF!!.executedStatementsMap.filter { statementMF!!.modMethodInstrumentationMap.contains(statementMF!!.statementMethodInstrumentationMap[it.key]) }
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
        sb.appendln("Phase1StatementMethodCoverage;$phase1StatementCoverage")
        sb.appendln("Phase1MethodCoverage;$phase1MethodCoverage")
        sb.appendln("Phase1ModifiedStatementCoverage;$phase1ModifiedStatementCoverage")
        sb.appendln("Phase1ModifiedMethodCoverage;$phase1ModifiedMethodCoverage")
        sb.appendln("Phase1ActionCount;$phase1Actions")
        sb.appendln("Phase2StatementMethodCoverage;$phase2StatementCoverage")
        sb.appendln("Phase2MethodCoverage;$phase2MethodCoverage")
        sb.appendln("Phase2ModifiedMethodCoverage;$phase2ModifiedCoverage")
        sb.appendln("Phase2ModifiedStatementCoverage;$phase2ModifiedStatementCoverage")
        sb.appendln("Phase2ActionCount;$phase2Actions")
        sb.appendln("Phase2StartTime:$phase2StartTime")
        sb.appendln("Phase3StartTime:$phase3StartTime")
        sb.appendln("Unreached windows;")
        WindowManager.instance.allWindows.filterNot {
       it is Launcher
                || it is OutOfApp || it is FakeWindow
        }. filter { node -> AbstractStateManager.instance.ABSTRACT_STATES.find { it.window == node && it !is VirtualAbstractState} == null} .forEach {
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

        val outputFile = context.model.config.baseDir.resolve(targetWidgetFileName)
        AutAutMF.log.info("Prepare writing triggered widgets report file: " +
                "\n- File name: ${outputFile.fileName}" +
                "\n- Absolute path: ${outputFile.toAbsolutePath().fileName}")

        Files.write(outputFile, sb.lines())
        AutAutMF.log.info("Finished writing report in ${outputFile.fileName}")
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

    fun accumulateEventsDependency(): HashMap<Input, HashMap<String, Long>> {
        val result = HashMap<Input, HashMap<String,Long>>()
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
            if (eventDependency.isNotEmpty())
                result.put(event,eventDependency)
        }
        return result
    }

    fun updateStage1Info(eContext: ExplorationContext<*,*,*>) {
        phase1ModifiedMethodCoverage = statementMF!!.getCurrentModifiedMethodCoverage()
        phase1StatementCoverage = statementMF!!.getCurrentCoverage()
        phase1MethodCoverage = statementMF!!.getCurrentMethodCoverage()
        phase1ModifiedStatementCoverage = statementMF!!.getCurrentModifiedMethodStatementCoverage()
        phase1Actions = eContext.explorationTrace.getActions().size
        setPhase2StartTime()
    }

    fun updateStage2Info(eContext: ExplorationContext<*,*,*>) {
        phase2ModifiedCoverage = statementMF!!.getCurrentModifiedMethodCoverage()
        phase2StatementCoverage = statementMF!!.getCurrentCoverage()
        phase2MethodCoverage = statementMF!!.getCurrentMethodCoverage()
        phase2ModifiedStatementCoverage = statementMF!!.getCurrentModifiedMethodStatementCoverage()
        phase2Actions = eContext.explorationTrace.getActions().size
        setPhase3StartTime()
    }

    fun havingInternetConfiguration(window: Window): Boolean {
        if (deviceEnvironmentConfiguration == null)
            return false
        if (!deviceEnvironmentConfiguration!!.configurations.containsKey("Internet"))
            return false
        if (deviceEnvironmentConfiguration!!.configurations["Internet"]!!.contains(window))
            return true
        return false
    }





    companion object {

        @JvmStatic
        val log: Logger by lazy { LoggerFactory.getLogger(AutAutMF::class.java) }
        object RegressionStrategy: PropertyGroup() {
            val use by booleanType
            val budgetScale by doubleType
            val manualInput by booleanType
            val manualIntent by booleanType
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