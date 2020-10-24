package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGActivityNode(classType: String,
                      nodeId: String = getNodeId(),
                      fromModel: Boolean):WTGNode(classType,nodeId,fromModel){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
        activityClass = classType
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][Activity]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGActivityNode>()
        fun getNodeId(): String = "Activity-${counter++}"
        fun getOrCreateNode(nodeId: String, classType: String,fromModel: Boolean=true): WTGActivityNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGActivityNode(nodeId = nodeId,classType = classType,fromModel = fromModel)
        }
    }
}