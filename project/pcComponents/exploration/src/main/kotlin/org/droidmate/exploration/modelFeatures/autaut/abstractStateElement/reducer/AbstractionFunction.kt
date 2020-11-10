package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.LocalReducerLV0
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.LocalReducerLV1
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.LocalReducerLV2
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
import org.droidmate.exploration.strategy.autaut.task.GoToAnotherWindow
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class AbstractionFunction (val root: DecisionNode) {
    val abandonedAttributePaths: HashSet<Pair<AttributePath, AbstractActionType>> = HashSet()
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


    companion object{
        var INSTANCE: AbstractionFunction
        init {
            val root = DecisionNode(reducer = BaseReducer(localReducer = LocalReducerLV1() ))
            val lv2Node = DecisionNode(reducer = BaseReducer(localReducer = LocalReducerLV2()))
            root.nextNode = lv2Node
            val lv3Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV2(),childrenReducer = LocalReducerLV0()))
            lv2Node.nextNode = lv3Node
            val lv4Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV2(),childrenReducer = LocalReducerLV1()))
            lv3Node.nextNode = lv4Node
            val lv5Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV2(),childrenReducer = LocalReducerLV2()))
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
            val lv3Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV2(),childrenReducer = LocalReducerLV0()))
            lv2Node.nextNode = lv3Node
            val lv4Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV2(),childrenReducer = LocalReducerLV1()))
            lv3Node.nextNode = lv4Node
            val lv5Node = DecisionNode(reducer = IncludeChildrenReducer(localReducer = LocalReducerLV2(),childrenReducer = LocalReducerLV2()))
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
}