apply(plugin = "maven-publish")

extensions.configure<org.gradle.api.publish.PublishingExtension> {
    publications {
        register<org.gradle.api.publish.maven.MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }

            groupId = "com.github.answufeng"
            artifactId = "android-close-button-detector"
            version = property("VERSION_NAME")?.toString() ?: "1.0.0"

            pom {
                name.set("android-close-button-detector")
                description.set("Detect Android close buttons from screenshots/images using a TensorFlow Lite model.")
                url.set("https://github.com/answufeng/android-close-button-detector")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("answufeng")
                        name.set("answufeng")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/answufeng/android-close-button-detector.git")
                    url.set("https://github.com/answufeng/android-close-button-detector")
                }
            }
        }
    }
}
