package org.droidmate.exploration.modelFeatures.regression.staticModel

class WTGOutScopeNode(nodeId: String= getNodeId()):WTGNode("",nodeId){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
    }

    override fun toString(): String {
        return "[OutScope]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGOutScopeNode>()
        fun getNodeId(): String = "OutScopeNode-${counter++}"
        fun getOrCreateNode(nodeId: String = getNodeId()): WTGOutScopeNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGOutScopeNode(nodeId = nodeId)
        }
    }
}