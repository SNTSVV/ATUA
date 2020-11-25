package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.LocalReducerLV1
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.LocalReducerLV2
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.LocalReducerLV3
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class AbstractionFunction (val root: DecisionNode) {
    val abandonedAttributePaths: HashSet<Triple<String, AttributeValuationSet, AbstractActionType>> = HashSet()

    fun isAbandonedAttributePath(activity: String, attributeValuationSet: AttributeValuationSet, action: AbstractActionType):Boolean {
        return abandonedAttributePaths.filter { it.first == activity && it.third == action }.any {
            attributeValuationSet.isDerivedFrom(it.second)
        }
    }
    fun reduce(guiWidget: Widget, guiState: State<*>,activity: String, tempWidgetReduceMap: HashMap<Widget,AttributePath> = HashMap()
    , tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath{
        var currentDecisionNode: DecisionNode?=null
        var attributePath: AttributePath
        var level = 0
        do {
            tempWidgetReduceMap.remove(guiWidget)
            //tempChildWidgetAttributePaths.clear()
            if (currentDecisionNode==null)
                currentDecisionNode = root
            else
                currentDecisionNode = currentDecisionNode.nextNode
            level += 1
            attributePath = currentDecisionNode!!.reducer.reduce(guiWidget, guiState,activity, tempWidgetReduceMap,tempChildWidgetAttributePaths)
        }
        while (currentDecisionNode!!.nextNode!=null
                && currentDecisionNode.containAttributePath(attributePath,activity))
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
            currentDecisionNode.attributePaths.add(Pair(attributePath,activity))
           /* val newAttributePath = reduce(guiWidget,guiState,activity, hashMapOf(), hashMapOf())
            updateAllAttributePathsHavingParent(attributePath,newAttributePath,activity)*/
            return true
        }
        else {
            if (attributePath.parentAttributePath == null)
                return false
            var parentAttributePath = attributePath.parentAttributePath
            var parentWidget: Widget? = guiState.widgets.find { it.idHash == guiWidget.parentHash }
            /*while (parentAttributePath!!.parentAttributePath != null && parentWidget!=null) {
                parentAttributePath = parentAttributePath!!.parentAttributePath
                parentWidget = guiState.widgets.find { it.id == parentWidget!!.parentId }
            }*/
            if (parentWidget!=null) {
                //increaseReduceLevel for parent
                val result = increaseReduceLevel(parentAttributePath, activity, false, parentWidget, guiState)
                return result
            }
            return false
        }
    }

    private fun updateAllAttributePathsHavingParent(oldParentAttributePath: AttributePath, newParentAttributePath: AttributePath, activity: String) {
        var currentDecisionNode: DecisionNode? = root
        do {
            val oldAttributePaths =currentDecisionNode!!.attributePaths.filter { it.second == activity && it.first.parentAttributePath == oldParentAttributePath }
            oldAttributePaths.forEach {
                val newAttributePath = AttributePath(localAttributes = it.first.localAttributes,
                        parentAttributePath = newParentAttributePath,
                        childAttributePaths = it.first.childAttributePaths)
                currentDecisionNode!!.attributePaths.add(Pair(newAttributePath,activity))
                currentDecisionNode!!.attributePaths.remove(it)
                updateAllAttributePathsHavingParent(it.first,newAttributePath,activity)
            }
            currentDecisionNode = currentDecisionNode.nextNode
        } while (currentDecisionNode!=null)
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

            abandonedAttributePaths.forEach {tripple->
                all.newLine()
                all.write("${tripple.first};${tripple.second.avsId};${tripple.third}")
            }
        }
    }

    private fun dumpDecisionNode(parentDirectory: Path, level: Int, currentDecisionNode: DecisionNode?) {
        File(parentDirectory.resolve("DecisionNode_LV${level}.csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            val dumpedAttributeValuationSet = ArrayList<Pair<UUID, String>>()
            currentDecisionNode!!.attributePaths.forEach {
                if (!dumpedAttributeValuationSet.contains(Pair(it.first.attributePathId, it.second))) {
                    it.first.dump(activity = it.second, dumpedAttributeValuationSets = dumpedAttributeValuationSet, bufferedWriter = all)
                }
            }
        }
    }

    private fun header(): String  {
        return "Activity;AttributeValuationSetID;className;resourceId;contentDesc;text;enabled;selected;checkable;isInputField;clickable;longClickable;scrollable;checked;isLeaf;parentId;childId"
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
            INSTANCE = AbstractionFunction(root)
        }
        private var backupAbstractionFunction: AbstractionFunction? = null
        //val backupAbstractStateList = ArrayList<Pair<State<*>,AbstractState>>()
        fun backup( autMF: AutAutMF){
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
            backupAbstractionFunction = AbstractionFunction(root)
            var decisionNode: DecisionNode? = INSTANCE.root
            var backupDecisionNode: DecisionNode? = backupAbstractionFunction!!.root
            while (decisionNode!=null && backupDecisionNode!=null)
            {
                backupDecisionNode.attributePaths.addAll(decisionNode.attributePaths)
                decisionNode = decisionNode!!.nextNode
                backupDecisionNode = backupDecisionNode!!.nextNode
            }
        }

        fun restore(autMF: AutAutMF){
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
            val attributeValuationSet: AttributeValuationSet,
            val abstractActionType: AbstractActionType,
            val resAbstractStates: List<AbstractState>
    )
}