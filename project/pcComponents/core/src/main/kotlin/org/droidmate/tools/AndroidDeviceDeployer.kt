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

package org.droidmate.tools

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.droidmate.configuration.ConfigProperties
import org.droidmate.configuration.ConfigProperties.Deploy.installAux
import org.droidmate.configuration.ConfigProperties.Deploy.uninstallAux
import org.droidmate.configuration.ConfigProperties.Exploration.apiVersion
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.IAndroidDevice
import org.droidmate.device.IDeployableAndroidDevice
import org.droidmate.device.android_sdk.*
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.device.deviceInterface.RobustDevice
import org.droidmate.device.error.DeviceException
import org.droidmate.deviceInterface.DeviceConstants
import org.droidmate.errors.UnexpectedIfElseFallthroughError
import org.droidmate.logging.Markers
import org.droidmate.misc.DroidmateException
import org.droidmate.misc.EnvironmentConstants
import org.droidmate.misc.FailableExploration
import org.slf4j.LoggerFactory
import java.util.*

class AndroidDeviceDeployer constructor(private val cfg: ConfigurationWrapper,
                                        private val adbWrapper: IAdbWrapper,
                                        private val deviceFactory: IAndroidDeviceFactory) : IAndroidDeviceDeployer {

	companion object {
		private val log by lazy { LoggerFactory.getLogger(AndroidDeviceDeployer::class.java) }

		@Throws(DeviceException::class)
		@JvmStatic
		fun tryResolveSerialNumber(adbWrapper: IAdbWrapper, usedSerialNumbers: List<String>, deviceIndex: Int): String {
			val devicesDescriptors = adbWrapper.getAndroidDevicesDescriptors()
			return getSerialNumber(devicesDescriptors, usedSerialNumbers, deviceIndex)
		}

		@JvmStatic
		private fun getSerialNumber(deviceDescriptors: List<AndroidDeviceDescriptor>, usedSerialNumbers: List<String>, deviceIndex: Int): String {
//    logcat.trace("Serial numbers of found android devices:")
//    assert deviceDescriptors?.size() > 0
//    deviceDescriptors.each {AndroidDeviceDescriptor update -> logcat.trace(update.deviceSerialNumber)}

			val unrecognizedNumbers = usedSerialNumbers.minus(deviceDescriptors.map { it.deviceSerialNumber })
			if (unrecognizedNumbers.isNotEmpty())
				throw DroidmateException("While obtaining new A(V)D serial number, DroidMate detected that one or more of the " +
						"already used serial numbers do not appear on the list of serial numbers returned by the 'adb devices' command. " +
						"This indicates the device(s) with these number most likely have been disconnected. Thus, DroidMate throws exception. " +
						"List of the offending serial numbers: $unrecognizedNumbers")

			val unusedDescriptors = deviceDescriptors.filter { add -> add.deviceSerialNumber !in usedSerialNumbers }

			if (unusedDescriptors.isEmpty())
				throw DroidmateException("No unused A(V)D serial numbers have been found. List of all already used serial numbers: $usedSerialNumbers")

			if (unusedDescriptors.size < deviceIndex + 1)
				throw DroidmateException("Requested device with device no. ${deviceIndex + 1} but the no. of available devices is ${unusedDescriptors.size}.")

			val serialNumbers = unusedDescriptors.toList()

			return serialNumbers[deviceIndex].deviceSerialNumber
		}
	}

	/**
	 * <p>
	 * <i> --- This doc was last reviewed on 21 Dec 2013.</i>
	 * </p><p>
	 * Determines if the device accessed through this class is currently setup. The value of this field is modified
	 * by {@link #trySetUp(IDeployableAndroidDevice)}  and {@link #tryTearDown(IDeployableAndroidDevice)}.
	 *
	 * </p><p>
	 * Useful to be tested for in preconditions requiring for the device to be set-up.
	 *
	 */
	private var deviceIsSetup: Boolean = false

	// To make DroidMate work with multiple A(V)D, this list will have to be one for all AndroidDeviceDeployer-s, not one per inst.
	private val usedSerialNumbers: MutableList<String> = mutableListOf()

	private val logcatMonitor = LogcatMonitor(cfg, adbWrapper)

	/**
	 * <p>
	 * Setups android device for DroidMate purposes. Starts adb server if necessary, forwards ports, pushes uiautomator-daemon jar,
	 * pushes monitor apk, pushes apiPoliciesFile, monitorPortFile, coveragePortFile, and starts uiautomator-daemon server.
	 *
	 * </p><p>
	 * Remember to call {@link #tryTearDown} when done with the device.
	 *
	 * </p>
	 * @throws DeviceException if any of the operation fails.
	 */
	@Throws(DeviceException::class)
	private suspend fun trySetUp(device: IDeployableAndroidDevice) {
		this.adbWrapper.startAdbServer()

		if (cfg[installAux]) {
			device.reinstallUiAutomatorDaemon()
			device.pushAuxiliaryFiles()
		}
		logcatMonitor.start()
		device.setupConnection()

		this.deviceIsSetup = true
	}

	/** <p>
	 * Stops the uiautomator-daemon and removes its jar from the A(V)D. Call it after {@link #trySetUp}
	 *
	 * </p>
	 * @throws DeviceException if any of the operations fails.
	 * @see #trySetUp(IDeployableAndroidDevice)
	 */
	@Throws(DeviceException::class)
	private suspend fun tryTearDown(device: IDeployableAndroidDevice) {
		this.deviceIsSetup = false

		if (device.isAvailable()) {
			log.debug("Tearing down.")
			// device.pullLogcatLogFile()
			logcatMonitor.terminate()

			device.closeConnection()

			if (cfg[uninstallAux]) {
				if (cfg[apiVersion] == ConfigurationWrapper.api23) {
					device.uninstallApk(DeviceConstants.uia2Daemon_testPackageName, true)
					device.uninstallApk(DeviceConstants.uia2Daemon_packageName, true)
				} else
					throw UnexpectedIfElseFallthroughError()
				if(cfg.monitorApk!=null) device.removeJar(cfg.getPath(EnvironmentConstants.monitor_apk_name))
			}
			device.removeFile("", DeviceConstants.imgPath.removeSuffix("/")) // delete the image dir to ensure that consequent runs will not accidentially pull old images
		} else
			log.trace("Device is not available. Skipping tear down.")
	}

	override suspend fun setupAndExecute(deviceSerialNumber: String, deviceIndex: Int,
	                                     apkDeployer: IApkDeployer,
	                                     apks: Collection<Apk>,
	                                     exploreFn: suspend (IApk, IRobustDevice) -> FailableExploration): Map<Apk, FailableExploration> {
		val explorationResults: MutableMap<Apk, FailableExploration> = HashMap()
		// Set the deviceSerialNumber in the configuration. Calculate the deviceSerialNumber, if not not provided by
		// the given deviceIndex. It can't be done in the configuration because it needs access to the AdbWrapper.

		// If deviceIndex is given and serialNumber is empty
		cfg.deviceSerialNumber = if (deviceSerialNumber.isEmpty()) {
			tryResolveSerialNumber(this.adbWrapper, this.usedSerialNumbers, deviceIndex)
		} else
			deviceSerialNumber

		check(cfg.deviceSerialNumber.isNotEmpty()) {"Expected deviceSerialNumber to be initialized."}
		log.info("Setup device with deviceSerialNumber of ${cfg.deviceSerialNumber}")


		val device = setupDevice()
		try {

			assert(explorationResults.isEmpty())

			var encounteredApkExplorationsStoppingException = false

			apks.forEachIndexed { i, apk ->
				if (!encounteredApkExplorationsStoppingException) {
					log.info(Markers.appHealth, "Processing ${i + 1} out of ${apks.size} apks: ${apk.fileName}")

					// explore this apk on device and store it in explorationResults
					apkDeployer.withDeployedApk(device, apk, exploreFn).let{ result ->
						explorationResults[apk] = result
						if (result.error.any { (it as? ApkExplorationException)?.shouldStopFurtherApkExplorations() == true }) {
							log.warn("Encountered an exception that stops further apk explorations. Skipping exploring the remaining apks.")
							encounteredApkExplorationsStoppingException = true
						}
					}

					// Just preventative measures for ensuring healthiness of the device connection.
					device.restartUiaDaemon(false)
				}
			}
			/** all explorations done - tear down */
		} finally {
			if (cfg[ConfigProperties.UiAutomatorServer.delayedImgFetch]) // we cannot cancel the job in the explorationLoop as subsequent apk explorations wouldn't have a job to pull images
				explorationResults.values.forEach { it.result?.apply{ imgTransfer.coroutineContext[Job]?.cancelAndJoin()} } // close pending coroutine job from img fetch

			log.debug("Finalizing: setupAndExecute(${cfg.deviceSerialNumber})->finally{} for computation($device)")
			try {
				tryTearDown(device)
				usedSerialNumbers -= cfg.deviceSerialNumber

			} catch (tearDownThrowable: Throwable) {
				log.warn(Markers.appHealth,
					"! Caught ${tearDownThrowable.javaClass.simpleName} in setupAndExecute(${cfg.deviceSerialNumber})->tryTearDown($device). " +
							"Adding as a cause to an ${ExplorationException::class.java.simpleName}. " +
							"Then adding to the collected error list.\n" +
							"The ${tearDownThrowable::class.java.simpleName}: $tearDownThrowable")
				log.error(Markers.appHealth, tearDownThrowable.message, tearDownThrowable)
			}
			log.debug("Finalizing DONE: setupAndExecute(${cfg.deviceSerialNumber})->finally{} for computation($device)")
		}
		return explorationResults
	}

	/**
	 * Setup contains the install of the deviceControlDaemon, monitor and starting the adb wrapper.
	 * This is to be done once before any specific AUT is installed.
	 * If any error occurs in this phase we cannot start the exploration for any app,
	 * therefore we do not catch any exception but rather propagate it.
	 */
	private suspend fun setupDevice(): IRobustDevice {
		this.usedSerialNumbers.add(cfg.deviceSerialNumber)

		val device = robustWithReadableLogs(this.deviceFactory.create(cfg.deviceSerialNumber))

		trySetUp(device)
		try {
			device.removeFile(
				"",
				DeviceConstants.imgPath.removeSuffix("/")
			) // delete the image dir to ensure that consequent runs will not accidentially pull old images
		} catch (e: Throwable){
			log.debug("Removing the (old) image directory on device failed",e)
		}
		return device
	}

	private fun robustWithReadableLogs(device: IAndroidDevice): IRobustDevice = RobustDevice(device, this.cfg)
}