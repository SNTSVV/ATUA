package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGOptionsMenuNode (classType: String,
                          nodeId: String = getNodeId(),
                          fromModel: Boolean):WTGNode(classType,nodeId,fromModel){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][OptionsMenu]-${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGOptionsMenuNode>()
        fun getNodeId(): String = "OptionsMenu-${counter++}"
        fun getOrCreateNode(nodeId: String, classType: String,fromModel: Boolean=true): WTGOptionsMenuNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGOptionsMenuNode(nodeId = nodeId,classType = classType,fromModel = fromModel)
        }
    }
}