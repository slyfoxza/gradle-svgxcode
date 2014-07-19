SVG transcoding Gradle plugin
=============================

Usage
-----
	buildscript {
		dependencies {
			classpath group: 'net.za.slyfox.gradle', name: 'gradle-svgxcode', version: '1.0.0-SNAPSHOT'
		}
	}
	
	android {
		sourceSets {
			main {
				res.srcDirs = [ 'src/main/res', "$buildDir/svgxcode/ic_launcher" ]
			}
		}
	}
	
	import net.za.slyfox.gradle.svgxcode.AndroidTranscode
	
	task transcodeLauncherDrawables(type: AndroidTranscode) {
		include 'src/main/svg/ic_launcher_*.svg'
		into "$buildDir/svgxcode/ic_launcher"
		densities 'ldpi-xxxhdpi'
		width = 48
	}
	
	tasks.matching({ it =~ /merge\w+Resources/ }).each { dependsOn(transcodeLauncherDrawables) }
