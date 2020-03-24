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
package org.droidmate.test_tools.device_simulation

import org.droidmate.deviceInterface.exploration.DeviceResponse
import org.droidmate.deviceInterface.exploration.ExplorationAction

/**
 * <p>
 * The time generator provides successive timestamps to the logs returned by the simulated device from a call to
 * {@link #perform(org.droidmate.uiautomator_daemon.guimodel.ExplorationAction)}.
 *
 * </p><p>
 * If this object s a part of simulation obtained from exploration output the time generator is null, as no time needs to be
 * generated. Instead, all time is obtained from the exploration output timestamps.
 *
 * </p>
 */
class GuiScreen /*constructor(private val internalId: String,
                            packageName : String = "", 
                            private val timeGenerator : ITimeGenerator? = null)*/ // TODO Fix tests
	: IGuiScreen {
	override fun perform(action: ExplorationAction): IScreenTransitionResult {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getGuiSnapshot(): DeviceResponse {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getId(): String {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun addHomeScreenReference(home: IGuiScreen) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun addMainScreenReference(main: IGuiScreen) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun addWidgetTransition(widgetId: String, targetScreen: IGuiScreen) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun addWidgetTransition(widgetId: String, targetScreen: IGuiScreen, ignoreDuplicates: Boolean) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun buildInternals() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun verify() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}
	// TODO Fix tests
	//private static final String packageAndroidLauncher = new DeviceConfigurationFactory(DeviceConstants.DEVICE_DEFAULT).getConfiguration().getPackageAndroidLauncher()
	/*companion object {
			const val idHome = "home"
			const val idChrome = "chrome"
			val reservedIds = arrayListOf(idHome, idChrome)
			val reservedIdsPackageNames = mapOf(
							idHome to DeviceModel.buildDefault().getAndroidLauncherPackageName(),
							idChrome to "com.android.chrome")

			fun getSingleMatchingWidget(action: ClickAction, widgets: List<Widget>): Widget {
					return widgets.find { w->w.xpath==action.xPath }!!
			}

			fun getSingleMatchingWidget(action: CoordinateClickAction, widgets: List<Widget>): Widget {
					return widgets.find { w->w.bounds.contains(action.x, action.y) }!!
			}

	}

	private val packageName: String
	private var internalGuiSnapshot: IDeviceGuiSnapshot = MissingGuiSnapshot()

	private var home: IGuiScreen? = null
	private var main: IGuiScreen? = null

	private val widgetTransitions: MutableMap<Widget, IGuiScreen> = mutableMapOf()
	private var finishedBuilding = false

	constructor(snapshot: IDeviceGuiSnapshot) : this(snapshot.id, snapshot.getPackageName()) {
			this.internalGuiSnapshot = snapshot
	}


	initialize {
			this.packageName = if (packageName.isNotEmpty()) packageName else reservedIdsPackageNames[internalId]!!

			assert(this.internalId.isNotEmpty())
			assert(this.packageName.isNotEmpty())
			assert((this.internalId !in reservedIds) || (this.packageName == reservedIdsPackageNames[internalId]))
			assert((this.internalId in reservedIds) || (this.packageName !in reservedIdsPackageNames.values))
	}

	override fun perform(action: ExplorationAction): IScreenTransitionResult {
			assert(finishedBuilding)
			return when (action) {
			// TODO review
					is SimulationAdbClearPackageAction -> internalPerform(action)
					is LaunchAppAction -> internalPerform(action)
					is ClickAction -> internalPerform(action)
					is CoordinateClickAction -> internalPerform(action)
					is LongClickAction -> internalPerform(action)
					is CoordinateLongClickAction -> internalPerform(action)
					else -> throw UnsupportedMultimethodDispatch(action)
			}
	}

	//region internalPerform multimethod

	// This method is used: it is a multimethod.
	private fun internalPerform(clearPackage: SimulationAdbClearPackageAction): IScreenTransitionResult {
			return if (this.getGuiSnapshot().getPackageName() == clearPackage.packageName)
					ScreenTransitionResult(home!!, ArrayList())
			else
					ScreenTransitionResult(this, ArrayList())
	}

	@Suppress("UNUSED_PARAMETER")
	private fun internalPerform(launch: LaunchAppAction): IScreenTransitionResult =
					ScreenTransitionResult(main!!, this.buildMonitorMessages())

	private fun internalPerform(action: ExplorationAction): IScreenTransitionResult {
			return when (action) {
					is PressHomeAction -> ScreenTransitionResult(home!!, ArrayList())
					is EnableWifiAction -> {
							assert(this == home)
							ScreenTransitionResult(this, ArrayList())
					}
					is PressBackAction -> ScreenTransitionResult(this, ArrayList())
					is ClickAction -> {
							val widget = getSingleMatchingWidget(action, widgetTransitions.keys.toList())
							ScreenTransitionResult(widgetTransitions[widget]!!, ArrayList())
					}
					is CoordinateClickAction -> {
							val widget = getSingleMatchingWidget(action, widgetTransitions.keys.toList())
							ScreenTransitionResult(widgetTransitions[widget]!!, ArrayList())
					}
					else -> throw UnexpectedIfElseFallthroughError("Found action $action")
			}
	}

	//endregion internalPerform multimethod

	override fun addWidgetTransition(widgetId: String, targetScreen: IGuiScreen, ignoreDuplicates: Boolean) {
			assert(!finishedBuilding)
			assert(this.internalId !in reservedIds)
			assert(ignoreDuplicates || !(widgetTransitions.keys.any { it.id.contains(widgetId) }))

			if (!(ignoreDuplicates && widgetTransitions.keys.any { it.id.contains(widgetId) })) {
					val widget = if (this.getGuiSnapshot() !is MissingGuiSnapshot)
							this.getGuiSnapshot().guiState.widgets.single { it.id == widgetId }
					else
							WidgetTestHelper.newClickableWidget(mutableMapOf("uid" to widgetId), /* widgetGenIndex */ widgetTransitions.keys.size)

					widgetTransitions[widget] = targetScreen
			}

			assert(widgetTransitions.keys.any { it.id.contains(widgetId) })
	}

	override fun addHomeScreenReference(home: IGuiScreen) {
			assert(!finishedBuilding)
			assert(home.getId() == idHome)
			this.home = home
	}

	override fun addMainScreenReference(main: IGuiScreen) {
			assert(!finishedBuilding)
			assert(main.getId() !in reservedIds)
			this.main = main
	}

	override fun buildInternals() {
			assert(!this.finishedBuilding)
			assert(this.getGuiSnapshot() is MissingGuiSnapshot)

			val widgets = widgetTransitions.keys
			when (internalId) {
					!in reservedIds -> {
							val guiState = if (widgets.isEmpty()) {
									buildEmptyInternals()
							} else
									GuiStateTestHelper.newGuiStateWithWidgets(
													widgets.size, packageName, /* enabled */ true, internalId, widgets.map { it.id })

							this.internalGuiSnapshot = UiautomatorWindowDumpTestHelper.fromGuiState(guiState)

					}
					idHome -> this.internalGuiSnapshot = UiautomatorWindowDumpTestHelper.newHomeScreenWindowDump(this.internalId)
					idChrome -> this.internalGuiSnapshot = UiautomatorWindowDumpTestHelper.newAppOutOfScopeWindowDump(this.internalId)
					else -> throw UnexpectedIfElseFallthroughError("Unsupported reserved uid: $internalId")
			}

			assert(this.getGuiSnapshot().id.isNotEmpty())
	}

	private fun buildEmptyInternals(): DeviceResponse {
			val guiState = GuiStateTestHelper.newGuiStateWithTopLevelNodeOnly(packageName, internalId)
			// This one widget is necessary, as it is the only xml element from which packageName can be obtained. Without it, following
			// method would fail: UiautomatorWindowDump.getPackageName when called on
			// org.droidmate.exploration.device.simulation.GuiScreen.guiSnapshot.
			assert(guiState.widgets.size == 1)
			return guiState
	}

	override fun verify() {
			assert(!finishedBuilding)
			this.finishedBuilding = true

			assert(this.home?.getId() == idHome)
			assert(this.main?.getId() !in reservedIds)
			assert(this.getGuiSnapshot().id.isNotEmpty())
			assert(this.getGuiSnapshot().guiState.id.isNotEmpty())
			// TODO: Review later
			//assert((this.internalId in reservedIds) || (this.widgetTransitions.keys.map { it.uid }.sorted() == this.getGuiSnapshot().guiStatus.getActionableWidgets().map { it.uid }.sorted()))
			assert(this.finishedBuilding)
	}

	private fun buildMonitorMessages(): List<TimeFormattedLogMessageI> {
			return listOf(
							TimeFormattedLogcatMessage.from(
											this.timeGenerator!!.shiftAndGet(mapOf("milliseconds" to 1500)), // Milliseconds amount based on empirical evidence.
											MonitorConstants.loglevel.toUpperCase(),
											MonitorConstants.tag_mjt,
											"4224", // arbitrary process ID
											MonitorConstants.msg_ctor_success),
							TimeFormattedLogcatMessage.from(
											this.timeGenerator.shiftAndGet(mapOf("milliseconds" to 1810)), // Milliseconds amount based on empirical evidence.
											MonitorConstants.loglevel.toUpperCase(),
											MonitorConstants.tag_mjt,
											"4224", // arbitrary process ID
											MonitorConstants.msgPrefix_init_success + this.packageName)
			)
	}

	override fun toString(): String {
			return MoreObjects.toStringHelper(this)
							.update("uid", internalId)
							.toString()
	}

	override fun getId(): String = this.internalId

	override fun getGuiSnapshot(): IDeviceGuiSnapshot = this.internalGuiSnapshot

	override fun addWidgetTransition(widgetId: String, targetScreen: IGuiScreen) {
			addWidgetTransition(widgetId, targetScreen, false)
	}*/
}