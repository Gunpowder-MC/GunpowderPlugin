import java.net.URL
import java.util.Properties

plugins {
    java
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.12.0"
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "Gunpowder"
        url = uri("https://maven.martmists.com/releases")
    }
}


val pr = URL("https://raw.githubusercontent.com/Gunpowder-MC/Gunpowder/master/gradle.properties").openConnection().getInputStream()
val props = Properties()
props.load(pr)
props.forEach { prop ->
    project.ext.set(prop.key as String, prop.value)
}

dependencies {
    /* Depend on the kotlin plugin, since we want to access it in our plugin */
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${props["kotlin"]}")
//    implementation("gradle.plugin.org.cadixdev.gradle:licenser:${props["licenser"]}")
    implementation("net.fabricmc:fabric-loom:${props["fabric_loom"]}")
    implementation("com.github.jengelman.gradle.plugins:shadow:${props["shadow"]}")
    implementation("gradle.plugin.com.matthewprenger:CurseGradle:${props["cursegradle"]}")

    /* Depend on the default Gradle API's since we want to build a custom plugin */
    implementation(gradleApi())
    implementation(localGroovy())
}

tasks.withType<Javadoc> {
    enabled = false
}

tasks.withType<Groovydoc> {
    enabled = false
}

gradlePlugin {
    plugins {
        create("main") {
            id = "io.github.gunpowder"
            implementationClass = "io.github.gunpowder.GunpowderPlugin"
        }
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

pluginBundle {
    website = "https://github.com/gunpowder-mc/"
    vcsUrl = "https://github.com/gunpowder-mc/GunpowderPlugin"
    description = "GunpowderPlugin imports and configures the required dependencies to develop for Gunpowder."

    (plugins) {
        "main" {
            // id is captured from java-gradle-plugin configuration
            displayName = "Gunpowder Development Plugin"
            tags = listOf("fabric", "mc", "minecraft", "server", "plugin", "gunpowder")
        }
    }

    mavenCoordinates {
        groupId = "io.github.gunpowder"
        artifactId = "gunpowder-plugins"
    }
}
