package io.github.gunpowder

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.matthewprenger.cursegradle.CurseExtension
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseUploadTask
import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.util.Constants
import net.minecrell.gradle.licenser.LicenseExtension
import net.minecrell.gradle.licenser.header.HeaderStyle
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.internal.DefaultPublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.SourceSetContainer


internal fun Project.configureGunpowder() {
    version = "${project.properties["extension_version"]}+gunpowder.${project.properties["gunpowder_version"]}.mc.${project.properties["minecraft"]}"
    group = "io.github.gunpowder"

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    configure<LicenseExtension> {
        header = rootProject.file("LICENSE")
        setIncludes(listOf("**/*.java", "**/*.kt"))
        style.put("java", HeaderStyle.BLOCK_COMMENT)
        style.put("kt", HeaderStyle.BLOCK_COMMENT)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    tasks.withType<ProcessResources> {
        inputs.property("version", project.properties["extension_version"])
        inputs.property("gunpowder", project.properties["gunpowder_version"])

        val sourceSets = project.property("sourceSets") as SourceSetContainer

        from(sourceSets.getByName("main").resources.srcDirs) {
            include("fabric.mod.json")
            expand(
                "version" to project.version,
                "gunpowder" to project.properties["gunpowder_version"] as String
            )
        }

        from(sourceSets.getByName("main").resources.srcDirs) {
            exclude("fabric.mod.json")
        }
    }

    configure<LoomGradleExtension> {
    }
}


internal fun Project.loadPlugins() {
    plugins.apply("java")
    plugins.apply("idea")
    plugins.apply("org.jetbrains.kotlin.jvm")
    plugins.apply("maven-publish")
    plugins.apply("net.minecrell.licenser")
    plugins.apply("fabric-loom")
    plugins.apply("com.github.johnrengelman.shadow")
    plugins.apply("com.matthewprenger.cursegradle")
}


internal fun Project.loadDependencies() {
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
            name = "Ladysnake Libs"
            url = uri("https://dl.bintray.com/ladysnake/libs")
        }
    }



    val libs = project.properties["libs"] as Map<String, Any>
    dependencies {
        add(Constants.Configurations.MINECRAFT, libs["minecraft"]!!)
        add(Constants.Configurations.MAPPINGS, libs["yarn"]!!)
        add("modImplementation", libs["fabric_loader"]!!)
        add("modImplementation", libs["fabric_api"]!!)
        add("modCompileOnly", libs["fabric_language_kotlin"]!!)

        add("modCompileOnly", libs["exposed_core"]!!)

        add("modCompileOnly", "io.github.gunpowder:gunpowder-api:${project.properties["gunpowder_version"]}+${project.properties["minecraft"]}")
        add("modRuntime", "io.github.gunpowder:gunpowder-base:${project.properties["gunpowder_version"]}+${project.properties["minecraft"]}")

        add("modRuntime", libs["modmenu"]!!)
    }

}


internal fun Project.setupTasks() {
    val base = project.convention.getPlugin(BasePluginConvention::class.java)
    val jarpath = "${buildDir}/libs/${base.archivesBaseName}-${project.version}"
    val sourceSets = property("sourceSets") as SourceSetContainer

    tasks.getByName("remapJar") {
        enabled = false
    }

    tasks.getByName<ShadowJar>("shadowJar") {
        dependsOn.add("classes")

        enabled = true
        archiveClassifier.set("dev")

        configurations.removeIf { true }  // Remove all
        configurations.add(this@setupTasks.configurations["shade"])
    }

    tasks.create<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.getByName("main").allSource)
    }

    tasks.create<RemapJarTask>("remapShadowJar") {
        dependsOn.add("shadowJar")

        input.set(file("$jarpath-dev.jar"))
        archiveFileName.set("${project.name}-${project.version}.jar")
        addNestedDependencies.set(true)
        remapAccessWidener.set(true)
    }


//    tasks.create<RemapJarTask>("remapSourcesJar") {
//        dependsOn.add("sourcesJar")
//        input.set(file("$jarpath-dev.jar"))
//        archiveFileName.set("${project.name}-${project.version}-sources.jar")
//        remapAccessWidener.set(true)
//    }

    tasks.getByName("build") {
        dependsOn("remapShadowJar")
    }

    if (file("secrets.gradle").exists()) {
        apply(from="secrets.gradle")

        tasks.getByName<PublishToMavenRepository>("publish") {
            dependsOn("remapMavenJar", "remapSourcesJar")
        }

        configure<DefaultPublishingExtension> {
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
                register("mavenJava", MavenPublication::class) {
                    artifact(file("${jarpath}.jar")).apply { classifier = "" }
                    artifact(file("${jarpath}-dev.jar")).apply { classifier = "dev" }
                    artifact(file("${jarpath}-sources.jar")).apply { classifier = "sources" }
                    artifact(file("${jarpath}-sources-dev.jar")).apply { classifier = "sources-dev" }
                }
            }
        }

        tasks.getByName<CurseUploadTask>("curseforge") {
            dependsOn("remapShadowJar")
        }

        configure<CurseExtension> {
            curseProjects.add(CurseProject().apply {
                apiKey = project.properties["cfKey"] as String
                releaseType = "release"
                changelogType = "markdown"
                changelog = file("CHANGELOG.md").readText().split("---")[0]

                addGameVersion(project.properties["minecraft"])
                addGameVersion("Fabric")
                addGameVersion("Java 8")
                addGameVersion("Java 9")
                addGameVersion("Java 10")

                mainArtifact("$jarpath.jar")
            })

            curseGradleOptions.apply {
                forgeGradleIntegration = false
            }
        }
    }
}