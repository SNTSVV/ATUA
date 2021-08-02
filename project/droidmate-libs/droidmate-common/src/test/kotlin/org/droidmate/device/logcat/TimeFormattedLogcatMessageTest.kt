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

package org.droidmate.device.logcat

import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(JUnit4::class)
class TimeFormattedLogcatMessageTest {

    @Test
    fun `Round-trips`() {
        arrayListOf(
            "12-23 09:27:52.758123 I/System.out(  937): key length:16",

            "12-22 20:30:01.440234 I/PMBA    (  483): Previous metadata 937116 mismatch vs 1227136 - rewriting",

            "12-22 20:30:34.190456 D/dalvikvm( 1537): GC_CONCURRENT freed 390K, 5% free 9132K/9572K, paused 17ms+5ms, total 65ms",

            "12-22 21:08:44.768798 W/ContextImpl(13377): Calling a method in the system process without a qualified user: " +
                    "android.app.ContextImpl.startService:1487 android.content.ContextWrapper.startService:494 " +
                    "android.content.ContextWrapper.startService:494 com.android.keychain.KeyChainBroadcastReceiver.onReceive:12 " +
                    "android.app.ActivityThread.handleReceiver:2407",

            "12-23 04:55:01.788478 D/Finsky  (18596): [1] FinskyApp.onCreate: Initializing network with DFE " +
                    "https://android.clients.google.com/fdfe/"
        ).forEach {
            assert(TimeFormattedLogcatMessage.from(it).toLogcatMessageString == it)
        }
    }
}