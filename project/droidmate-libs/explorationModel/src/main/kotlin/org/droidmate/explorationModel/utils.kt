/*
 * Copyright (c) 2019.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

@file:Suppress("unused")

package org.droidmate.explorationModel

import com.natpryce.konfig.ConfigurationProperties
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import java.io.File
import java.io.Serializable
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.measureNanoTime

data class ConcreteId(val uid: UUID, val configId: UUID): Serializable {
	override fun toString(): String = "${uid}_$configId"

	companion object {
		fun fromString(s: String): ConcreteId? =
				if(s == "null") null else s.split("_").let { ConcreteId(UUID.fromString(it[0]), UUID.fromString(it[1])) }
	}
}

internal operator fun UUID.plus(uuid: UUID?): UUID {
	return if(uuid == null) this
	else UUID(this.mostSignificantBits + uuid.mostSignificantBits, this.leastSignificantBits + uuid.mostSignificantBits)
}
internal operator fun UUID.plus(id: Int): UUID {
	return UUID(this.mostSignificantBits + id, this.leastSignificantBits + id)
}

fun String.toUUID(): UUID = UUID.nameUUIDFromBytes(trim().toLowerCase().toByteArray(Charset.forName("UTF-8")))
fun Int.toUUID(): UUID = UUID.nameUUIDFromBytes(toString().toByteArray(Charset.forName("UTF-8")))
fun center(c:Int, d:Int):Int = c+(d/2)

val emptyUUID: UUID = UUID.nameUUIDFromBytes(byteArrayOf())
fun String.asUUID(): UUID? = if(this == "null") null else UUID.fromString(this)
//typealias ConcreteId = Pair<UUID, UUID>
//fun ConcreteId.toString() = "${first}_$second"  // mainly for nicer debugging strings
/** custom dumpString method used for model dump & load **/
//fun ConcreteId.dumpString() = "${first}_$second"
val emptyId = ConcreteId(emptyUUID, emptyUUID)

/** string sanitation functions */
fun String.removeNewLineAndSemicolon() = replace(Regex("\\r\\n|\\r|\\n")," ").replace(";"," ")

fun String.sanitize(): String =
	removeNewLineAndSemicolon()
		.replace("<newline>", " ").replace("<semicolon>"," ")
		.replace("\\s+", " ").splitOnCaseSwitch().split(" ").distinct().filter { it.isNotBlank() }
		.joinToString(separator = " ") { it.trim() }  // erase redundant spaces

fun String.splitOnCaseSwitch(): String{
	if(this.isBlank()) return ""
	var newString = ""
	this.forEachIndexed { i, c ->
		newString += when{
			c.isWhitespace() || c=='_' || c=='/' || c=='.' || c=='-' || c==',' -> " "
			!c.isLetter() -> ""
			c.isUpperCase() && i>0 && this[i-1].isLowerCase() -> " $c"
			else -> c
		}
	}
	return newString
}

fun String.replaceNewLine() = replace(Regex("\\r\\n|\\r|\\n"),"<newline>")


private const val datePattern = "ddMM-HHmmss"
internal fun timestamp(): String = DateTimeFormatter.ofPattern(datePattern).format(LocalDateTime.now())

/** for performance measurement or computation printouts we often are only interested in the first two digits */
fun Double.twoDigitString() = String.format("%.2f", this)
fun Double.oneDigitString() = String.format("%.1f", this)

/** debug functions */

var debugOutput = true
var measurePerformance = true

@Suppress("ConstantConditionIf")
fun debugOut(msg:String, enabled: Boolean = true) { if (debugOutput && enabled) println(msg) }

inline fun <T> nullableDebugT(msg: String, block: () -> T?, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T? {
	var res: T? = null
	@Suppress("ConstantConditionIf")
	if (measurePerformance) {
		measureNanoTime {
			res = block.invoke()
		}.let {
			timer(it)
			println("time ${if (inMillis)(it / 1000000.0).twoDigitString() +" ms" else (it / 1000.0).twoDigitString() + " ns/1000"} \t $msg")
		}
	} else res = block.invoke()
	return res
}

inline fun <T> debugT(msg: String, block: () -> T, timer: (Long) -> Unit = {}, inMillis: Boolean = false): T {
	return nullableDebugT(msg, block, timer, inMillis)!!
}

fun Collection<Rectangle>.firstCenter() = firstOrNull()?.center
fun Collection<Rectangle>.firstOrEmpty() = firstOrNull() ?: Rectangle(0,0,0,0)

internal class UiElementP( properties: Map<String,Any?>) : UiElementPropertiesI {
	/** no meta information is persisted */
	override val metaInfo: List<String> = emptyList()

	override val isKeyboard: Boolean by properties
	override val hintText: String by properties
	override val inputType: Int by properties
	override val text: String by properties
	override val contentDesc: String by properties
	override val resourceId: String by properties
	override val className: String by properties
	override val packageName: String by properties
	override val isInputField: Boolean by properties
	override val isPassword: Boolean by properties
	override val visibleBounds: Rectangle by properties
	override val boundaries: Rectangle by properties
	override val clickable: Boolean by properties
	override val checked: Boolean? by properties
	override val longClickable: Boolean by properties
	override val focused: Boolean? by properties
	override val selected: Boolean by properties
	override val scrollable: Boolean by properties
	override val xpath: String by properties
	override val idHash: Int by properties
	override val parentHash: Int by properties
	override val childHashes: List<Int> by properties
	override val definedAsVisible: Boolean by properties
	override val enabled: Boolean by properties
	override val imgId: Int by properties
	override val visibleAreas: List<Rectangle> by properties
	override val hasUncoveredArea: Boolean by properties
	override val hasClickableDescendant: Boolean by properties
}

object DummyProperties: UiElementPropertiesI {
	override val hintText: String = "Dummy-hintText"
	override val inputType: Int = 0
	override val imgId: Int = 0
	override val visibleBounds: Rectangle = Rectangle(0,0,0,0)
	override val hasUncoveredArea: Boolean = false
	override val boundaries: Rectangle = Rectangle(0,0,0,0)
	override val visibleAreas: List<Rectangle> = listOf(Rectangle(0,0,0,0))
	override val metaInfo: List<String> = emptyList()
	override val isKeyboard: Boolean = false
	override val text: String = "Dummy-Widget"
	override val contentDesc: String = "No-contentDesc"
	override val checked: Boolean? = null
	override val resourceId: String = "No-resourceId"
	override val className: String = "No-className"
	override val packageName: String = "No-packageName"
	override val enabled: Boolean = false
	override val isInputField: Boolean = false
	override val isPassword: Boolean = false
	override val clickable: Boolean = false
	override val longClickable: Boolean = false
	override val scrollable: Boolean = false
	override val focused: Boolean? = null
	override val selected: Boolean = false
	override val definedAsVisible: Boolean = false
	override val xpath: String = "No-xPath"
	override val idHash: Int = 0
	override val parentHash: Int = 0
	override val childHashes: List<Int> = emptyList()
	override val hasClickableDescendant: Boolean = false
}

/**
 * This function loads the configuration from the resource defined by [resourcePath].
 * If this function is called from code within a jar dependency,
 * the file is temporarily extracted and deleted after it was loaded successfully.
 */
fun ConfigurationProperties.Companion.configFromResource(resourcePath: String): ConfigurationProperties =
	if (ClassLoader.getSystemResources(resourcePath).toList().isEmpty()) {
		// this module is part of a jar dependency, therefore we have to copy the resource to a file first (or fix the 'konfig' library)
		val inS = this::class.java.getResourceAsStream(resourcePath)
			?: throw RuntimeException("cannot load resource file $resourcePath")

		// input stream from resource loaded, write to tmp file
		val tmpFile = File("tmp_res-modelConfig.properties")
		inS.use { resource ->
			tmpFile.outputStream().use { fileOut ->
				resource.copyTo(fileOut)
			}
		}
		// load configuration from tmp file
		ConfigurationProperties.Companion.fromFile(tmpFile).also {
			tmpFile.delete()
		}
	} else {
		ConfigurationProperties.Companion.fromResource(resourcePath)
	}

