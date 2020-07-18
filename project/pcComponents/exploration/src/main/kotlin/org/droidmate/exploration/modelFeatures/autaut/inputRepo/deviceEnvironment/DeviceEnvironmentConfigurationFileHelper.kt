package org.droidmate.exploration.modelFeatures.autaut.inputRepo.deviceEnvironment

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path

class DeviceEnvironmentConfigurationFileHelper {
    companion object{

        fun readInputConfigurationFile(filePath: Path): DeviceEnvironmentConfiguration {
            val deviceEnvironmentConfiguration = DeviceEnvironmentConfiguration()
            if (Files.exists(filePath))
            {
                val jsonData = String(Files.readAllBytes(filePath))
                val jObj = JSONObject(jsonData)
                readJson(jMap = jObj, deviceEnvironmentConfiguration = deviceEnvironmentConfiguration)
            }
            return deviceEnvironmentConfiguration
        }

        private fun readJson(jMap: JSONObject, deviceEnvironmentConfiguration: DeviceEnvironmentConfiguration) {
            val environmentJson = jMap.get("EnvironmentConfiguration")
            if (environmentJson!=null) {
                (environmentJson as JSONArray).forEach {
                    val settingConfigJson = it as JSONObject
                    val setting = settingConfigJson.getString("SettingName")
                    val windows = HashSet<WTGNode>()
                    settingConfigJson.getJSONArray("AppliedActivities").forEach {
                        val activity = it.toString()
                        val window = WTGNode.allMeaningNodes.find { it.activityClass == activity || it.classType == activity }
                        if (window != null) {
                            windows.add(window)
                        }
                    }
                    if (windows.isNotEmpty()) {
                        deviceEnvironmentConfiguration.configurations.put(setting,windows)
                    }
                }
            }
        }
    }
}