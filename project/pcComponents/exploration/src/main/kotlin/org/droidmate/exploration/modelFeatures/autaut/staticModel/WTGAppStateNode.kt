package org.droidmate.exploration.modelFeatures.regression.staticModel



open class WTGAppStateNode(nodeId: String= getNodeId(),
                      val wtgRelatedNode: WTGNode):WTGNode(wtgRelatedNode.classType,nodeId){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
    }

    override fun toString(): String {
        return "[AppState]${wtgRelatedNode.classType}-$nodeId"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGAppStateNode>()
        fun getNodeId(): String = "AP-${counter++}"
        fun getOrCreateNode(nodeId: String, wtgActivityNode: WTGNode): WTGAppStateNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGAppStateNode(nodeId = nodeId, wtgRelatedNode = wtgActivityNode)
        }
    }
}