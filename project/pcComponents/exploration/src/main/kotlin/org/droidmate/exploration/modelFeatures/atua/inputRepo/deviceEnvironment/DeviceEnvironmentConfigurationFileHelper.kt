// ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
// Copyright (C) 2019 - 2021 University of Luxembourg
//
// This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.droidmate.exploration.modelFeatures.atua.inputRepo.deviceEnvironment

import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Activity
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
                    val windows = HashSet<Window>()
                    settingConfigJson.getJSONArray("AppliedActivities").forEach {
                        val activity = it.toString()
                        val window = WindowManager.instance.allMeaningWindows.find { it.classType == activity && it is Activity }
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