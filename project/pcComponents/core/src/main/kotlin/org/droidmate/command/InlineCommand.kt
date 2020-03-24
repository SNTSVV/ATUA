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

package org.droidmate.command

import org.droidmate.device.android_sdk.AaptWrapper
import org.droidmate.monitor.ApkInliner
import org.droidmate.configuration.ConfigurationWrapper
import org.droidmate.device.android_sdk.Apk
import org.droidmate.misc.FailableExploration
import org.droidmate.misc.SysCmdExecutor
import org.droidmate.tools.ApksProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

class InlineCommand @JvmOverloads constructor(cfg: ConfigurationWrapper,
                                              private val inliner: ApkInliner = ApkInliner(cfg.resourceDir)) {
	companion object {
		@JvmStatic
		private val log: Logger by lazy { LoggerFactory.getLogger(this::class.java) }

		@JvmStatic
		private fun moveOriginal(apk: Apk, originalsDir: Path) {
			val original = originalsDir.resolve(apk.fileName)

			if (!Files.exists(original)) {
				Files.move(apk.path, original)
				log.info("Moved ${original.fileName} to '${originalsDir.fileName}' sub dir.")
			} else {
				log.info("Skipped moving ${original.fileName} to '${originalsDir.fileName}' sub dir: it already exists there.")
			}
		}
	}

	fun execute(cfg: ConfigurationWrapper): Map<Apk, FailableExploration> {
		val apksProvider = ApksProvider(AaptWrapper())
		val apks = apksProvider.getApks(cfg.apksDirPath, 0, ArrayList(), false)

		if (apks.all { it.inlined }) {
			log.warn("No non-inlined apks found. Aborting.")
			return emptyMap()
		}

		val originalsDir = cfg.apksDirPath.resolve("originals").toAbsolutePath()
		Files.createDirectories(originalsDir)

		apks.filter { !it.inlined }.forEach { apk ->
			inliner.instrumentApk(apk.path, apk.path.parent)
			log.info("Inlined ${apk.fileName}")
			moveOriginal(apk, originalsDir)
		}

		return emptyMap()
	}
}
