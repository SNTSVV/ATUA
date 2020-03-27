package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGOpeningKeyboardNode(nodeId: String= getNodeId(),
                             wtgRelatedNode: WTGNode):WTGAppStateNode(nodeId=nodeId, wtgRelatedNode = wtgRelatedNode){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
    }

    override fun toString(): String {
        return "[OpeningKeyboardNode]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGOpeningKeyboardNode>()
        fun getNodeId(): String = "OpeningKeyboardNode-${counter++}"
        fun getOrCreateNode(nodeId: String = getNodeId(), wtgRelatedNode: WTGNode): WTGOpeningKeyboardNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGOpeningKeyboardNode(nodeId = nodeId,wtgRelatedNode = wtgRelatedNode)
        }
    }
}