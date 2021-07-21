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
package org.droidmate.exploration.modelFeatures.atua.inputRepo.textInput

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

class InputConfigurationFileHelper {
    companion object{
        fun readInputConfigurationFile(filePath: Path): InputConfiguration {
            val domainInputConfiguration = InputConfiguration()
            if (Files.exists(filePath))
            {
                val jsonData = String(Files.readAllBytes(filePath))
                val jObj = JSONObject(jsonData)
                readInputInformation(jMap = jObj, inputConfiguration = domainInputConfiguration)
            }
            return domainInputConfiguration
        }

        private fun readInputInformation(jMap: JSONObject, inputConfiguration: InputConfiguration) {
            jMap.keys().asSequence().forEach {
                val name = it.toString()
                val inputInformationJson = jMap.getJSONObject(it)
                val informationType: InformationType = InformationType(name = name)
                inputConfiguration.informationTypes.add(informationType)
                val dataFieldJson = inputInformationJson.getJSONObject("DataFields")
                val dataFields: ArrayList<DataField> = readDataFields(dataFieldJson, informationType)
                informationType.dataFields.addAll(dataFields)
                val dataJson = inputInformationJson.getJSONArray("Instances")
                val data: ArrayList<InformationInstance> = readInstances(dataJson, informationType)
                informationType.data.addAll(data)
            }

        }

        private fun readInstances(dataJson: JSONArray, informationType: InformationType): ArrayList<InformationInstance> {
            val data = ArrayList<InformationInstance>()
            dataJson.forEach {
                val instanceJson = it as JSONObject
                val instance: InformationInstance = readInformationInstance(instanceJson, informationType)
                data.add(instance)
            }
            return data
        }

        private fun readInformationInstance(instanceJson: JSONObject, informationType: InformationType): InformationInstance {
            val data = HashMap<DataField, String>()
            val instance = InformationInstance(informationType = informationType, data = data)
            instanceJson.keys().asSequence().forEach {
                val name = it
                val dataField = informationType.dataFields.find { it.name == name }
                if (dataField != null)
                {
                    val value = instanceJson.getString(it)
                    data.put(dataField,value)
                }
            }
            return instance
        }

        private fun readDataFields(dataFieldsJson: JSONObject, informationType: InformationType): ArrayList<DataField> {
            val dataFields = ArrayList<DataField>()
            dataFieldsJson.keys().asSequence().forEach {
                val dataFieldJson = dataFieldsJson[it] as JSONObject
                val resourceIdPatternsJson = dataFieldJson.getJSONArray("resourceIdPatterns")
                val resourceIdPatterns = ArrayList<String>()
                val dataField = DataField(name = it, resourceIdPatterns = resourceIdPatterns, informationType = informationType)
                dataFields.add(dataField)
                resourceIdPatternsJson.forEach {
                    val resourceId = it.toString()
                    resourceIdPatterns.add(resourceId)
                }
            }
            return dataFields
        }
    }
}