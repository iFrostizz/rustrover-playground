import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.execution.ParametersListUtil
import java.nio.charset.Charset

class ScriptGutterIconProvider : LineMarkerProvider {
    companion object {
        const val SHEBANG = "#!/usr/bin/env -S cargo +nightly -Zscript"
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.text != SHEBANG) return null

        val file = element.containingFile.virtualFile ?: return null
        if (!ScratchUtil.isScratch(file)) return null

        return LineMarkerInfo<PsiElement>(
            element,
            element.textRange,
            AllIcons.RunConfigurations.TestState.Run_run,
            { ScratchExecutionBundle.message("action.AnAction.description.runscript.run") },
            { event, elt ->
                val project = elt.project
                val virtualFile = elt.containingFile.virtualFile ?: return@LineMarkerInfo

                val group = DefaultActionGroup()
                group.add(object : AnAction(ScratchExecutionBundle.message("action.AnAction.text.runscript.run") + " '${virtualFile.name}'",
                                            ScratchExecutionBundle.message("action.AnAction.description.runscript.run"),
                                            AllIcons.Actions.Execute) {
                    override fun actionPerformed(e: AnActionEvent) {
                        runCargoScript(project, virtualFile)
                    }
                })
                group.add(object : AnAction(ScratchExecutionBundle.message("action.AnAction.text.runscript.configuration"),
                                            ScratchExecutionBundle.message("action.AnAction.description.runscript.configuration"),
                                            AllIcons.Actions.Edit) {
                    override fun actionPerformed(e: AnActionEvent) {
                        editConfiguration(project, virtualFile)
                    }
                })

                JBPopupFactory.getInstance().createActionGroupPopup(
                    null, group, DataManager.getInstance().getDataContext(event.component),
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true
                ).show(RelativePoint(event))
            },
            GutterIconRenderer.Alignment.CENTER,
            { ScratchExecutionBundle.message("action.AnAction.text.runscript.run") })
    }

    private fun editConfiguration(project: Project, file: VirtualFile) {
        val properties = PropertiesComponent.getInstance(project)
        val argsKey = "cargo.script.args.${file.path}"
        val newArgs = Messages.showInputDialog(
            project,
            ScratchExecutionBundle.message("message.runscript.enter.args", file.name),
            ScratchExecutionBundle.message("action.AnAction.text.runscript.configuration"),
            null,
            properties.getValue(argsKey, ""),
            null
        )
        if (newArgs != null) {
            properties.setValue(argsKey, newArgs)
        }
    }

    private fun runCargoScript(project: Project, file: VirtualFile) {
        val properties = PropertiesComponent.getInstance(project)
        val argsKey = "cargo.script.args.${file.path}"
        val savedArgs = properties.getValue(argsKey, "")

        val commandLine =
            GeneralCommandLine("cargo", "-Zscript", file.path).withWorkDirectory(file.parent?.path ?: project.basePath)
                .withCharset(Charset.forName("UTF-8"))
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        if (savedArgs.isNotBlank()) {
            commandLine.addParameters(ParametersListUtil.parse(savedArgs))
        }

        val process = KillableColoredProcessHandler(commandLine)

        ProcessTerminatedListener.attach(process, project)

        val label = "Cargo script: ${file.name}"

        val executor = RunScratchContentExecutor(project, process)
            .withTitle(label)
            .withStop({ process.killProcess() }, { process.isStartNotified && !process.isProcessTerminated })
            .withRerun { runCargoScript(project, file) }
            .withConfig { editConfiguration(project, file) }

        executor.run()
    }
}