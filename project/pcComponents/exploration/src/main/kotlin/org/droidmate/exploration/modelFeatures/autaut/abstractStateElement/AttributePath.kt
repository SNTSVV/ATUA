package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import kotlin.collections.HashMap

data class AttributePath (
        val localAttributes: HashMap<AttributeType, String> = HashMap(),
        val parentAttributePath: AttributePath? = null,
        val childAttributePaths: HashSet<AttributePath> = HashSet()){

    override fun equals(other: Any?): Boolean {
        if (other !is AttributePath)
            return false
        if (other.hashCode() == this.hashCode())
            return true
        return false
    }

    override fun hashCode(): Int {
        var hashValue: Int=31
        hashValue = hashValue + this.localAttributes.hashCode()
        hashValue = hashValue + this.parentAttributePath.hashCode()
        hashValue = hashValue + this.childAttributePaths.hashCode()

        return hashValue
    }
    fun getClassName(): String {
        if (localAttributes.containsKey(AttributeType.className))
        {
            return localAttributes[AttributeType.className]!!
        }
        return ""
    }
    fun getResourceId(): String {
        if (localAttributes.containsKey(AttributeType.resourceId))
        {
            return localAttributes[AttributeType.resourceId]!!
        }
        return ""
    }

    fun getContentDesc(): String {
        if (localAttributes.containsKey(AttributeType.contentDesc))
        {
            return localAttributes[AttributeType.contentDesc]!!
        }
        return ""
    }

    fun getText(): String {
        if (localAttributes.containsKey(AttributeType.text))
        {
            return localAttributes[AttributeType.text]!!
        }
        return ""
    }

    fun isClickable(): Boolean{
        if (localAttributes.containsKey(AttributeType.clickable))
        {
            return localAttributes[AttributeType.clickable]!!.toBoolean()
        }
        return false
    }

    fun isLongClickable(): Boolean{
        if (localAttributes.containsKey(AttributeType.longClickable))
        {
            return localAttributes[AttributeType.longClickable]!!.toBoolean()
        }
        return false
    }

    fun isScrollable(): Boolean{
        if (localAttributes.containsKey(AttributeType.scrollable))
        {
            return localAttributes[AttributeType.scrollable]!!.toBoolean()
        }
        return false
    }

    fun isInputField(): Boolean{
        if (localAttributes.containsKey(AttributeType.isInputField))
        {
            return localAttributes[AttributeType.isInputField]!!.toBoolean()
        }
        return false
    }

    fun isCheckable(): Boolean{
        if (localAttributes[AttributeType.checkable]!!.equals("true"))
        {
            return true
        }
        return false
    }
    fun isInteractive(): Boolean{
        return isClickable() || isLongClickable() || isScrollable() || isCheckable()
    }

    fun contains(attributePath: AttributePath): Boolean {
        //check local
        attributePath.localAttributes.forEach {
            if (!localAttributes.containsKey(it.key))
                return false
            if (localAttributes[it.key] != it.value)
                return false
        }
        if (parentAttributePath!=null && attributePath.parentAttributePath!=null) {
            if (!parentAttributePath.contains(attributePath.parentAttributePath)) {
                return false
            }
        }
        if (childAttributePaths!=null && attributePath.childAttributePaths!=null) {
            childAttributePaths.forEach { childAttPath->
                if (!attributePath.childAttributePaths.any { childAttPath.contains(it)}){
                    return false
                }
            }
        }
        return true

    }

    fun hasParentWithClassName(s: String): Boolean {
        var parentAttributePath = this.parentAttributePath
        while (parentAttributePath!=null) {
            if (parentAttributePath.getClassName().contains(s)) {
                return true
            }
            parentAttributePath = parentAttributePath.parentAttributePath
        }
        return false
    }

    fun hasParent(attributePath: AttributePath): Boolean {
        var parentAttributePath = this.parentAttributePath
        while (parentAttributePath!=null) {
            if (parentAttributePath.equals(attributePath)) {
                return true
            }
            parentAttributePath = parentAttributePath.parentAttributePath
        }
        return false
    }
}