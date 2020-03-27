package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGFakeNode(nodeId: String= getNodeId()):WTGNode("",nodeId){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
    }

    override fun toString(): String {
        return "[FakeNode]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGFakeNode>()
        fun getNodeId(): String = "FakeNode-${counter++}"
        fun getOrCreateNode(nodeId: String= getNodeId()): WTGFakeNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGFakeNode(nodeId = nodeId)
        }
    }
}