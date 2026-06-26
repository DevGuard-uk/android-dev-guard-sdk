import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

plugins {
    id("com.android.application") version "8.9.2" apply false
    id("com.android.library") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

subprojects {
    group = findProperty("GROUP") ?: "uk.devguard"
    version = findProperty("VERSION_NAME") ?: "1.0.1"
}

gradle.projectsEvaluated {
    val publishToCentral = rootProject.findProperty("mavenCentralPublishing")?.toString() == "true"

    subprojects {
        if (!pluginManager.hasPlugin("maven-publish")) return@subprojects

        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                if (name != "release") return@configureEach
                pom {
                    name.set(findProperty("POM_NAME")?.toString() ?: "DevGuard Android")
                    description.set(findProperty("POM_DESCRIPTION")?.toString() ?: "")
                    url.set(findProperty("POM_URL")?.toString() ?: "https://devguard.uk")
                    licenses {
                        license {
                            name.set(findProperty("POM_LICENSE_NAME")?.toString() ?: "MIT")
                            url.set(findProperty("POM_LICENSE_URL")?.toString() ?: "https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set(findProperty("POM_DEVELOPER_ID")?.toString() ?: "devguard")
                            name.set(findProperty("POM_DEVELOPER_NAME")?.toString() ?: "DevGuard UK")
                            url.set(findProperty("POM_DEVELOPER_URL")?.toString() ?: "https://devguard.uk")
                        }
                    }
                    scm {
                        url.set(findProperty("POM_SCM_URL")?.toString() ?: "")
                        connection.set(findProperty("POM_SCM_CONNECTION")?.toString() ?: "")
                        developerConnection.set(findProperty("POM_SCM_DEV_CONNECTION")?.toString() ?: "")
                    }
                }
            }

            if (publishToCentral) {
                repositories {
                    maven {
                        name = "MavenCentral"
                        url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                        credentials {
                            username = rootProject.findProperty("mavenCentralUsername")?.toString()
                                ?: System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername")
                            password = rootProject.findProperty("mavenCentralPassword")?.toString()
                                ?: System.getenv("ORG_GRADLE_PROJECT_mavenCentralPassword")
                        }
                    }
                }
            }
        }

        if (publishToCentral && pluginManager.hasPlugin("signing")) {
            extensions.configure<SigningExtension> {
                val keyId = rootProject.findProperty("signingInMemoryKeyId")?.toString()
                val key = rootProject.findProperty("signingInMemoryKey")?.toString()
                val password = rootProject.findProperty("signingInMemoryPassword")?.toString()
                if (!keyId.isNullOrBlank() && !key.isNullOrBlank() && !password.isNullOrBlank()) {
                    useInMemoryPgpKeys(keyId, key.replace("\\n", "\n"), password)
                }
                val publishing = extensions.getByType<PublishingExtension>()
                publishing.publications.withType<MavenPublication>().configureEach {
                    if (name == "release") sign(this)
                }
            }
        }
    }
}

tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    description = "Publish sdk, core, and crash-reporter to Maven Central (requires credentials + signing)."
    dependsOn(
        ":core:publishReleasePublicationToMavenCentralRepository",
        ":crash-reporter:publishReleasePublicationToMavenCentralRepository",
        ":sdk:publishReleasePublicationToMavenCentralRepository",
    )
    finalizedBy("transferMavenCentralStagingToPortal")
}

tasks.register("transferMavenCentralStagingToPortal") {
    group = "publishing"
    description =
        "Move OSSRH staging upload into Central Portal Deployments (required for maven-publish)."

    doLast {
        val publishToCentral = findProperty("mavenCentralPublishing")?.toString() == "true"
        if (!publishToCentral) {
            logger.lifecycle("mavenCentralPublishing is not true — skipping portal transfer.")
            return@doLast
        }

        val username = findProperty("mavenCentralUsername")?.toString()
            ?: System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername")
        val password = findProperty("mavenCentralPassword")?.toString()
            ?: System.getenv("ORG_GRADLE_PROJECT_mavenCentralPassword")
        require(!username.isNullOrBlank() && !password.isNullOrBlank()) {
            "Missing mavenCentralUsername / mavenCentralPassword in ~/.gradle/gradle.properties"
        }

        val namespace = findProperty("GROUP")?.toString() ?: "uk.devguard"
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
        val client = HttpClient.newHttpClient()
        fun bearerRequest(url: String, method: String = "POST"): HttpResponse<String> {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer $token")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build()
            return client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val defaultUrl =
            "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$namespace"
        val defaultResponse = bearerRequest(defaultUrl)
        if (defaultResponse.statusCode() in 200..299) {
            logger.lifecycle("Transferred staging repository to Central Portal via defaultRepository.")
            return@doLast
        }

        logger.lifecycle(
            "defaultRepository transfer returned HTTP ${defaultResponse.statusCode()}; trying repository search."
        )

        val searchUrl =
            "https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=$namespace"
        val searchResponse = bearerRequest(searchUrl, method = "GET")
        check(searchResponse.statusCode() in 200..299) {
            "Repository search failed: HTTP ${searchResponse.statusCode()} ${searchResponse.body()}"
        }

        val keyRegex = """"key"\s*:\s*"([^"]+)"""".toRegex()
        val openKeys = keyRegex.findAll(searchResponse.body())
            .map { it.groupValues[1] }
            .filter { it.contains(namespace) }
            .toList()
        check(openKeys.isNotEmpty()) {
            "No open staging repository found for $namespace. Body: ${searchResponse.body()}"
        }

        val repoKey = openKeys.last()
        val encodedKey = URLEncoder.encode(repoKey, StandardCharsets.UTF_8)
        val uploadUrl =
            "https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/$encodedKey"
        val uploadResponse = bearerRequest(uploadUrl)
        check(uploadResponse.statusCode() in 200..299) {
            "Repository upload failed: HTTP ${uploadResponse.statusCode()} ${uploadResponse.body()}"
        }
        logger.lifecycle("Transferred staging repository '$repoKey' to Central Portal.")
    }
}
