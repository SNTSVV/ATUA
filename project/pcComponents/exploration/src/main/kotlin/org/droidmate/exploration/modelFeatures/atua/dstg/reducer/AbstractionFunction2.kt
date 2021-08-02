package org.droidmate.exploration.modelFeatures.atua.dstg.reducer


import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributeType
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer.LocalReducerLV1
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer.LocalReducerLV2
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer.LocalReducerLV3
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributePath
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.ewtg.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.ewtg.Helper
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AbstractionFunction2 (val root: DecisionNode2) {
    val abandonedAbstractTransitions: ArrayList<AbstractTransition> = ArrayList()

    fun isAbandonedAbstractTransition(activity: String, abstractTransition: AbstractTransition):Boolean {
        return abandonedAbstractTransitions.filter { it.source.activity == activity }.any {
            abstractTransition.abstractAction == it.abstractAction
                    && ((!abstractTransition.abstractAction.isWidgetAction() && !it.abstractAction.isWidgetAction())
                            || abstractTransition.abstractAction.attributeValuationMap!!.isDerivedFrom(it.abstractAction.attributeValuationMap!!))
                    && abstractTransition.source.equals(it.source)
                    && abstractTransition.dest.equals(it.dest)
                    /*&& abstractTransition.prevWindow == it.prevWindow*/
        }
        // TODO check
    }

    fun Widget.isInteractiveLeaf(guiState: State<*>): Boolean {
        if (!this.isInteractive)
            return false
        if (this.isLeaf())
            return this.isInteractive
        return this.childHashes.all { childHash->
            val childWidget = guiState.widgets.find { it.idHash == childHash }!!
            !childWidget.isInteractiveLeaf(guiState)
        }
    }

    fun reduce(guiWidget: Widget, guiState: State<*>, ewtgWidget: EWTGWidget?, isOptionsMenu:Boolean,
               guiTreeRectangle: Rectangle, window: Window, rotation: Rotation,
               autaut:ATUAMF, tempWidgetReduceMap: HashMap<Widget,AttributePath> = HashMap(),
               tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath{
        val isInteractiveLeaf = guiWidget.isInteractiveLeaf(guiState)
        var currentDecisionNode: DecisionNode2?=null
        var attributePath: AttributePath? = null
        var level = 0
        do {
            tempWidgetReduceMap.remove(guiWidget)
            //tempChildWidgetAttributePaths.clear()
            level += 1
            if (currentDecisionNode==null)
                currentDecisionNode=root
            else if (currentDecisionNode.nextNode == null)
                break
            else
                currentDecisionNode = currentDecisionNode.nextNode

            if (isOptionsMenu && isInteractiveLeaf && level <= 5) {
               if (ewtgWidget!=null && !currentDecisionNode!!.ewtgWidgets.contains(ewtgWidget))
                   currentDecisionNode!!.ewtgWidgets.add(ewtgWidget)
            }
        }
        while (currentDecisionNode!!.nextNode!=null
                && currentDecisionNode.ewtgWidgets.contains(ewtgWidget))
        attributePath = currentDecisionNode!!.reducer.reduce(guiWidget, guiState,isOptionsMenu,guiTreeRectangle, window,rotation,autaut, tempWidgetReduceMap,tempChildWidgetAttributePaths)
        return attributePath!!
    }

    /**
     * Increase the level of Reducer. Return [true] if it can be increased, otherwise [false]
     */
    fun increaseReduceLevel(guiWidget: Widget, guiState: State<*>, ewtgWidget: EWTGWidget, classType: String, rotation: Rotation, atuaMF: ATUAMF, maxlevel: Int=6): Boolean
    {
        val isInteractiveLeaf = guiWidget.isInteractiveLeaf(guiState)
        var currentDecisionNode: DecisionNode2?=null
        var attributePath: AttributePath? = null
        val guiTreeRectangle = Helper.computeGuiTreeDimension(guiState)
        var isOptionsMenu = if (!Helper.isDialog(rotation,guiTreeRectangle, guiState, atuaMF))
            Helper.isOptionsMenuLayout(guiState)
        else
            false
        val tempWidgetReduceMap = HashMap<Widget,AttributePath>()
        val tempChildWidgetAttributePaths = HashMap<Widget,AttributePath>()
        var level = 0
        do {
            tempWidgetReduceMap.remove(guiWidget)
            //tempChildWidgetAttributePaths.clear()
            level += 1
            if (currentDecisionNode==null)
                currentDecisionNode=root
            else if (currentDecisionNode.nextNode == null)
                break
            else
                currentDecisionNode = currentDecisionNode.nextNode
        }
        while (currentDecisionNode!!.nextNode!=null
                && currentDecisionNode.ewtgWidgets.contains(ewtgWidget) && level <= maxlevel)
        if (currentDecisionNode!=null && !currentDecisionNode!!.ewtgWidgets.contains(ewtgWidget)) {
            currentDecisionNode.ewtgWidgets.add(ewtgWidget)
           /* val newAttributePath = reduce(guiWidget,guiState,classType, hashMapOf(), hashMapOf())
            updateAllAttributePathsHavingParent(attributePath,newAttributePath,classType)*/
            return true
        }
        return false
/*       else {
            if (attributePath.parentAttributePathId == emptyUUID)
                return false
            var parentAttributePath = AttributePath.getAttributePathById(attributePath.parentAttributePathId,classType)
            var parentWidget: Widget? = guiState.widgets.find { it.idHash == guiWidget.parentHash }
            *//*while (parentAttributePath!!.parentAttributePath != null && parentWidget!=null) {
                parentAttributePath = parentAttributePath!!.parentAttributePath
                parentWidget = guiState.widgets.find { it.id == parentWidget!!.parentId }
            }*//*
            if (parentWidget!=null) {
                //increaseReduceLevel for parent
                val result = increaseReduceLevel(parentAttributePath, classType, false, parentWidget, guiState)
                return result
            }
            return false
        }*/
    }


    fun dump(dstgFolder: Path) {
        val parentDirectory = Files.createDirectory(dstgFolder.resolve("AbstractionFunction"))
        var level = 1
        var currentDecisionNode: DecisionNode2? = root
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

    private fun dumpDecisionNode(parentDirectory: Path, level: Int, currentDecisionNode: DecisionNode2?) {
        File(parentDirectory.resolve("DecisionNode_LV${level}.csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            val dumpedAttributeValuationSet = ArrayList<Pair<String, UUID>>()
            currentDecisionNode!!.ewtgWidgets.forEach {
                all.newLine()
                all.write("${it.widgetId};${it.resourceIdName};${it.className};${it.parent?.widgetId};${it.window.classType};${it.createdAtRuntime}")
            }
        }
    }

    private fun header(): String  {
        return "[1]widgetId;[2]resourceIdName;[3]className;[4]parent;[5]activity;[6]createdAtRuntime"
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
        var INSTANCE: AbstractionFunction2
        init {
            val root = DecisionNode2(reducer = BaseReducer(localReducer = LocalReducerLV1() ))
            val lv2Node = DecisionNode2(reducer = BaseReducer(localReducer = LocalReducerLV2()))
            root.nextNode = lv2Node
            val lv3Node = DecisionNode2(reducer = BaseReducer(localReducer = LocalReducerLV3()))
            lv2Node.nextNode = lv3Node
            val lv4Node = DecisionNode2(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV1()))
            lv3Node.nextNode = lv4Node
            val lv5Node = DecisionNode2(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV2()))
            lv4Node.nextNode = lv5Node
            val lv6Node = DecisionNode2(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV3()))
            lv5Node.nextNode = lv6Node
            INSTANCE = AbstractionFunction2(root)
        }
        private var backupAbstractionFunction: AbstractionFunction2? = null
        //val backupAbstractStateList = ArrayList<Pair<State<*>,AbstractState>>()
        fun backup( autMF: ATUAMF){
            //backupAbstractStateList.clear()
            //backupAbstractStateList.addAll(autMF.abstractStateList)

            val root = DecisionNode2(reducer = BaseReducer(localReducer = LocalReducerLV1()))
            val lv2Node = DecisionNode2(reducer = BaseReducer(localReducer = LocalReducerLV2()))
            root.nextNode = lv2Node
            val lv3Node = DecisionNode2(reducer = BaseReducer(localReducer = LocalReducerLV3()))
            lv2Node.nextNode = lv3Node
            val lv4Node = DecisionNode2(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV1()))
            lv3Node.nextNode = lv4Node
            val lv5Node = DecisionNode2(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV2()))
            lv4Node.nextNode = lv5Node
            val lv6Node = DecisionNode2(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV3(),childrenReducer = LocalReducerLV3()))
            lv5Node.nextNode = lv6Node
            backupAbstractionFunction = AbstractionFunction2(root)
            var decisionNode: DecisionNode2? = INSTANCE.root
            var backupDecisionNode: DecisionNode2? = backupAbstractionFunction!!.root
            while (decisionNode!=null && backupDecisionNode!=null)
            {
                decisionNode.ewtgWidgets.forEach {
                    backupDecisionNode!!.ewtgWidgets.add(it)
                }
                decisionNode = decisionNode.nextNode
                backupDecisionNode = backupDecisionNode.nextNode
            }
            backupAbstractionFunction!!.abandonedAbstractTransitions.addAll(INSTANCE.abandonedAbstractTransitions)
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