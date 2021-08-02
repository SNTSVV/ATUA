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

package org.droidmate.misc

import kotlinx.coroutines.delay
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.droidmate.deviceInterface.exploration.DeviceResponse

class Utils {
    companion object {
        private val log by lazy { LoggerFactory.getLogger(Utils::class.java) }

        @JvmStatic
        @Throws(Throwable::class)
        suspend fun <T> retryOnException(
            target: suspend () -> T,
            beforeRetryCommand: suspend () -> Any,
            attempts: Int,
            internalDelay: Int,
            targetName: String
        ): T {
            assert(attempts > 0)
            var attemptsLeft = attempts
            var succeeded = false
            var exception: Throwable? = null
            var out: T? = null

            while (!succeeded && attemptsLeft-- > 0) {
                try {
                    out = target.invoke()
                    succeeded = (out as? DeviceResponse)?.isSuccessful ?: true
                    exception = (out as? DeviceResponse)?.throwable
                } catch (e: Throwable) {
                    beforeRetryCommand()

                    exception = e

                    if (attemptsLeft > 0) {
                        log.debug("Discarded $e from \"$targetName\". Sleeping for $internalDelay and retrying.")
                        delay(internalDelay.toLong())
                    } else
                        throw e
                }
            }

            return if (exception != null)
                throw exception
            else
                out!!
        }

        @JvmStatic
        @Throws(Throwable::class)
        suspend fun retryOnFalse(target: suspend () -> Boolean, attempts: Int, internalDelay: Int): Boolean {
            assert(attempts > 0)
            var attemptsLeft = attempts

            var succeeded = target.invoke()
            attemptsLeft--
            while (!succeeded && attemptsLeft > 0) {
                delay(internalDelay.toLong())
                succeeded = target.invoke()
                attemptsLeft--
            }

            assert((attemptsLeft <= 0) || succeeded)
            return succeeded
        }

        @JvmStatic
        @Throws(Throwable::class)
        suspend fun <T> retryOnFalse(target: suspend () -> T, validator: (T) -> Boolean, attempts: Int, internalDelay: Int): T {
            assert(attempts > 0)
            var attemptsLeft = attempts

            var value = target.invoke()
            var succeeded = validator.invoke(value)
            attemptsLeft--
            while (!succeeded && attemptsLeft > 0) {
                delay(internalDelay.toLong())
                value = target.invoke()
                succeeded = validator.invoke(value)
                attemptsLeft--
            }

            assert((attemptsLeft <= 0) || succeeded)
            return value
        }

        @JvmStatic
        fun quoteIfIsPathToExecutable(path: String): String {
            return if (SystemUtils.IS_OS_WINDOWS) {
                if (Files.isExecutable(Paths.get(path)))
                    "\"$path\""
                else
                    path
            } else {
                path
            }
        }

        @JvmStatic
        fun quoteAbsolutePaths(stringArray: Array<String>): Array<String> {
            stringArray.forEachIndexed { idx, it ->
                if (File(it).isAbsolute)
                    stringArray[idx] = "\"$it\""
            }
            return stringArray
        }
    }
}
