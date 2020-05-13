package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGDialogNode(classType: String,
                    nodeId: String= getNodeId()):WTGNode(classType,nodeId){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
    }

    override fun isStatic(): Boolean {
        return true
    }
    override fun toString(): String {
        return "[Window][Dialog]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGDialogNode>()
        fun getNodeId(): String = "Dialog-${counter++}"
        fun getOrCreateNode(nodeId: String, classType: String): WTGDialogNode{
            val node = allNodes.find { it.nodeId == nodeId }
            if (node != null)
                return node!!
            else
                return WTGDialogNode(nodeId = nodeId,classType = classType)
        }
    }
}