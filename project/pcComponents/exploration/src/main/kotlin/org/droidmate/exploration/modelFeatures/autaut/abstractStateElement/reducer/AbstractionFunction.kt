package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.LocalReducerLV0
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.LocalReducerLV1
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.LocalReducerLV2
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class AbstractionFunction (val root: DecisionNode) {
    val abandonedAttributePaths: HashSet<Pair<AttributePath, AbstractInteraction>> = HashSet()
    fun reduce(guiWidget: Widget, guiState: State<*>,activity: String, tempWidgetReduceMap: HashMap<Widget,AttributePath> = HashMap()
    , tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath{
        var currentDecisionNode: DecisionNode?=null
        var attributePath: AttributePath
        do {
            if (currentDecisionNode==null)
                currentDecisionNode = root
            else
                currentDecisionNode = currentDecisionNode.nextNode
            attributePath = currentDecisionNode!!.reducer.reduce(guiWidget, guiState,activity, tempWidgetReduceMap,tempChildWidgetAttributePaths)
        }
        while (currentDecisionNode!!.nextNode!=null
                && currentDecisionNode.nextNode!!.attributePaths.contains(Pair(attributePath,activity)))
        return attributePath
    }

    /**
     * Increase the level of Reducer. Return [true] if it can be increased, otherwise [false]
     */
    fun increaseReduceLevel(attributePath: AttributePath, activity: String, level2Maximum: Boolean): Boolean
    {
        var currentDecisionNode: DecisionNode? = root
        var tempAttributePath: AttributePath
        var level = 1
        do {
            if (level2Maximum && level == 2)
                break
            currentDecisionNode = currentDecisionNode!!.nextNode
            level++
        }while (currentDecisionNode!=null && currentDecisionNode!!.attributePaths.contains(Pair(attributePath,activity)))
        if (currentDecisionNode!=null) {
            currentDecisionNode!!.attributePaths.add(Pair(attributePath,activity))
            return true
        }
        else {

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
        fun backup(){
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

        fun restore(){
            if (backupAbstractionFunction!=null)
            {
                INSTANCE = backupAbstractionFunction!!
            }
        }
    }
}