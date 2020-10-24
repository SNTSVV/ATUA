package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGContextMenuNode (classType: String,
                          nodeId: String= getNodeId(),
                          fromModel:Boolean):WTGNode(classType,nodeId,fromModel){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][ContextMenu]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGContextMenuNode>()
        fun getNodeId(): String = "ContextMenuNode-${counter++}"
        fun getOrCreateNode(nodeId: String, classType: String, fromModel: Boolean=true): WTGContextMenuNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGContextMenuNode(nodeId = nodeId,classType = classType,fromModel = fromModel )
        }
    }
}