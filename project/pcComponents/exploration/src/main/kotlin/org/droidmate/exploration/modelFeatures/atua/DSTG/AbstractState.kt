package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.EWTG.*
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Activity
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.modelReuse.ModelVersion
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.toUUID
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

open class AbstractState(
        id: String? = null,
        val activity: String,
        val attributeValuationMaps: ArrayList<AttributeValuationMap> = arrayListOf(),
        val guiStates: ArrayList<State<*>> = ArrayList(),
        var window: Window,
        val EWTGWidgetMapping: HashMap<AttributeValuationMap, EWTGWidget> = HashMap(),
        val abstractTransitions: HashSet<AbstractTransition> = HashSet(),
        val inputMappings: HashMap<AbstractAction, ArrayList<Input>> = HashMap(),
        val isHomeScreen: Boolean = false,
        val isOpeningKeyboard: Boolean = false,
        val isRequestRuntimePermissionDialogBox: Boolean = false,
        val isAppHasStoppedDialogBox: Boolean = false,
        val isOutOfApplication: Boolean = false,
        var hasOptionsMenu: Boolean = true,
        var rotation: Rotation,
        var internet: InternetStatus,
        var loadedFromModel: Boolean = false,
        var modelVersion: ModelVersion = ModelVersion.RUNNING
) {
    val actionCount = HashMap<AbstractAction, Int>()
    val targetActions = HashSet<AbstractAction>()

    val abstractStateId: String
    var hashCode: Int = 0
    var isInitalState = false
    init {
        abstractStateIdByWindow.putIfAbsent(window,0)
        val maxId = abstractStateIdByWindow[window]!!
        if (id==null) {
            abstractStateId = "${window}_${maxId + 1}"
        } else {
            abstractStateId = id
        }
        abstractStateIdByWindow.put(window,maxId+1)
        window.mappedStates.add(this)
        attributeValuationMaps.forEach {
            it.captured = true
        }

        countAVMFrequency()
        hashCode = computeAbstractStateHashCode(attributeValuationMaps,activity, rotation, internet)
    }
    fun updateHashCode() {
        hashCode = computeAbstractStateHashCode(attributeValuationMaps,activity, rotation, internet)
    }
     fun countAVMFrequency() {
        if (!AbstractStateManager.instance.attrValSetsFrequency.containsKey(window)) {
            AbstractStateManager.instance.attrValSetsFrequency.put(window, HashMap())
        }
        val widgetGroupFrequency = AbstractStateManager.instance.attrValSetsFrequency[window]!!
        attributeValuationMaps.forEach {
            if (!widgetGroupFrequency.containsKey(it)) {
                widgetGroupFrequency.put(it, 1)
            } else {
                widgetGroupFrequency[it] = widgetGroupFrequency[it]!! + 1
            }
        }
    }

    fun isStructuralEqual(other: AbstractState): Boolean {
        return hashCode == other.hashCode
    }

    fun initAction(){
        val resetAction = AbstractAction(
                actionType = AbstractActionType.RESET_APP
        )
        actionCount.put(resetAction,0)
        val launchAction = AbstractAction(
                actionType = AbstractActionType.LAUNCH_APP
        )
        actionCount.put(launchAction,0)
        if (isOpeningKeyboard) {
            val closeKeyboardAction = AbstractAction(
                    actionType = AbstractActionType.CLOSE_KEYBOARD
            )
            actionCount.put(closeKeyboardAction, 0)
            return
        }
        val pressBackAction = AbstractAction(
                actionType = AbstractActionType.PRESS_BACK
        )
        actionCount.put(pressBackAction, 0)
        if (window !is OptionsMenu && window !is Dialog) {
            if (WindowManager.instance.updatedModelWindows.any { it is OptionsMenu && it.ownerActivity == this.window }) {
                val pressMenuAction = AbstractAction(
                        actionType = AbstractActionType.PRESS_MENU
                )
                actionCount.put(pressMenuAction, 0)
            }
            val minmaxAction = AbstractAction(
                    actionType = AbstractActionType.MINIMIZE_MAXIMIZE
            )
            actionCount.put(minmaxAction, 0)

        }
        if (window is Activity || rotation == Rotation.LANDSCAPE) {
            val rotationAction = AbstractAction(
                    actionType = AbstractActionType.ROTATE_UI
            )
            actionCount.put(rotationAction, 0)
        }

        if (window is Dialog) {
            val clickOutDialog = AbstractAction(
                    actionType = AbstractActionType.CLICK_OUTBOUND
            )
            actionCount.put(clickOutDialog, 0)
        }

        attributeValuationMaps.forEach {
            //it.actionCount.clear()
            it.initActions()
        }
    }

    @Suppress
    fun addAttributeValuationSet(attributeValuationMap: AttributeValuationMap) {
        if (attributeValuationMaps.contains(attributeValuationMap)) {
            return
        }
        //attributeValuationSets.add(attributeValuationSet)
        attributeValuationMap.captured = true
        val attrValSetsFrequency = AbstractStateManager.instance.attrValSetsFrequency[window]!!
        attrValSetsFrequency[attributeValuationMap] = 1
    }

    fun addAction(action: AbstractAction) {
        if (action.actionType == AbstractActionType.PRESS_HOME) {
            return
        }
        if (action.attributeValuationMap == null) {
            if (action.actionType == AbstractActionType.CLICK)
                return
            if (!actionCount.containsKey(action)) {
                actionCount[action] = 0
            }
            return
        }
        if (!action.attributeValuationMap.actionCount.containsKey(action)) {
            action.attributeValuationMap.actionCount[action] = 0
        }
    }

    fun getAttributeValuationSet(widget: Widget, guiState: State<*>): AttributeValuationMap? {
        if (!guiStates.contains(guiState))
            return null
        val mappedWidget_AttributeValuationSet = AbstractStateManager.instance.activity_widget_AttributeValuationSetHashMap[activity]
        if (mappedWidget_AttributeValuationSet == null)
            return null
        val mappedAttributeValuationSet = mappedWidget_AttributeValuationSet.get(widget)
        if (mappedAttributeValuationSet == null)
            return null
        val attributeValuationSet = attributeValuationMaps.find {
            it.haveTheSameAttributePath(mappedAttributeValuationSet)
        }
        return attributeValuationSet
    }

    fun getAvailableActions(): List<AbstractAction> {
        val allActions = ArrayList<AbstractAction>()
        allActions.addAll(actionCount.keys)
        allActions.addAll(attributeValuationMaps.map { it.actionCount.keys }.flatten())
        //
        return allActions
    }

    fun getUnExercisedActions(currentState: State<*>?): List<AbstractAction> {
        val unexcerisedActions = HashSet<AbstractAction>()
        //use hashmap to optimize the performance of finding widget
        val widget_WidgetGroupMap = HashMap<Widget, AttributeValuationMap>()
        if (currentState != null) {
            Helper.getVisibleWidgetsForAbstraction(currentState).forEach { w ->
                val wg = this.getAttributeValuationSet(w, currentState)
                if (wg != null)
                    widget_WidgetGroupMap.put(w, wg)
            }
        }
        unexcerisedActions.addAll(actionCount.filter {
            ( it.value == 0 )
                    && it.key.actionType != AbstractActionType.FAKE_ACTION
                    && it.key.actionType != AbstractActionType.LAUNCH_APP
                    && it.key.actionType != AbstractActionType.RESET_APP
                    && it.key.actionType != AbstractActionType.ENABLE_DATA
                    && it.key.actionType != AbstractActionType.DISABLE_DATA
                    && it.key.actionType != AbstractActionType.WAIT
                    && it.key.actionType != AbstractActionType.PRESS_BACK

                    && !it.key.isWidgetAction()
        }
                .map { it.key })
        val widgetActionCounts = if (currentState != null) {
            widget_WidgetGroupMap.values.filter { !it.isUserLikeInput() }.distinct()
                    .map { w -> w.actionCount }
        } else {
            attributeValuationMaps
                    .filter { !it.isUserLikeInput() }.map { w -> w.actionCount }
        }
        widgetActionCounts.forEach {
            val actions = it.filter { it.value == 0 || (it.key.attributeValuationMap?.cardinality==Cardinality.MANY && it.value < 2) }.map { it.key }
            unexcerisedActions.addAll(actions)

        }

        val itemActions = attributeValuationMaps.map { w -> w.actionCount.filter { it.key.isItemAction() } }.map { it.keys }.flatten()

        return unexcerisedActions.toList()
    }


    override fun toString(): String {
        return "AbstractState[${this.abstractStateId}]-${window}-Rotation:$rotation"
    }


    fun setActionCount(action: AbstractAction, count: Int) {
        if (action.attributeValuationMap == null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = count
            }
            return
        }
        if (action.attributeValuationMap.actionCount.containsKey(action)) {
            action.attributeValuationMap.actionCount[action] = count
        }
    }

    fun increaseActionCount(action: AbstractAction, updateSimilarAbstractState: Boolean = false) {
        if (action.attributeValuationMap == null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = actionCount[action]!! + 1
            } else {
                actionCount[action] = 1
            }
            val nonDataAction = AbstractAction(
                    actionType = action.actionType
            )
            if (actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                actionCount[nonDataAction] = actionCount[nonDataAction]!! + 1
            }
        } else if (attributeValuationMaps.contains(action.attributeValuationMap)) {
            val widgetGroup = attributeValuationMaps.find { it.equals(action.attributeValuationMap) }!!
            if (widgetGroup.actionCount.containsKey(action)) {
                widgetGroup.actionCount[action] = widgetGroup.actionCount[action]!! + 1
                widgetGroup.exerciseCount++
            } /*else {
                widgetGroup.actionCount[action] = 1
            }*/
            val nonDataAction = AbstractAction(
                    actionType = action.actionType,
                    attributeValuationMap = widgetGroup
            )
            if (widgetGroup.actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                widgetGroup.actionCount[nonDataAction] = widgetGroup.actionCount[nonDataAction]!! + 1
            }
        }
        if (action.isWidgetAction()) {
            val virtualAbstractState = AbstractStateManager.instance.ABSTRACT_STATES
                    .find { it.window == this.window && it is VirtualAbstractState }
            if (virtualAbstractState!=null) {
                if (!virtualAbstractState.attributeValuationMaps.contains(action.attributeValuationMap!!)) {
                    virtualAbstractState.attributeValuationMaps.add(action.attributeValuationMap!!)
                    virtualAbstractState.addAction(action)
                }
            }
        }
      /*  if (updateSimilarAbstractState && !action.isWidgetAction()) {
            increaseSimilarActionCount(action)
        }*/
    }

    fun increaseSimilarActionCount(action: AbstractAction) {
        AbstractStateManager.instance.ABSTRACT_STATES
                .filter { it.window == this.window && it!=this}
                .forEach {
                    if (it.getAvailableActions().contains(action)) {
                        it.increaseActionCount(action)
                    }
                }
    }

    fun getActionCount(action: AbstractAction): Int {
        if (action.attributeValuationMap == null) {
            return actionCount[action] ?: 0
        }
        return action.attributeValuationMap.actionCount[action] ?: 0
    }

    fun getActionCountMap(): Map<AbstractAction, Int> {
        val result = HashMap<AbstractAction, Int>()
        result.putAll(actionCount)
        attributeValuationMaps.map { it.actionCount }.forEach {
            result.putAll(it)
        }
        return result
    }

    fun computeScore(autautMF: ATUAMF): Double {
        var localScore = 0.0
        val unexploredActions = getUnExercisedActions(null).filterNot { it.attributeValuationMap == null }
        val windowWidgetFrequency = AbstractStateManager.instance.attrValSetsFrequency[this.window]!!
        unexploredActions.forEach {
            val actionScore = it.getScore()
            if (windowWidgetFrequency.containsKey(it.attributeValuationMap!!))
                localScore += (actionScore / windowWidgetFrequency[it.attributeValuationMap!!]!!.toDouble())
            else
                localScore += actionScore
        }
        localScore += this.getActionCountMap().map { it.value }.sum()
        this.guiStates.forEach {
            localScore += autautMF.getUnexploredWidget(it).size
        }
        /* actions.forEach {action ->
             autautMF.abstractTransitionGraph.edges(this).filter { edge->
                     edge.label.abstractAction == action
                             && edge.source != edge.destination
                             && edge.destination?.data !is VirtualAbstractState
                 }.forEach { edge ->
                     val dest = edge.destination?.data
                     if (dest != null) {
                         val widgetGroupFrequences = AbstractStateManager.instance.widgetGroupFrequency[dest.window]!!
                         val potentialActions = dest.getUnExercisedActions(null).filterNot { it.widgetGroup == null }
                         potentialActions.forEach {
                             if (widgetGroupFrequences.containsKey(it.widgetGroup))
                                 potentialActionCount += (1/widgetGroupFrequences[it.widgetGroup]!!.toDouble())
                             else
                                 potentialActionCount += 1
                         }
                         potentialActionCount += potentialActions.size
                     }
                 }
         }*/
        return  localScore
    }

    /**
     * write csv
     * uuid -> AbstractState_[uuid]
     */
    open fun dump(parentDirectory: Path) {
        val dumpedAttributeValuationSet = ArrayList<String>()
        File(parentDirectory.resolve("AbstractState_" + abstractStateId.toString() + ".csv").toUri()).bufferedWriter().use { all ->
            val header = header()
            all.write(header)
            attributeValuationMaps.forEach {
                if (!dumpedAttributeValuationSet.contains(it.avmId)) {
                    all.newLine()
                    it.dump(all, dumpedAttributeValuationSet,this)
                }
            }
        }
    }

    fun header(): String {
        return "AttributeValuationSetID;parentAVMID;${localAttributesHeader()};cardinality;captured;wtgWidgetMapping;hashcode"
    }

    private fun localAttributesHeader(): String {
        var result = ""
        AttributeType.values().toSortedSet().forEach {
            result+=it.toString()
            result+=";"
        }
        result = result.substring(0,result.length-1)
        return result
    }

    fun belongToAUT(): Boolean {
        return !this.isHomeScreen && !this.isOutOfApplication && !this.isRequestRuntimePermissionDialogBox && !this.isAppHasStoppedDialogBox
    }

    companion object {
        val abstractStateIdByWindow = HashMap<Window, Int>()
        fun computeAbstractStateHashCode(attributeValuationMaps: List<AttributeValuationMap>, activity: String, rotation: Rotation, internet: InternetStatus): Int {
            return attributeValuationMaps.sortedBy { it.avmId }.fold(emptyUUID) { id, avs ->
                /*// e.g. keyboard elements are ignored for uid computation within [addRelevantId]
                // however different selectable auto-completion proposes are only 'rendered'
                // such that we have to include the img id (part of configId) to ensure different state configuration id's if these are different*/

                //ConcreteId(addRelevantId(id, widget), configId + widget.uid + widget.id.configId)
                id + avs.hashCode.toUUID()
            }.plus(listOf<String>(rotation.toString(),internet.toString(),activity).joinToString("<;>").toUUID()).hashCode()
        }

        internal operator fun UUID.plus(uuid: UUID?): UUID {
            return if (uuid == null) this
            else UUID(this.mostSignificantBits + uuid.mostSignificantBits, this.leastSignificantBits + uuid.mostSignificantBits)
        }

    }
}

enum class InternetStatus {
    Enable,
    Disable,
    Undefined
}

class VirtualAbstractState(activity: String,
                           staticNode: Window,
                           isHomeScreen: Boolean = false) : AbstractState(
        activity = activity,
        window = staticNode,
        rotation = Rotation.PORTRAIT,
        internet = InternetStatus.Undefined,
        isHomeScreen = isHomeScreen,
        isOutOfApplication = (staticNode is OutOfApp)
)

class AppResetAbstractState() : AbstractState(activity = "", window = Launcher.getOrCreateNode(), rotation = Rotation.PORTRAIT, internet = InternetStatus.Undefined)