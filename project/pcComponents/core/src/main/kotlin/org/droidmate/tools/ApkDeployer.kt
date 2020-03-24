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

import org.droidmate.configuration.ConfigProperties.Deploy.installApk
import org.droidmate.configuration.ConfigProperties.Deploy.uninstallApk
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.ApkExplorationException
import org.droidmate.device.error.DeviceException
import org.droidmate.device.IDeployableAndroidDevice
import org.droidmate.device.android_sdk.IApk
import org.droidmate.device.deviceInterface.IRobustDevice
import org.droidmate.logging.Markers
import org.droidmate.misc.FailableExploration
import org.slf4j.LoggerFactory

/**
 * @see IApkDeployer#withDeployedApk(IDeployableAndroidDevice, org.droidmate.exploration.IApk, (IApk) -> Any)
 */
class ApkDeployer constructor(private val cfg: ConfigurationWrapper) : IApkDeployer {
	companion object {
		private val log by lazy { LoggerFactory.getLogger(ApkDeployer::class.java) }
	}

	override suspend fun withDeployedApk(device: IRobustDevice, apk: IApk, exploreFn: suspend (IApk, IRobustDevice) -> FailableExploration): FailableExploration {
		log.debug("withDeployedApk(device, $apk.fileName, computation)")

		val deployApkException = deployApk(device, apk)
		if (deployApkException != null) {  // if the apk cannot be deployed we cannot create any exploration result, just return the exception
			return FailableExploration(null, listOf(deployApkException))
		}

		val explResult = exploreFn(apk,device)  // all exploration exceptions should be handled internally by in function

		log.debug("Finalizing: withDeployedApk($device, ${apk.fileName}).finally{} for computation($apk.fileName)")
		try {
			tryUndeployApk(device, apk)
		} catch (undeployApkThrowable: Throwable) {
			log.warn(Markers.appHealth,
					"! Caught ${undeployApkThrowable.javaClass.simpleName} in withDeployedApk($device, ${apk.fileName})->tryUndeployApk(). " +
							"Adding as a cause to an ${ApkExplorationException::class.java.simpleName}. Then adding to the collected error list.\n" +
							"The ${undeployApkThrowable.javaClass.simpleName}: $undeployApkThrowable")
			log.error(Markers.appHealth, undeployApkThrowable.message, undeployApkThrowable)
			// if we have an error on undeploy this does not affect the exploration results therefore this error is only logged
		}
		log.debug("Finalizing DONE: withDeployedApk($device, ${apk.fileName}).finally{} for computation($apk.fileName)")

		log.trace("Undeployed apk $apk.fileName")
		return explResult
	}

	private suspend fun deployApk(device: IDeployableAndroidDevice, apk: IApk): ApkExplorationException? {

		if (cfg[installApk]) {
			try {
				tryReinstallApk(device, apk)

			} catch (deployThrowable: Throwable) {
				log.warn(Markers.appHealth,
						"! Caught ${deployThrowable.javaClass.simpleName} in deployApk($device, $apk.fileName). " +
								"Adding as a cause to an ${ApkExplorationException::class.java.simpleName}. Then adding to the collected error list.")
				log.error(Markers.appHealth, deployThrowable.message, deployThrowable)
				return ApkExplorationException(apk, deployThrowable)
			}
		}
		return null
	}

	@Throws(DeviceException::class)
	private suspend fun tryUndeployApk(device: IDeployableAndroidDevice, apk: IApk) {
		if (cfg[uninstallApk]) {
			if (device.isAvailable()) {
				log.info("Uninstalling ${apk.fileName}")
				// This method is called in RunnableTerminateExplorationAction.performDeviceActions
				// but exploration might throw exception before that call is made, so here we make another attempt at doing it.
				device.closeMonitorServers()
				device.clearPackage(apk.packageName)
				device.uninstallApk(apk.packageName, /* ignoreFailure = */ false)
			} else
				log.info("Device not available. Skipping uninstalling $apk.fileName")

		} else {
			// If the apk is not uninstalled, some of its monitored services might remain, interfering with monitored
			// logcat messages expectations for next explored apk, making DroidMate throw an assertion error.
		}
	}

	@Throws(DeviceException::class)
	private suspend fun tryReinstallApk(device: IDeployableAndroidDevice, apk: IApk) {
		log.info("Reinstalling {}", apk.fileName)
		/* The apk is uninstalled before installation to ensure:
		 - any cache will be purged.
		 - a different version of the same app can be installed, if necessary (without uninstall, an error will be issued about
		 certificates not matching (or something like that))
		*/
		device.uninstallApk(apk.packageName, /* ignoreFailure  = */ true)

		if (!device.isAvailable())
			throw DeviceException("No device is available just before installing $apk", /* stopFurtherApkExplorations */ true)
		device.installApk(apk)
	}

}
