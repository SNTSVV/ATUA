package org.droidmate.exploration.modelFeatures.autaut.staticModel

class WTGOutScopeNode(nodeId: String= getNodeId(), activity: String):WTGNode(activity,nodeId){
    init {
        allNodes.add(this)
        WTGNode.allNodes.add(this)
        this.activityClass = activity
    }

    override fun toString(): String {
        return "[OutScope]${super.toString()}"
    }
    companion object{
        var counter = 0
        val allNodes = ArrayList<WTGOutScopeNode>()
        fun getNodeId(): String = "OutScopeNode-${counter++}"
        fun getOrCreateNode(activity: String): WTGOutScopeNode{
            val node = allNodes.find { it.activityClass == activity }
            if (node != null)
                return node!!
            else
                return WTGOutScopeNode(nodeId = getNodeId(), activity = activity)
        }
    }
}