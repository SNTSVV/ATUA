package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGActivityNode(classType: String,
                      nodeId: String = getNodeId()):WTGNode(classType,nodeId){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[AppState][Initial]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGActivityNode>()
        fun getNodeId(): String = "ActivityNode-${counter++}"
        fun getOrCreateNode(nodeId: String, classType: String): WTGActivityNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGActivityNode(nodeId = nodeId,classType = classType)
        }
    }
}