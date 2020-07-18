package org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput

import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class InputConfiguration {
    val informationTypes = ArrayList<InformationType>()
    val currentDataInputs = HashMap<InformationType, InformationInstance>()

    fun initializeGeneralInformationType(){
        val dateInformationType = InformationType(name = "Date")

        val dayDataField = DataField(name = "Day", informationType = dateInformationType)
        dayDataField.resourceIdPatterns.add("day")

        val monthDataField = DataField(name = "Month", informationType = dateInformationType)
        monthDataField.resourceIdPatterns.add("month")

        val yearDataField = DataField("Year", informationType = dateInformationType)
        yearDataField.resourceIdPatterns.add("year")
    }

    fun resetCurrentDataInputs()
    {
        currentDataInputs.clear()
    }

    fun getInputDataField(inputWidget: Widget, state: State<*>): String {
        val inputWidgetResourceId = getInputWidgetResourceId(inputWidget,state )
        val dataField = informationTypes.flatMap { it.dataFields }.find {
            it.resourceIdPatterns.find { inputWidgetResourceId.contains(it) } != null
        }
        if (dataField == null)
            return ""
        if (!currentDataInputs.containsKey(dataField.informationType))
        {
            val instance = dataField.informationType.data.random()
            currentDataInputs.put(dataField.informationType, instance)
        }
        val dataInput = currentDataInputs[dataField.informationType]!!
        return dataInput.data[dataField]?:""
    }

    fun getInputWidgetResourceId(inputWidget: Widget, state: State<*>): String {
        var widget = inputWidget
        while (widget.resourceId.isBlank())
        {
            widget = state.widgets.find { it.idHash == widget.parentHash }!!
        }
        return widget.resourceId
    }

    fun getDataField(inputWidget: Widget, state: State<*>): DataField?{
        val inputWidgetResourceId = getInputWidgetResourceId(inputWidget,state )
        val dataField = informationTypes.flatMap { it.dataFields }.find {
            it.resourceIdPatterns.find { inputWidgetResourceId.contains(it) } != null
        }
       return dataField
    }
}