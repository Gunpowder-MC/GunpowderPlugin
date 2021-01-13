package io.github.gunpowder

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.getValue
import java.net.URL
import java.util.*

open class GunpowderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val props = URL("https://raw.githubusercontent.com/Gunpowder-MC/Gunpowder/0.3.x/gradle.properties").openConnection().getInputStream()
        Properties().apply {
            load(props)
        }.forEach {
            project.extra[it.key as String] = it.value
        }

        val shade by project.configurations.creating
        project.extra["shade"] = shade

        project.apply(mapOf("from" to "https://raw.githubusercontent.com/Gunpowder-MC/Gunpowder/0.3.x/dependencies.gradle"))

        project.loadPlugins()
        project.configureGunpowder()
        project.loadDependencies()
        project.setupTasks()
    }
}
