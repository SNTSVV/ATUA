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

import com.natpryce.konfig.*
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.StandardToStringStyle
import org.droidmate.configuration.ConfigProperties.ApiMonitorServer.monitorSocketTimeout
import org.droidmate.configuration.ConfigProperties.ApiMonitorServer.monitorUseLogcat
import org.droidmate.configuration.ConfigProperties.Core.configPath
import org.droidmate.configuration.ConfigProperties.Core.hostIp
import org.droidmate.configuration.ConfigProperties.Core.logLevel
import org.droidmate.configuration.ConfigProperties.Deploy.deployRawApks
import org.droidmate.configuration.ConfigProperties.Deploy.installApk
import org.droidmate.configuration.ConfigProperties.Deploy.installAux
import org.droidmate.configuration.ConfigProperties.Deploy.installMonitor
import org.droidmate.configuration.ConfigProperties.Deploy.replaceResources
import org.droidmate.configuration.ConfigProperties.Deploy.shuffleApks
import org.droidmate.configuration.ConfigProperties.Deploy.uninstallApk
import org.droidmate.configuration.ConfigProperties.Deploy.uninstallAux
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkAppIsRunningRetryAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkAppIsRunningRetryDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootFirstDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.checkDeviceAvailableAfterRebootLaterDelays
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.stopAppRetryAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.stopAppSuccessCheckDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.waitForCanRebootDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.deviceOperationAttempts
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.deviceOperationDelay
import org.droidmate.configuration.ConfigProperties.DeviceCommunication.waitForDevice
import org.droidmate.configuration.ConfigProperties.ExecutionMode.coverage
import org.droidmate.configuration.ConfigProperties.ExecutionMode.explore
import org.droidmate.configuration.ConfigProperties.ExecutionMode.inline
import org.droidmate.configuration.ConfigProperties.Exploration.apiVersion
import org.droidmate.configuration.ConfigProperties.Exploration.apkNames
import org.droidmate.configuration.ConfigProperties.Exploration.apksDir
import org.droidmate.configuration.ConfigProperties.Exploration.apksLimit
import org.droidmate.configuration.ConfigProperties.Exploration.widgetActionDelay
import org.droidmate.configuration.ConfigProperties.Exploration.deviceIndex
import org.droidmate.configuration.ConfigProperties.Exploration.deviceSerialNumber
import org.droidmate.configuration.ConfigProperties.Exploration.launchActivityDelay
import org.droidmate.configuration.ConfigProperties.Exploration.launchActivityTimeout
import org.droidmate.configuration.ConfigProperties.Exploration.runOnNotInlined
import org.droidmate.configuration.ConfigProperties.Output.outputDir
import org.droidmate.configuration.ConfigProperties.Output.reportDir
import org.droidmate.configuration.ConfigProperties.Output.screenshotDir
import org.droidmate.configuration.ConfigProperties.Report.includePlots
import org.droidmate.configuration.ConfigProperties.Report.inputDir
import org.droidmate.configuration.ConfigProperties.Selectors.actionLimit
import org.droidmate.configuration.ConfigProperties.Selectors.playbackModelDir
import org.droidmate.configuration.ConfigProperties.Selectors.pressBackProbability
import org.droidmate.configuration.ConfigProperties.Selectors.randomSeed
import org.droidmate.configuration.ConfigProperties.Selectors.resetEvery
import org.droidmate.configuration.ConfigProperties.Selectors.stopOnExhaustion
import org.droidmate.configuration.ConfigProperties.Selectors.timeLimit
import org.droidmate.configuration.ConfigProperties.Strategies.Parameters.uiRotation
import org.droidmate.configuration.ConfigProperties.Strategies.allowRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies.back
import org.droidmate.configuration.ConfigProperties.Strategies.denyRuntimeDialog
import org.droidmate.configuration.ConfigProperties.Strategies.minimizeMaximize
import org.droidmate.configuration.ConfigProperties.Strategies.playback
import org.droidmate.configuration.ConfigProperties.Strategies.reset
import org.droidmate.configuration.ConfigProperties.Strategies.rotateUI
import org.droidmate.configuration.ConfigProperties.Strategies.terminate
import org.droidmate.configuration.ConfigProperties.Strategies.textInput
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.basePort
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.delayedImgFetch
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.enablePrintOuts
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.imgQuality
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.socketTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.startTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.waitForInteractableTimeout
import org.droidmate.configuration.ConfigProperties.UiAutomatorServer.waitForIdleTimeout
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF.Companion.StatementCoverage.coverageDir
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF.Companion.StatementCoverage.enableCoverage
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF.Companion.StatementCoverage.onlyCoverAppPackageName
import org.droidmate.legacy.Resource
import org.droidmate.logging.Markers.Companion.runData
import org.droidmate.misc.EnvironmentConstants
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.StringBuilder
import java.lang.management.ManagementFactory
import java.nio.file.*

/**
 * @see IConfigurationBuilder#build(java.lang.String [ ], java.nio.file.FileSystem)
 */
public class ConfigurationBuilder : IConfigurationBuilder {
	@Throws(ConfigurationException::class)
	override fun build(args: Array<String>, vararg options: CommandLineOption): ConfigurationWrapper = build(args, FileSystems.getDefault(),*options)

	/**
	 * there may be occassions when an (extended) project only wants to allow for a subset of the commandline arguments to be available,
	 * e.g. to make the --help more comprehensive.
	 * Then this function can be used to create a configuration which will only show/allow the commandline options from [options].
	 */
	fun buildRestrictedOptions(args: Array<String>, fs: FileSystem, vararg options: CommandLineOption): ConfigurationWrapper = build(parseArgs(args,
			*options).first, fs)

	@Throws(ConfigurationException::class)
	override fun build(args: Array<String>, fs: FileSystem, vararg options: CommandLineOption): ConfigurationWrapper = build(parseArgs(args,
			*options,
			// Core
			CommandLineOption(logLevel, description = "Logging level of the entirety of application. Possible values, comma separated: info, debug, trace, warn, error."),
			CommandLineOption(configPath, description = "Path to a custom configuration file, which replaces the default configuration.", short = "config"),
			CommandLineOption(hostIp, description="allows to specify an adb host different from localhost, i.e. to allow container environments to access host devices"),
			// ApiMonitorServer
			CommandLineOption(monitorSocketTimeout, description = "Socket timeout to communicate with the API monitor service."),
			CommandLineOption(monitorUseLogcat, description = "Use logical for API logging instead of TCPServer (deprecated)."),
			CommandLineOption(ConfigProperties.ApiMonitorServer.basePort, description = "The base port for the communication with the the API monitor service. DroidMate communicates over this base port + device index."),
			// ExecutionMode
			CommandLineOption(inline, description = "If present, instead of normal run, DroidMate will inline all non-inlined apks. Before inlining backup copies of the apks will be created and put into a sub-directory of the directory containing the apks. This flag cannot be combined with another execution mode."),
			CommandLineOption(explore, description = "Run DroidMate in exploration mode."),
			CommandLineOption(coverage, description = "If present, instead of normal run, DroidMate will run in 'instrument APK for coverage' mode. This flag cannot be combined with another execution mode."),
			// Deploy
			CommandLineOption(installApk, description = "Reinstall the app to the device. If the app is not previously installed the exploration will fail"),
			CommandLineOption(installAux, description = "Reinstall the auxiliary files (UIAutomator and Monitor) to the device. If the auxiliary files are not previously installed the exploration will fail."),
			CommandLineOption(uninstallApk, description = "Uninstall the APK after the exploration."),
			CommandLineOption(uninstallAux, description = "Uninstall auxiliary files (UIAutomator and Monitor) after the exploration."),
			CommandLineOption(replaceResources, description = "Replace the resources from the extracted resources folder upon execution."),
			CommandLineOption(shuffleApks, description = "ExplorationStrategy the apks in the input directory in a random order."),
			CommandLineOption(deployRawApks, description = "Deploys apks to device in 'raw' form, that is, without instrumenting them. Will deploy them raw even if instrumented version is available from last run."),
			CommandLineOption(installMonitor, description = "Install the API monitor into the device."),
			// DeviceCommunication
			CommandLineOption(checkAppIsRunningRetryAttempts, description = "Number of attempts to check if an app is running on the device."),
			CommandLineOption(checkAppIsRunningRetryDelay, description = "Timeout for each attempt to check if an app is running on the device in milliseconds."),
			CommandLineOption(checkDeviceAvailableAfterRebootAttempts, description = "Determines how often DroidMate checks if a device is available after a reboot."),
			CommandLineOption(checkDeviceAvailableAfterRebootFirstDelay, description = "The first timeout after a device rebooted, before its availability will be checked."),
			CommandLineOption(checkDeviceAvailableAfterRebootLaterDelays, description = "The non-first timeout after a device rebooted, before its availability will be checked."),
			CommandLineOption(stopAppRetryAttempts, description = "Number of attempts to close an 'application has stopped' dialog."),
			CommandLineOption(stopAppSuccessCheckDelay, description = "Delay after each failed attempt close an 'application has stopped' dialog"),
			CommandLineOption(waitForCanRebootDelay, description = "Delay (in milliseconds) after an attempt was made to reboot a device, before."),
			CommandLineOption(deviceOperationAttempts, description = "Number of attempts to retry other failed device operations."),
			CommandLineOption(deviceOperationDelay, description = "Delay (in milliseconds) after an attempt was made to perform a device operation, before retrying again."),
			CommandLineOption(waitForDevice, description = "Wait for a device to be connected to the PC instead of cancelling the exploration."),
			// Exploration
			CommandLineOption(apksDir, description = "Directory containing the apks to be processed by DroidMate."),
			CommandLineOption(apksLimit, description = "Limits the number of apks on which DroidMate will run. 0 means no limit."),
			CommandLineOption(apkNames, description = "Filters apps on which DroidMate will be run. Supply full file names, separated by commas, surrounded by square brackets. If the list is empty, it will run on all the apps in the apks dir. Example value: [app1.apk, app2.apk]"),
			CommandLineOption(deviceIndex, description = "Index of the device to be used (from adb devices). Zero based."),
			CommandLineOption(deviceSerialNumber, description = "Serial number of the device to be used. Mutually exclusive to index."),
			CommandLineOption(runOnNotInlined, description = "Allow DroidMate to run on non-inlined apks."),
			CommandLineOption(launchActivityDelay, description = "Delay (in milliseconds) to wait for the app to load before continuing the exploration after a reset (or exploration start)."),
			CommandLineOption(launchActivityTimeout, description = "Maximum amount of time to be waited for an app to start after a reset in milliseconds."),
			CommandLineOption(apiVersion, description = "Has to be set to the Android API version corresponding to the (virtual) devices on which DroidMate will run. Currently supported values: api23"),
			CommandLineOption(widgetActionDelay, description = "Default delay to be applied after interacting with a widget (click, long click, tick)"),
			// Output
			CommandLineOption(outputDir, description = "Path to the directory that will contain DroidMate exploration output."),
			CommandLineOption(screenshotDir, description = "Path to the directory that will contain the screenshots from an exploration."),
			CommandLineOption(reportDir, description = "Path to the directory that will contain the report files."),
			// Strategies
			CommandLineOption(reset, description = "Enables use of the reset strategy during an exploration."),
			CommandLineOption(ConfigProperties.Strategies.explore, description = "Enables use of biased random exploration strategy."),
			CommandLineOption(terminate, description = "Enables use of default terminate strategy."),
			CommandLineOption(back, description = "Enables use of 'press back button' strategy"),
			CommandLineOption(allowRuntimeDialog, description = "Enables use of strategy to always click 'Allow' on permission dialogs."),
			CommandLineOption(denyRuntimeDialog, description = "Enables use of strategy to always click 'Deny' on permission dialogs."),
			CommandLineOption(playback, description = "Enables use of playback strategy (if a playback model is provided)."),
			CommandLineOption(ConfigProperties.Strategies.dfs, description = "Enables use of Depth-First-Search strategy."),
			CommandLineOption(rotateUI, description = "Enables use of Rotate UI strategy."),
			CommandLineOption(minimizeMaximize, description = "Enables use of Minimize-Maximize strategy to attempt to close the app and reopen it on the same screen."),
			CommandLineOption(textInput, description = "Enable use of Text Input Dictionary"),
			// Strategies parameters
			CommandLineOption(uiRotation, description = "Value of the UI rotation for Rotate UI strategy. Valid values are: 0, 90, 180, 270. Other values will be rounded to one of these."),

			// Selectors
			CommandLineOption(pressBackProbability, description = "Probability of randomly pressing the back button while exploring. Set to 0 to disable the press back strategy."),
			CommandLineOption(playbackModelDir, description = "Directory of a previous exploration model. Required for playback."),
			CommandLineOption(resetEvery, description = "Number of actions to automatically reset the exploration from its initial activity. Set to 0 to disable."),
			CommandLineOption(actionLimit, description = "How many actions the GUI exploration strategy can conduct before terminating."),
			CommandLineOption(timeLimit, description = "How long the exploration of any given apk should take, in minutes. If set to 0, instead actionsLimit will be used."),
			CommandLineOption(randomSeed, description = "The seed for a random generator used by a random-clicking GUI exploration strategy. If null, a seed will be randomized."),
			CommandLineOption(stopOnExhaustion, description = "Terminate exploration when all widgets have been explored at least 1x."),
			CommandLineOption(ConfigProperties.Selectors.dfs, description = "Use Depth-First-Search strategy, if the strategy is registered."),
			// Report
			CommandLineOption(inputDir, description = "Path to the directory containing report input. The input is to be DroidMate exploration output."),
			CommandLineOption(includePlots, description = "Include plots on reports (requires gnu plot)."),
			// UiAutomatorServer
			CommandLineOption(startTimeout, description = "How long DroidMate should wait, in milliseconds, for message on logcat confirming that UiAutomatorDaemonServer has started on android (virtual) device."),
			CommandLineOption(waitForIdleTimeout, description = "Timeout for a device to be idle an operation."),
			CommandLineOption(waitForInteractableTimeout, description = "Timeout for a widget to be available after an operation."),
			CommandLineOption(enablePrintOuts, description = "Enable or disable debug and performance outputs on the device output (in the LogCat)."),
			CommandLineOption(socketTimeout, description = "Socket timeout to communicate with the UiDaemonServer."),
			CommandLineOption(basePort, description = "The base port for the communication with the devices. DroidMate communicates over this base port + device index."),
			CommandLineOption(delayedImgFetch, description = "Option to allow for faster exploration by delaying screen-shot fetch to an asynchronous call."),
			CommandLineOption(imgQuality, description = "Quality of the image to be stored for fetching."),
			// StatementCoverage
			CommandLineOption(enableCoverage, description = "If true, the statement coverage of the exploration will be measured. This requires the apk to be instrumented with 'coverage' mode."),
			CommandLineOption(onlyCoverAppPackageName, description = "Only instrument statement coverage for statements belong inside the app package name scope. Libraries with other package names will be ignored. Be aware that this filtering might not be always correct."),
			CommandLineOption(coverageDir, description = "Path to the directory that will contain the coverage data."),
			CommandLineOption(org.droidmate.explorationModel.config.ConfigProperties.Output.debugMode, description = "enable debug output"),
			CommandLineOption(org.atua.modelFeatures.ATUAMF.Companion.RegressionStrategy.use , description = "If true, regression testing is used"),
			CommandLineOption(org.atua.modelFeatures.ATUAMF.Companion.RegressionStrategy.budgetScale, description = "Budget scale. Increase this for longer testing time."),
			CommandLineOption(org.atua.modelFeatures.ATUAMF.Companion.RegressionStrategy.reuseBaseModel, description = "Enable base model reuse."),
			CommandLineOption(org.atua.modelFeatures.ATUAMF.Companion.RegressionStrategy.baseModelDir, description = "Base model dir.")
			).first, fs)

	@Throws(ConfigurationException::class)
	override fun build(cmdLineConfig: Configuration, fs: FileSystem): ConfigurationWrapper {
		val defaultConfig = ConfigurationProperties.fromResource("defaultConfig.properties")

		val customFile = when {
			cmdLineConfig.contains(configPath) -> File(cmdLineConfig[configPath].path)
			defaultConfig.contains(configPath) -> File(defaultConfig[configPath].path)
			else -> null
		}

		val config : Configuration =
				// command line
				cmdLineConfig overriding
				// overrides custom config file
				(if (customFile?.exists() == true)
					ConfigurationProperties.fromFile(customFile)
				else
					cmdLineConfig) overriding
				// overrides default config file
				defaultConfig

		// Set the logging directory for the logback logger as early as possible
		val outputPath = Paths.get(config[outputDir].toString())
								.resolve(ConfigurationWrapper.log_dir_name)

		System.setProperty("logsDir", outputPath.toString())
		assert(System.getProperty("logsDir") == outputPath.toString())

		return memoizedBuildConfiguration(config, fs)
	}

	companion object {
		private val log by lazy { LoggerFactory.getLogger(ConfigurationBuilder::class.java) }

		@JvmStatic
		private fun memoizedBuildConfiguration(cfg: Configuration, fs: FileSystem): ConfigurationWrapper {
			log.debug("memoizedBuildConfiguration(args, fileSystem)")

			return bindAndValidate(ConfigurationWrapper(cfg, fs))
		}

		@JvmStatic
		@Throws(ConfigurationException::class)
		private fun bindAndValidate(config: ConfigurationWrapper): ConfigurationWrapper {
			try {
				setupResourcesAndPaths(config)
				validateExplorationSettings(config)
				normalizeAndroidApi(config)
			} catch (e: ConfigurationException) {
				throw e
			}

			logConfigurationInEffect(config)

			return config
		}

		@JvmStatic
		private fun normalizeAndroidApi(config: ConfigurationWrapper) {
			// Currently supports only API23 as configuration (works with API 24, 25 and 26 as well)
			assert(config[apiVersion] == ConfigurationWrapper.api23)
		}

		@JvmStatic
		private fun validateExplorationSettings(cfg: ConfigurationWrapper) {
			validateExplorationStrategySettings(cfg)

			val apkNames = Files.list(cfg.getPath(cfg[apksDir]))
					.filter { it.toString().endsWith(".apk") }
					.map { it.fileName.toString() }

			if (cfg[deployRawApks] && arrayListOf("inlined", "monitored").any { apkNames.anyMatch { s -> s.contains(it) } })
				throw ConfigurationException(
						"DroidMate was instructed to deploy raw apks, while the apks dir contains an apk " +
								"with 'inlined' or 'monitored' in its name. Please do not mix such apk with raw apks in one dir.\n" +
								"The searched apks dir path: ${cfg.getPath(cfg[apksDir]).toAbsolutePath()}")
		}

		@JvmStatic
		private fun validateExplorationStrategySettings(cfg: ConfigurationWrapper) {
			if (cfg[randomSeed] == -1L) {
				log.info("Generated random seed: ${cfg.randomSeed}")
			}
		}

		@JvmStatic
		private fun getCompiledResourcePath(cfg: ConfigurationWrapper,
											resourceName: String,
											compileCommand: (Path) -> Path): Path {
			val path = cfg.resourceDir.resolve(resourceName)

			if (!cfg[replaceResources] && Files.exists(path))
				return path

			return compileCommand.invoke(cfg.resourceDir)
		}

		@JvmStatic
		private fun getResourcePath(cfg: ConfigurationWrapper, resourceName: String): Path {
			val path = cfg.resourceDir.resolve(resourceName)

			if (!cfg[replaceResources] && Files.exists(path))
				return path

			return Resource(resourceName).extractTo(cfg.resourceDir)
		}

		@JvmStatic
		@Throws(ConfigurationException::class)
		private fun setupResourcesAndPaths(cfg: ConfigurationWrapper) {
			cfg.droidmateOutputDirPath = cfg.getPath(cfg[outputDir]).toAbsolutePath()
			cfg.resourceDir = cfg.droidmateOutputDirPath
					.resolve(EnvironmentConstants.dir_name_temp_extracted_resources)
			cfg.droidmateOutputReportDirPath = cfg.droidmateOutputDirPath
					.resolve(cfg[reportDir]).toAbsolutePath()
			cfg.reportInputDirPath = cfg.getPath(cfg[inputDir]).toAbsolutePath()

			cfg.uiautomator2DaemonApk = getResourcePath(cfg, "deviceControlDaemon.apk").toAbsolutePath()
			log.debug("Using uiautomator2-daemon.apk located at ${cfg.uiautomator2DaemonApk}")

			cfg.uiautomator2DaemonTestApk = getResourcePath(cfg, "deviceControlDaemon-test.apk").toAbsolutePath()
			log.debug("Using uiautomator2-daemon-test.apk located at ${cfg.uiautomator2DaemonTestApk}")

			cfg.monitorApk = try {
				if(!cfg[installMonitor]) null
				else getCompiledResourcePath(cfg, EnvironmentConstants.monitor_apk_name) {path ->
					val customApiFile = cfg.resourceDir.resolve("monitored_apis.json")
					val apiPath = if (Files.exists(customApiFile)) {
						customApiFile
					} else {
						null
					}
					org.droidmate.monitor.Compiler.compile(path, apiPath)
				}.toAbsolutePath()
			} catch (e:Throwable) {
				null
			}
			log.debug("Using ${EnvironmentConstants.monitor_apk_name} located at ${cfg.monitorApk}")

			cfg.apiPoliciesFile = try{
				getResourcePath(cfg, EnvironmentConstants.api_policies_file_name).toAbsolutePath()}
			catch (e:Throwable){
				null
			}
			log.debug("Using ${EnvironmentConstants.api_policies_file_name} located at ${cfg.apiPoliciesFile}")

			cfg.apksDirPath = cfg.getPath(cfg[apksDir]).toAbsolutePath()

			Files.createDirectories(cfg.apksDirPath)
			log.debug("Reading APKs from: ${cfg.apksDirPath.toAbsolutePath()}")

			if (Files.notExists(cfg.droidmateOutputDirPath)) {
				Files.createDirectories(cfg.droidmateOutputDirPath)
				log.info("Writing output to: ${cfg.droidmateOutputDirPath}")
			}
		}

		/*
* To keep the source DRY, we use apache's ReflectionToStringBuilder, which gets the field names and values using
* reflection.
*/
		@JvmStatic
		private fun logConfigurationInEffect(config: Configuration) {

			// The customized display style strips the output of any data except the field name=value pairs.
			val displayStyle = StandardToStringStyle()
			displayStyle.isArrayContentDetail = true
			displayStyle.isUseClassName = false
			displayStyle.isUseIdentityHashCode = false
			displayStyle.contentStart = ""
			displayStyle.contentEnd = ""
			displayStyle.fieldSeparator = System.lineSeparator()

			val configurationDump = ReflectionToStringBuilder(config, displayStyle).toString()
				.split(System.lineSeparator())
				.sorted()

			val sb = StringBuilder()
			sb.appendln("--------------------------------------------------------------------------------")
				.appendln("Working dir:   ${System.getProperty("user.dir")}")
				.appendln("")
				.appendln("JVM arguments: ${readJVMArguments()}")
				.appendln("")
				.appendln("Configuration dump:")
				.appendln("")

			configurationDump.forEach { sb.appendln(it) }

			sb.appendln("")
				.appendln("End of configuration dump")
				.appendln("--------------------------------------------------------------------------------")

			log.debug(runData, sb.toString())
		}

		/**
		 * Based on: http://stackoverflow.com/a/1531999/986533
		 */
		@JvmStatic
		private fun readJVMArguments(): List<String> = ManagementFactory.getRuntimeMXBean().inputArguments
	}

}
