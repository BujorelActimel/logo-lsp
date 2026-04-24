package com.logolsp.plugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import java.nio.file.Path

class LogoLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "LOGO") {

    override fun isSupportedFile(file: VirtualFile): Boolean =
        file.extension?.lowercase() == "logo"

    override fun createCommandLine(): GeneralCommandLine {
        val pluginPath = (PluginManagerCore.getPlugin(PluginId.getId("com.logolsp"))
            ?: error("LOGO LSP plugin not found")).pluginPath
        val serverJar = pluginPath.resolve("server").resolve("logo-lsp-server.jar")
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        return GeneralCommandLine(java, "-jar", serverJar.toString())
    }
}
