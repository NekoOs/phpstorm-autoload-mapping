import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.jetbrains.php")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }

    testImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
    }

    test {
        useJUnit()
    }

    named<PrepareSandboxTask>("prepareTestSandbox") {
        // Vue's LSP is unrelated to PHP PSI tests and expects a full installed-product layout.
        disabledPlugins.add("org.jetbrains.plugins.vue")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild")
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
