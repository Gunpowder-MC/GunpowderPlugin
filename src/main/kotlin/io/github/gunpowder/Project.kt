package io.github.gunpowder

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.matthewprenger.cursegradle.CurseExtension
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseRelation
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.RemapSourcesJarTask
import net.fabricmc.loom.util.Constants
import org.cadixdev.gradle.licenser.LicenseExtension
import org.cadixdev.gradle.licenser.header.HeaderStyle
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.SourceSetContainer


internal fun Project.configureGunpowder() {
    println("[GunpowderPlugin] Configuring project")
    version = "${project.properties["extension_version"]}+gunpowder.${project.properties["gunpowder_version"]}.mc.${project.properties["minecraft"]}"

    println("[GunpowderPlugin] Project version: $version")

    group = "io.github.gunpowder"

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_16
        targetCompatibility = JavaVersion.VERSION_16
    }

    configure<LicenseExtension> {
        setHeader(rootProject.file("LICENSE"))
        setIncludes(listOf("**/*.java", "**/*.kt"))
        style.put("java", HeaderStyle.BLOCK_COMMENT)
        style.put("kt", HeaderStyle.BLOCK_COMMENT)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_16.toString()
            freeCompilerArgs = freeCompilerArgs.toMutableList().also { it.add("-Xjvm-default=all-compatibility") }
        }
    }

    tasks.withType<ProcessResources> {
        inputs.property("version", project.properties["extension_version"])
        inputs.property("gunpowder", project.properties["gunpowder_version"])

        filesMatching("fabric.mod.json") {
            expand(
                "version" to project.version,
                "gunpowder" to project.properties["gunpowder_version"] as String
            )
        }
    }

    configure<LoomGradleExtensionAPI> {

    }
}


internal fun Project.loadPlugins() {
    println("[GunpowderPlugin] Loading plugins")
    plugins.apply("java")
    plugins.apply("idea")
    plugins.apply("org.jetbrains.kotlin.jvm")
    plugins.apply("maven-publish")
    plugins.apply("org.cadixdev.licenser")
    plugins.apply("fabric-loom")
    plugins.apply("com.github.johnrengelman.shadow")
    plugins.apply("com.matthewprenger.cursegradle")
    plugins.apply("com.github.ben-manes.versions")
}


internal fun Project.loadDependencies() {
    println("[GunpowderPlugin] Setting up dependencies")

    repositories {
        mavenCentral()
        jcenter()
        maven {
            name = "Gunpowder"
            url = uri("https://maven.martmists.com/")
        }
        maven {
            name = "Jitpack"
            url = uri("https://jitpack.io")
        }
        maven {
            name = "Ladysnake Mods"
            url = uri("https://ladysnake.jfrog.io/artifactory/mods")
            content {
                includeGroup("io.github.ladysnake")
                includeGroupByRegex("io\\.github\\.onyxstudios.*")
            }
        }
        maven {
            name = "HeavenKing"
            url = uri("https://hephaestus.dev/release")
        }
        maven {
            name = "Permissions"
            url = uri("https://maven.nucleoid.xyz")
        }
    }

    val libs = project.properties["libs"] as Map<String, Any>

    println("[GunpowderPlugin] Minecraft: ${libs["minecraft"]}")
    println("[GunpowderPlugin] Fabric Loader: ${libs["fabric_loader"]}")
    println("[GunpowderPlugin] Fabric API: ${libs["fabric_api"]}")
    println("[GunpowderPlugin] Yarn: ${libs["yarn"]}")
    println("[GunpowderPlugin] Gunpowder: ${project.properties["gunpowder_version"]}")

    dependencies {
        add(Constants.Configurations.MINECRAFT, libs["minecraft"]!!)
        add(Constants.Configurations.MAPPINGS, libs["yarn"]!!)
        add("modImplementation", libs["fabric_loader"]!!)
        add("modImplementation", libs["fabric_api"]!!)
        add("modImplementation", libs["fabric_language_kotlin"]!!)
        add("modImplementation", libs["permissions"]!!)

        add("modCompileOnly", libs["exposed_core"]!!)
        add("modImplementation", libs["hermes"]!!)

        add("modCompileOnly", "io.github.gunpowder:gunpowder-api:${project.properties["gunpowder_version"]}+${project.properties["minecraft"]}")
        add("modRuntime", "io.github.gunpowder:gunpowder-base:${project.properties["gunpowder_version"]}+${project.properties["minecraft"]}")
    }

}


internal fun Project.setupTasks() {
    println("[GunpowderPlugin] Configuring tasks")

    val base = project.convention.getPlugin(BasePluginConvention::class.java)
    val jarpath = "${buildDir}/libs/${base.archivesBaseName}-${project.version}"
    val sourceSets = property("sourceSets") as SourceSetContainer

    val jarTask = tasks.getByName("jar")

    val remapJarTask = tasks.getByName("remapJar") {
        enabled = false
    }

    val shadowJarTask = tasks.getByName<ShadowJar>("shadowJar") {
        dependsOn.add("classes")

        enabled = true
        archiveClassifier.set("dev")

        configurations.removeIf { true }  // Remove all
        configurations.add(this@setupTasks.configurations["shade"])
    }

    val sourcesJarTask = tasks.create<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.getByName("main").allSource)
    }

    val remapShadowJarTask = tasks.create<RemapJarTask>("remapShadowJar") {
        dependsOn.add("shadowJar")

        input.set(shadowJarTask.archiveFile)
        archiveFileName.set("${project.name}-${project.version}.jar")
        addNestedDependencies.set(true)
        remapAccessWidener.set(true)
    }

    val remapMavenJarTask = tasks.create<RemapJarTask>("remapMavenJar") {
        dependsOn.add("shadowJar")

        input.set(shadowJarTask.archiveFile)
//        input.set(file("$jarpath-dev.jar"))
        archiveFileName.set("${project.name}-${project.version}-maven.jar")
        addNestedDependencies.set(false)
        remapAccessWidener.set(true)
    }


    val remapSourcesJarTask = tasks.getByName<RemapSourcesJarTask>("remapSourcesJar")
//    tasks.create<RemapJarTask>("remapSourcesJar") {
//        dependsOn.add("sourcesJar")
//        input.set(file("$jarpath-dev.jar"))
//        archiveFileName.set("${project.name}-${project.version}-sources.jar")
//        remapAccessWidener.set(true)
//    }

    tasks.getByName("build") {
        dependsOn("remapShadowJar", "remapMavenJar", "remapSourcesJar")
    }

    if (project.properties["mavenToken"] != null) {
        println("[GunpowderPlugin] Setting up maven publish")

        tasks.getByName("publish") {
            dependsOn("remapMavenJar", "remapSourcesJar")
        }

        configure<PublishingExtension> {
            repositories {
                maven {
                    url = uri("https://maven.martmists.com/releases")
                    credentials {
                        username = "admin"
                        password = project.properties["mavenToken"] as String
                    }
                }
            }

            publications {
                create<MavenPublication>("mavenJava") {
                    println("[GunpowderPlugin] Maven jar:            $jarpath-maven.jar")
                    println("[GunpowderPlugin] Shadow jar:           $jarpath-dev.jar")
                    println("[GunpowderPlugin] Sources jar:          $jarpath-sources.jar")
                    println("[GunpowderPlugin] Sources (mapped) jar: $jarpath-sources-dev.jar")

                    artifact(remapMavenJarTask.archiveFile).apply { classifier = "" }
                    artifact(shadowJarTask.archiveFile).apply { classifier = "dev" }
                    artifact(sourcesJarTask.archiveFile).apply { classifier = "sources" }
                    artifact("$jarpath-sources-dev.jar").apply { classifier = "sources-dev" }
                }
            }
        }
    }

    if (project.properties["curseId"] != null && project.properties["cfToken"] != null) {
        println("[GunpowderPlugin] Setting up curseforge publish")

        tasks.getByName("curseforge") {
            dependsOn("remapShadowJar")
        }

        configure<CurseExtension> {
            curseProjects.add(CurseProject().apply {
                apiKey = project.properties["cfToken"] as String
                id = project.properties["curseId"] as String
                releaseType = "release"
                changelogType = "markdown"
                changelog = file("CHANGELOG.md").readText().split("---")[0]

                addGameVersion((project.properties["minecraft"] as String).split("-")[0])
                addGameVersion("Fabric")
                addGameVersion("Java 16")

                println("[GunpowderPlugin] Curseforge jar: $jarpath.jar")
                mainArtifact("$jarpath.jar")

                relations(closureOf<CurseRelation> {
                    requiredDependency("gunpowder-mc")
                    (project.properties["mod_dependencies"]!! as String).split(",").forEach {
                        it.replace(" ", "").also { itt ->
                            if (itt.isNotBlank()) {
                                println("[GunpowderPlugin] Required dependency: ${it.replace(" ", "")}")
                                requiredDependency(itt)
                            }
                        }
                    }
                })
            })

            curseGradleOptions.apply {
                forgeGradleIntegration = false
            }
        }
    }
}