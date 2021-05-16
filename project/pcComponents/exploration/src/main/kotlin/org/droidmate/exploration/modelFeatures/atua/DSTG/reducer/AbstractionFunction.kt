package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.localReducer.LocalReducerLV1
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.localReducer.LocalReducerLV2
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.localReducer.LocalReducerLV3
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AbstractionFunction (val root: DecisionNode) {
    val abandonedAbstractTransitions: ArrayList<AbstractTransition> = ArrayList()

    fun isAbandonedAttributePath(activity: String, abstractTransition: AbstractTransition):Boolean {
        return abandonedAbstractTransitions.filter { it.source.activity == activity }.any {
            abstractTransition.abstractAction == it.abstractAction &&
            abstractTransition.abstractAction.attributeValuationMap?.equals(it.abstractAction.attributeValuationMap!!)?:false
                    && abstractTransition.source.equals(it.source)
                    && abstractTransition.dest.equals(it.dest)
        }
    }

    fun reduce(guiWidget: Widget, guiState: State<*>, activity: String, rotation: Rotation, autaut:ATUAMF, tempWidgetReduceMap: HashMap<Widget,AttributePath> = HashMap()
               , tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath{
        val guiTreeRectangle = Helper.computeGuiTreeDimension(guiState)
        var isOptionsMenu = if (!Helper.isDialog(rotation,guiTreeRectangle, guiState, autaut))
                Helper.isOptionsMenuLayout(guiState)
        else
            false
        var currentDecisionNode: DecisionNode?=null
        var attributePath: AttributePath
        var level = 0
        do {
            tempWidgetReduceMap.remove(guiWidget)
            //tempChildWidgetAttributePaths.clear()
            level += 1
            if (currentDecisionNode==null)
                currentDecisionNode = root
            else
                currentDecisionNode = currentDecisionNode.nextNode
            attributePath = currentDecisionNode!!.reducer.reduce(guiWidget, guiState,activity,rotation,autaut, tempWidgetReduceMap,tempChildWidgetAttributePaths)
        }
        while (currentDecisionNode!!.nextNode!=null
                && currentDecisionNode.containAttributePath(attributePath,activity))
        if (level==1 && isOptionsMenu) {
            if (!currentDecisionNode.attributePaths.containsKey(activity)) {
                currentDecisionNode.attributePaths.put(activity, arrayListOf())
            }
            currentDecisionNode!!.attributePaths.get(activity)!!.add(attributePath)
            attributePath = currentDecisionNode!!.nextNode!!.reducer.reduce(guiWidget, guiState, activity,rotation,autaut, tempWidgetReduceMap, tempChildWidgetAttributePaths)
        }
        return attributePath
    }

    /**
     * Increase the level of Reducer. Return [true] if it can be increased, otherwise [false]
     */
    fun increaseReduceLevel(attributePath: AttributePath, activity: String, level2Maximum: Boolean,guiWidget: Widget, guiState: State<*>): Boolean
    {
        var currentDecisionNode: DecisionNode? = null
        var level = 1
        do {
            if (level2Maximum && level == 2)
                break
            if (currentDecisionNode==null)
                currentDecisionNode = root
            else
                currentDecisionNode = currentDecisionNode.nextNode
            level++
        }while (currentDecisionNode!!.nextNode!=null && currentDecisionNode.containAttributePath(attributePath, activity))
        if (!currentDecisionNode!!.containAttributePath(attributePath, activity)) {
            if (!currentDecisionNode.attributePaths.containsKey(activity)) {
                currentDecisionNode.attributePaths.put(activity, arrayListOf())
            }
            currentDecisionNode.attributePaths.get(activity)!!.add(attributePath)
           /* val newAttributePath = reduce(guiWidget,guiState,activity, hashMapOf(), hashMapOf())
            updateAllAttributePathsHavingParent(attributePath,newAttributePath,activity)*/
            return true
        }
        return false
/*       else {
            if (attributePath.parentAttributePathId == emptyUUID)
                return false
            var parentAttributePath = AttributePath.getAttributePathById(attributePath.parentAttributePathId,activity)
            var parentWidget: Widget? = guiState.widgets.find { it.idHash == guiWidget.parentHash }
            *//*while (parentAttributePath!!.parentAttributePath != null && parentWidget!=null) {
                parentAttributePath = parentAttributePath!!.parentAttributePath
                parentWidget = guiState.widgets.find { it.id == parentWidget!!.parentId }
            }*//*
            if (parentWidget!=null) {
                //increaseReduceLevel for parent
                val result = increaseReduceLevel(parentAttributePath, activity, false, parentWidget, guiState)
                return result
            }
            return false
        }*/
    }


    fun dump(dstgFolder: Path) {
        val parentDirectory = Files.createDirectory(dstgFolder.resolve("AbstractionFunction"))
        var level = 1
        var currentDecisionNode: DecisionNode? = root
        do {
            dumpDecisionNode(parentDirectory, level, currentDecisionNode)
            currentDecisionNode = currentDecisionNode!!.nextNode
            level++
        } while (currentDecisionNode != null)
        File(parentDirectory.resolve("abandonedAttributeValuationSet.csv").toUri()).bufferedWriter().use { all ->
            all.write("Activity;AttributeValuationSetID;Action Type")

            abandonedAbstractTransitions.forEach { tripple->
                all.newLine()
                all.write("${tripple.source.activity};${tripple.abstractAction.attributeValuationMap!!.avmId};${tripple.abstractAction.actionType}")
            }
        }
    }

    private fun dumpDecisionNode(parentDirectory: Path, level: Int, currentDecisionNode: DecisionNode?) {
        File(parentDirectory.resolve("DecisionNode_LV${level}.csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            val dumpedAttributeValuationSet = ArrayList<Pair<String, UUID>>()
            currentDecisionNode!!.attributePaths.forEach {
                dumpedAttributeValuationSet.clear()
                val activity = it.key
                val captured = it.value
                captured.forEach {
                    if (!dumpedAttributeValuationSet.contains(Pair(activity,it.attributePathId))) {
                        it.dump(activity = activity, dumpedAttributeValuationSets = dumpedAttributeValuationSet, bufferedWriter = all,capturedAttributePaths = captured)
                    }
                }
            }
        }
    }

    private fun header(): String  {
        return "Activity;AttributeValuationSetID;parentAttributeValutionSetID;${localAttributesHeader()}"
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

    companion object{
        var INSTANCE: AbstractionFunction
        init {
            val root = DecisionNode(reducer = BaseReducer(localReducer = LocalReducerLV1() ))
            val lv2Node = DecisionNode(reducer = BaseReducer(localReducer = LocalReducerLV2()))
            root.nextNode = lv2Node
            val lv3Node = DecisionNode(reducer = BaseReducer(localReducer = LocalReducerLV3()))
            lv2Node.nextNode = lv3Node
            val lv4Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV1()))
            lv3Node.nextNode = lv4Node
            val lv5Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV2()))
            lv4Node.nextNode = lv5Node
            val lv6Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV3()))
            lv5Node.nextNode = lv6Node
            INSTANCE = AbstractionFunction(root)
        }
        private var backupAbstractionFunction: AbstractionFunction? = null
        //val backupAbstractStateList = ArrayList<Pair<State<*>,AbstractState>>()
        fun backup( autMF: ATUAMF){
            //backupAbstractStateList.clear()
            //backupAbstractStateList.addAll(autMF.abstractStateList)

            val root = DecisionNode(reducer = BaseReducer(localReducer = LocalReducerLV1() ))
            val lv2Node = DecisionNode(reducer = BaseReducer(localReducer = LocalReducerLV2()))
            root.nextNode = lv2Node
            val lv3Node = DecisionNode(reducer = BaseReducer(localReducer = LocalReducerLV3()))
            lv2Node.nextNode = lv3Node
            val lv4Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV1()))
            lv3Node.nextNode = lv4Node
            val lv5Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV2()))
            lv4Node.nextNode = lv5Node
            val lv6Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV3()))
            lv5Node.nextNode = lv6Node
            backupAbstractionFunction = AbstractionFunction(root)
            var decisionNode: DecisionNode? = INSTANCE.root
            var backupDecisionNode: DecisionNode? = backupAbstractionFunction!!.root
            while (decisionNode!=null && backupDecisionNode!=null)
            {
                decisionNode.attributePaths.forEach { t, u ->
                    backupDecisionNode!!.attributePaths.put(t, ArrayList(u))
                }
                decisionNode = decisionNode.nextNode
                backupDecisionNode = backupDecisionNode.nextNode
            }
        }

        fun restore(autMF: ATUAMF){
            if (backupAbstractionFunction!=null)
            {
                INSTANCE = backupAbstractionFunction!!
                backupAbstractionFunction = null
            }
           /* if (backupAbstractStateList.isNotEmpty()) {
                autMF.abstractStateList.clear()
                autMF.abstractStateList.addAll(backupAbstractStateList)
            }*/
        }
    }

    class AbandonedTransition (
            val activity: String,
            val attributeValuationMap: AttributeValuationMap,
            val abstractActionType: AbstractActionType,
            val resAbstractStates: List<AbstractState>
    )
}