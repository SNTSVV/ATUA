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

// "unused" warning is suppressed because vals in this project are being used in the 'droidmate' project gradle build scripts
// as well as in the 'droidmate' project itself. The "unused" warning doesn't properly recognize some of the usages.
@file:Suppress("unused")
//TODO ensure all gradle scripts using these constants get cleaned up and that they are only derived/used at runtime, then remove buildSrc alltogether
package org.droidmate.buildsrc

import com.konradjamrozik.*
import org.zeroturnaround.exec.ProcessExecutor
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit

private val exeExt = if (OS.isWindows) ".exe" else ""

//region Values directly based on system environment variables
val java_home = "JAVA_HOME".asEnvDir
val android_sdk_dir = "ANDROID_HOME".asEnvDir
//endregion

val jarsigner_relative_path = "bin/jarsigner$exeExt"
val jarsigner = java_home.resolveRegularFile(jarsigner_relative_path)

//region Android SDK components
//$ANDROID_HOME/build-tools/ lists available versions -> we want to find the highest installed version
var max:Pair<String,Int> = Pair("",0)
val buildTools = Files.list(android_sdk_dir.resolve("build-tools")).use { file ->
	println("available build-tools: ")
	file.forEach {
	val fileName = it.fileName.toString()
	println("\t$fileName")
	val versionCmp = fileName.replace(".","").toIntOrNull()
	if(versionCmp!=null && versionCmp > max.second)
		max = Pair(fileName,versionCmp)
} }

private val build_tools_version = max.first
val aapt_command_relative = "build-tools/$build_tools_version/aapt$exeExt"
val adb_command_relative = "platform-tools/adb$exeExt"
val aapt_command = android_sdk_dir.resolveRegularFile(aapt_command_relative)
val adb_command = android_sdk_dir.resolveRegularFile(adb_command_relative)

//jitpack debugging list all files in android home
val debug = Files.list(android_sdk_dir).use {  file ->
	println("available directories in AndroidHome: ")
	file.forEach {
		val fileName = it.fileName.toString()
		println("\t$fileName")
	}
}

//endregion

const val monitor_generator_res_name_monitor_template = "monitorTemplate.txt"
private const val monitor_generator_output_dir = "temp"
fun generated_monitor(apiLevel: Int): String {
	return "$monitor_generator_output_dir/generated_Monitor_api$apiLevel.java"
}

val monitor_generator_output_relative_path_api23 = generated_monitor(23)

val apk_inliner_param_input_default = Paths.get("input-apks")
val apk_inliner_param_output_dir_default = Paths.get("output-apks")
val apk_inliner_param_input = "-input"
val apk_inliner_param_output_dir = "-outputDir"
val AVD_dir_for_temp_files = "/data/local/tmp/"

const val uia2_daemon_project_name = "deviceControlDaemon"
val uia2_daemon_relative_project_dir = File("project${File.separator}deviceComponents", uia2_daemon_project_name)

const val monitored_apk_fixture_api23_name = "MonitoredApkFixture_api23-debug.apk"
val monitored_inlined_apk_fixture_api23_name = "${monitored_apk_fixture_api23_name.removeSuffix(".apk")}-inlined.apk"

const val monitor_api23_apk_name = "monitor_api23.apk"
const val monitor_on_avd_apk_name = "monitor.apk"
const val api_policies_file_name = "api_policies.txt"
const val monitor_port_file_name = "monitor_port.tmp"
const val coverage_port_file_name = "coverage_port.tmp"
/**
 * Denotes name of directory containing apk fixtures for testing. The handle to this path is expected to be obtained
 * in following ways:
 *
 * From a build.gradle script:
 *
 *   new File(sourceSets.test.resources.srcDirs[0], <this_var_reference>)
 *
 * From compiled source code:
 *
 *   new Resource("<this_var_reference>").extractTo(fs.getPath(BuildConstants.dir_name_temp_extracted_resources))
 */
const val apk_fixtures = "fixtures/apks"

val test_temp_dir_name = "out${File.separator}temp_dir_for_tests"

/**
 * Directory for resources extracted from jars in the classpath.
 *
 * Some resources have to be extracted to a directory. For example, an .apk file that is inside a .jar needs to be pushed
 * to a device.
 */
const val dir_name_temp_extracted_resources = "temp_extracted_resources"

// !!! DUPLICATION WARNING !!! with org.droidmate.MonitorConstants.monitor_time_formatter_locale 
val locale = Locale.US

fun executeCommand(commandName: String, commandContent: String): Int {

	val cmd = if (OS.isWindows) "cmd /c " else ""
	val commandString = cmd + commandContent

	println("=========================")
	println("Executing command named: $commandName")
	println("Command string:")
	println(commandString)

	val err = ByteArrayOutputStream()
	val out = ByteArrayOutputStream()
	val process = ProcessExecutor()
			.readOutput(true)
			.redirectOutput(out)
			.redirectError(err)
			.timeout(120, TimeUnit.SECONDS)

	print("executing...")
	val result = process.commandSplit(commandString).execute()
	println(" DONE")

	println("return code: ${result.exitValue}")
	val stderrContent = err.toString(Charsets.UTF_8.toString())
	val stdoutContent = out.toString(Charsets.UTF_8.toString())

	if (stderrContent != "") {
		println("----------------- stderr:")
		println(stderrContent)
		println("----------------- /stderr")
	} else
		println("stderr is empty")

	if (stdoutContent != "") {
		if (result.exitValue == 0)
			println("stdout is ${stdoutContent.length} chars long")
		else {
			println("----------------- stdout:")
			println(stdoutContent)
			println("----------------- /stderr")
		}
	} else
		println("stdout is empty")

	println("=========================")
	return result.exitValue
}
