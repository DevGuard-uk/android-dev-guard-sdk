plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "io.devguard.crash"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = rootProject.findProperty("GROUP")?.toString() ?: "uk.devguard"
                artifactId = "android-crash-reporter"
                version = project.version.toString()
            }
        }
    }
}
