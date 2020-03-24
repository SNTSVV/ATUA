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
package org.droidmate.command.exploration

import org.droidmate.test_tools.DroidmateTestCase
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class ExplorationTest : DroidmateTestCase() {
	// TODO Fix tests
	@Test
	fun dummy() {
	}

	// it fails because the device simulator now always uses UnreliableGuiSnapshot and the code doesn't handle it yet.
	// after it passes 'app has stopped' dialog box, work on providing empty gui snapshots, within the retry attempts, of course.
	/*@Category(RequiresSimulator::class)
	@Test
	fun `Runs on simulator`() {
			val simulatorSpec = "s1-w1->s1"
			runOnSimulator(simulatorSpec)
	}

	@Category(RequiresSimulator::class)
	@Test
	fun `Supports external app displayed after explored app reset`() {
			val simulatorSpec = "s1-w1->chrome"
			runOnSimulator(simulatorSpec)
	}

	@Category(RequiresSimulator::class)
	@Test
	fun `Supports external app displayed after exploration termination`() {
			val simulatorSpec = "s1-w1->chrome"
			val cfg = ConfigurationForTests().setArgs(arrayListOf(Configuration.pn_actionsLimit, "2")).get()
			runOnSimulator(simulatorSpec, ArrayList(), cfg)
	}

	@Category(RequiresDevice::class)
	@Test
	fun `Collects monitored API calls logs during device exploration`() {
			val cfg = ConfigurationForTests().forDevice().setArgs(arrayListOf(
							Configuration.pn_apksNames, "[${EnvironmentConstants.monitored_inlined_apk_fixture_api23_name}]",
							Configuration.pn_widgetIndexes, "[0]"
			)).get()

			// Configuration cfg =  ConfigurationBuilder().build(args)
			val deviceTools = DeviceTools(cfg)

			val apk = SingleApkFixture(deviceTools.aapt, cfg)

			val exploration = Exploration.build(cfg)

			val outData: MutableList<ExplorationContext?> = ArrayList()

			deviceTools.deviceDeployer.setupAndExecute("", 0) { device ->
					deviceTools.apkDeployer.withDeployedApk(device, apk) { deployedApk ->

							// Act
							val explData = exploration.execute(deployedApk, RobustDevice(device, cfg))
							outData.update(explData.result)
							explData.exception as Any
					}
			}

			assert(outData.isNotEmpty())
			val out = outData.first()

			val apiLogs = MonitoredInlinedApkFixtureApiLogs(extractApiLogsList(out!!.logRecords))
			apiLogs.assertCheck()
	}

	/**
	 * <p>
	 * Bug: Assertion error in Exploration#tryAssertDeviceHasPackageInstalled
	 *
	 * </p><p>
	 * The call to
	 * <pre>IExplorableAndroidDevice#hasPackageInstalled(java.lang.String)</pre>
	 * returns false, causing an assert to fail.
	 *
	 * </p><p>
	 * https://hg.st.cs.uni-saarland.de/issues/994
	 *
	 * </p>
	 */
	@Category(RequiresSimulator::class)
	@Test
	fun `Has no bug #994`() {
			val simulatorSpec = "s1-w1->s1"
			val failableOut = runOnSimulator(simulatorSpec, arrayListOf(ExceptionSpec("hasPackageInstalled", "", 1, false, false)))
			assert(failableOut.result == null)
			assert(failableOut.exception != null)
	}

	private fun runOnSimulator(simulatorSpec: String,
														 exceptionSpecs: List<IExceptionSpec> = ArrayList(),
														 cfg: Configuration = ConfigurationForTests().get()): Failable<ExplorationContext, DeviceException> {
			val timeGenerator = TimeGenerator()

			val apk = ApkTestHelper.build("mock_app1")
			val simulator = AndroidDeviceSimulator(timeGenerator, arrayListOf(apk.packageName), simulatorSpec, exceptionSpecs)
			val simulatedDevice = RobustDevice(simulator, cfg)

			val exploration = Exploration.build(cfg, timeGenerator)

			// Act
			val failableOut = exploration.execute(apk, simulatedDevice)

			if (failableOut.result != null) {
					assert(!failableOut.result!!.exceptionIsPresent)

					val out2Simulation = DeviceSimulation(failableOut.result!!)
					val expectedSimulation = simulator.currentSimulation
					out2Simulation.assertEqual(expectedSimulation!!)
			}
			return failableOut

	}

	private fun extractApiLogsList(actions: List<ExplorationRecord>): List<List<IApiLogcatMessage>> =
					actions.map { pair -> pair.getResult().deviceLogs.apiLogs }*/
}