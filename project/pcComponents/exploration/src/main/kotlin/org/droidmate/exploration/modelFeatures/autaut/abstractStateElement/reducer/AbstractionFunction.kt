package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
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
    val abandonedAttributePaths: HashSet<Pair<AttributePath, AbstractInteraction>> = HashSet()
    fun reduce(guiWidget: Widget, guiState: State<*>,activity: String, tempWidgetReduceMap: HashMap<Widget,AttributePath> = HashMap()
    , tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath{
        var currentDecisionNode: DecisionNode?=null
        var attributePath: AttributePath
        do {
            tempWidgetReduceMap.remove(guiWidget)
            tempChildWidgetAttributePaths.clear()
            if (currentDecisionNode==null)
                currentDecisionNode = root
            else
                currentDecisionNode = currentDecisionNode.nextNode
            attributePath = currentDecisionNode!!.reducer.reduce(guiWidget, guiState,activity, tempWidgetReduceMap,tempChildWidgetAttributePaths)
        }
        while (currentDecisionNode!!.nextNode!=null
                && currentDecisionNode.nextNode!!.containAttributePath(attributePath,activity))
        return attributePath
    }

    /**
     * Increase the level of Reducer. Return [true] if it can be increased, otherwise [false]
     */
    fun increaseReduceLevel(attributePath: AttributePath, activity: String, level2Maximum: Boolean,guiWidget: Widget, guiState: State<*>): Boolean
    {
        var currentDecisionNode: DecisionNode? = root
        var tempAttributePath: AttributePath
        var prevDecisionNode: DecisionNode? = null
        var level = 1
        do {
            if (level2Maximum && level == 2)
                break
            prevDecisionNode = currentDecisionNode
            currentDecisionNode = currentDecisionNode!!.nextNode
            level++
        }while (currentDecisionNode!=null && currentDecisionNode!!.attributePaths.filter { it.second == activity }. any { attributePath.contains(it.first) })
        if (currentDecisionNode!=null) {
            currentDecisionNode!!.attributePaths.add(Pair(attributePath,activity))
            return true
        }
        else {
            if (attributePath.parentAttributePath!=null && guiWidget.parentId!=null) {
                //increaseReduceLevel for parent
                val parentWidget = guiState.widgets.find { it.id == guiWidget.parentId }
                if (parentWidget!=null) {
                    val result = increaseReduceLevel(attributePath.parentAttributePath, activity, level2Maximum, parentWidget, guiState)
                   /*if (result == true) {
                        val newParrentAttributePath = reduce(parentWidget, guiState, activity, hashMapOf(), hashMapOf())
                        val newAttributePath = AttributePath(localAttributes = attributePath.localAttributes,
                                parentAttributePath = newParrentAttributePath,
                                childAttributePaths = attributePath.childAttributePaths)
                        prevDecisionNode!!.attributePaths.add(Pair(newAttributePath, activity))
                    }*/
                    return result
                }
            }
            return false
        }
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