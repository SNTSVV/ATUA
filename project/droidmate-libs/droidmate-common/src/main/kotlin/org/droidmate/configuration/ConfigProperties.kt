// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.configuration

import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.doubleType
import com.natpryce.konfig.getValue
import com.natpryce.konfig.intType
import com.natpryce.konfig.listType
import com.natpryce.konfig.longType
import com.natpryce.konfig.stringType
import com.natpryce.konfig.uriType

abstract class ConfigProperties {

    object Core : PropertyGroup() {
        val logLevel by stringType // TODO we could use a nice enumType instead
        val configPath by uriType
        val hostIp by stringType
    }

    object ApiMonitorServer : PropertyGroup() {
        val monitorSocketTimeout by intType
        val monitorUseLogcat by booleanType
        val basePort by intType
    }

    object ExecutionMode : PropertyGroup() {
        val inline by booleanType
        val explore by booleanType
        val coverage by booleanType
    }

    object Deploy : PropertyGroup() {
        val installApk by booleanType
        val installAux by booleanType
        val uninstallApk by booleanType
        val uninstallAux by booleanType
        val replaceResources by booleanType
        val shuffleApks by booleanType
        val useApkFixturesDir by booleanType
        val deployRawApks by booleanType
        val installMonitor by booleanType
    }

    object DeviceCommunication : PropertyGroup() {
        val checkAppIsRunningRetryAttempts by intType
        val checkAppIsRunningRetryDelay by intType
        val checkDeviceAvailableAfterRebootAttempts by intType
        val checkDeviceAvailableAfterRebootFirstDelay by intType
        val checkDeviceAvailableAfterRebootLaterDelays by intType
        val stopAppRetryAttempts by intType
        val stopAppSuccessCheckDelay by intType
        val deviceOperationAttempts by intType
        val deviceOperationDelay by intType
        val waitForCanRebootDelay by intType
        val waitForDevice by booleanType
    }

    object Exploration : PropertyGroup() {
        val apksDir by uriType
        val apksLimit by intType
        val apkNames by listType(stringType)
        val deviceIndex by intType
        val deviceSerialNumber by stringType
        val runOnNotInlined by booleanType
        val launchActivityDelay by longType
        val launchActivityTimeout by intType
        val apiVersion by intType
        val widgetActionDelay by longType
    }

    object Output : PropertyGroup() {
        val outputDir by uriType
        val screenshotDir by stringType
        val reportDir by stringType
    }

    object Strategies : PropertyGroup() {
        val reset by booleanType
        val explore by booleanType
        val terminate by booleanType
        val back by booleanType
        val allowRuntimeDialog by booleanType
        val denyRuntimeDialog by booleanType
        val playback by booleanType
        val dfs by booleanType
        val rotateUI by booleanType
        val minimizeMaximize by booleanType
        val textInput by booleanType
        object Parameters : PropertyGroup() {
            val uiRotation by intType
            val randomScroll by booleanType
            val biasedRandom by booleanType
        }
    }

    object Selectors : PropertyGroup() {
        val pressBackProbability by doubleType
        val playbackModelDir by uriType
        val resetEvery by intType
        val actionLimit by intType
        val timeLimit by intType
        val randomSeed by longType
        val stopOnExhaustion by booleanType
        val dfs by booleanType
    }

    object Report : PropertyGroup() {
        val inputDir by uriType
        val includePlots by booleanType
    }

    object UiAutomatorServer : PropertyGroup() {
        val waitForIdleTimeout by intType
        val waitForInteractableTimeout by intType
        val enablePrintOuts by booleanType
        val delayedImgFetch by booleanType
        val imgQuality by intType
        val startTimeout by intType
        val socketTimeout by intType
        val basePort by intType
    }
}