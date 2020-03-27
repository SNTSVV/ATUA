package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.WidgetReducer
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

data class WidgetGroup (val attributePath: AttributePath, val cardinality: Cardinality) {
    var exerciseCount: Int = 0
    var guiWidgets = HashSet<Widget>()
    fun getGUIWidgets ( guiState: State<*>): List<Widget>{
        val abstractState = AbstractStateManager.instance.getAbstractState(guiState)!!
        val tempFullAttributePaths: HashMap<Widget, AttributePath> = HashMap()
        val tempRelativeAttributePaths: HashMap<Widget, AttributePath> = HashMap()
        val selectedGuiWidgets = ArrayList<Widget>()
        guiState.widgets.forEach {
            val reducedAttributePath = WidgetReducer.reduce(it,guiState,abstractState.activity,tempFullAttributePaths,tempRelativeAttributePaths)
            if (reducedAttributePath.equals(attributePath))
            {
                selectedGuiWidgets.add(it)
            }
        }
        return selectedGuiWidgets
    }

    fun isAbstractRepresentationOf(widget: Widget, guiState: State<*>): Boolean
    {
        val abstractState = AbstractStateManager.instance.getAbstractState(guiState)!!
        val tempFullAttributePaths: HashMap<Widget, AttributePath> = HashMap()
        val tempRelativeAttributePaths: HashMap<Widget, AttributePath> = HashMap()
        val reducedAttributePath = WidgetReducer.reduce(widget,guiState,abstractState.activity,tempFullAttributePaths,tempRelativeAttributePaths)
        if (reducedAttributePath.equals(attributePath))
        {
            return true
        }
        return false
    }

    fun getLocalAttributes(): HashMap<AttributeType, String>{
        return attributePath.localAttributes
    }

    override fun hashCode(): Int {
        return attributePath.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is WidgetGroup)
            return false
        return this.hashCode()==other.hashCode()
    }

}

enum class Cardinality{
    ZERO,
    ONE,
    MANY
}