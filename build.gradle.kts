import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

val pluginVersionValue = providers.gradleProperty("pluginVersion").get()
val platformSinceBuildValue = providers.gradleProperty("platformSinceBuild").get()
val marketplaceVersionPattern = Regex("""\d{3}\.\d+\.\d+""")

require(marketplaceVersionPattern.matches(pluginVersionValue)) {
    "pluginVersion must use <JetBrains-platform-branch>.<feature>.<patch>"
}
require(pluginVersionValue.substringBefore('.') == platformSinceBuildValue.substringBefore('.')) {
    "pluginVersion '$pluginVersionValue' must start with the platformSinceBuild branch " +
        "'$platformSinceBuildValue'"
}

group = providers.gradleProperty("pluginGroup").get()
version = pluginVersionValue

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
        options.compilerArgs.add("-Xlint:deprecation")
    }

    test {
        useJUnit()
    }

    named<PrepareSandboxTask>("prepareTestSandbox") {
        // Vue's LSP is unrelated to PHP PSI tests and expects a full installed-product layout.
        disabledPlugins.add("org.jetbrains.plugins.vue")
    }

    named<BuildSearchableOptionsTask>("buildSearchableOptions") {
        // Keep headless option indexing independent from a development IDE left open by runIde.
        sandboxConfigDirectory.set(layout.buildDirectory.dir("searchableOptions-sandbox/config"))
        sandboxSystemDirectory.set(layout.buildDirectory.dir("searchableOptions-sandbox/system"))
        sandboxLogDirectory.set(layout.buildDirectory.dir("searchableOptions-sandbox/log"))
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = pluginVersionValue

        ideaVersion {
            sinceBuild = platformSinceBuildValue
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
