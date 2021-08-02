
pluginManagement {
	val kotlinVersion = "1.3.41"  // need at least 1.3.20 to avoid error 'command line is too long'
	repositories {
		gradlePluginPortal()
		jcenter()
	}
	resolutionStrategy {
		eachPlugin {
			when{
				requested.id.id.contains("kotlinx-serialization") -> useModule("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
				requested.id.namespace?.startsWith("org.jetbrains.kotlin") == true -> useVersion(kotlinVersion)
			}
		}
	}
}

rootProject.name = "explorationModel"
