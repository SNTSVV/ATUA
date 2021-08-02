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

package org.droidmate.device.error

import org.droidmate.misc.DroidmateException

open class DeviceException : DroidmateException {
    companion object {
        private const val serialVersionUID: Long = 1
    }

    val stopFurtherApkExplorations: Boolean

    constructor() : super() {
        stopFurtherApkExplorations = false
    }

    constructor(cause: Throwable) : super(cause) {
        stopFurtherApkExplorations = false
    }

    constructor(message: String) : super(message) {
        stopFurtherApkExplorations = false
    }

    constructor(message: String, stopFurtherApkExplorations: Boolean) : super(message) {
        this.stopFurtherApkExplorations = stopFurtherApkExplorations
    }

    @JvmOverloads
    constructor(message: String, cause: Throwable, stopFurtherApkExplorations: Boolean = false) : super(
        message,
        cause
    ) {
        this.stopFurtherApkExplorations = stopFurtherApkExplorations
    }
}
