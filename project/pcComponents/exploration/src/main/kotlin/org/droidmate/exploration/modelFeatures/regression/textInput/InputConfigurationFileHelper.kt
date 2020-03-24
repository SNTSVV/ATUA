package org.droidmate.exploration.modelFeatures.regression.textInput

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

class InputConfigurationFileHelper {
    companion object{
        fun readInputConfigurationFile(filePath: Path):InputConfiguration {
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
                val informationType:InformationType = InformationType(name = name)
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
                val instance: InformationInstance = readInformationInstance (instanceJson,informationType)
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