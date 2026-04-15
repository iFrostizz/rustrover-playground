import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.util.ExecUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import java.nio.charset.Charset


class ScriptGutterIconProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.text.trimIndent() != "#!/usr/bin/env -S cargo +nightly -Zscript") return null

        val file = element.containingFile.virtualFile ?: return null
        if (!ScratchUtil.isScratch(file)) return null

        return LineMarkerInfo<PsiElement>(
            element,
            element.textRange,
            AllIcons.RunConfigurations.TestState.Run_run,
            { "Run cargo script" },
            { _, elt ->
                val project = elt.project
                val virtualFile = elt.containingFile.virtualFile ?: return@LineMarkerInfo
                runCargoScript(project, virtualFile)
            },
            GutterIconRenderer.Alignment.CENTER,
            { "Run cargo script" })
    }

    private fun runCargoScript(project: Project, file: VirtualFile) {
        val commandLine = GeneralCommandLine("cargo", "-Zscript", file.path)
            .withWorkDirectory(file.parent?.path ?: project.basePath)
            .withCharset(Charset.forName("UTF-8"))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        val process = KillableColoredProcessHandler(commandLine)

        ProcessTerminatedListener.attach(process, project)

        val label = "Cargo script: ${file.name}"

        val executor = RunContentExecutor(project, process)
            .withTitle(label)
            .withStop(
                { process.killProcess() },
                { process.isStartNotified && !process.isProcessTerminated }
            )
            .withRerun { runCargoScript(project, file) }

        executor.run()
    }
}