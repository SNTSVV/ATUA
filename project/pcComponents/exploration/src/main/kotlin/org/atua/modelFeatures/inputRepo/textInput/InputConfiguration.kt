/*
 * ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
 * Copyright (C) 2019 - 2021 University of Luxembourg
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package org.atua.modelFeatures.inputRepo.textInput

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