
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
/*
  This project contains classes that:
  - are reused by multiple projects,
  - some of which are to be deployed on an Android device.
  - do not require access to Android API (no android.jar).

  Because some of the classes are deployed on an Android Device, the classes in this project are compiled with Java 7.
*/

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.droidmate"
version = "2.4.4-RC1"

plugins {
	id("org.jetbrains.kotlin.jvm") apply true
	`maven-publish`
}

// for publishing to m2
apply (plugin= "maven")
apply (plugin="maven-publish")

repositories {
	mavenCentral()
	mavenLocal()
}

dependencies {
	implementation( "org.jetbrains.kotlin:kotlin-stdlib" )
	implementation( "org.jetbrains.kotlin:kotlin-reflect" )
	implementation( "org.slf4j:slf4j-api:1.7.25" )
	compile(kotlin("stdlib"))
}

// compile bytecode to java 8 (default is java 6)
tasks.withType<KotlinCompile> {
	kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<Wrapper> {
	gradleVersion = "5.3"  //define what gradle wrapper version is to be used on wrapper initialization (only if there is no wrapper yet or "gradlew wrapper" is called)
}

/*
val sourcesJar by tasks.registering(Jar::class) {
	classifier = "sources"
	from(sourceSets.main.get().allSource)
}
*/

/*
publishing {
	publications {
		register("mavenJava", MavenPublication::class) {
			from(components["java"])
			artifact(sourcesJar.get())
		}
	}
}*/
