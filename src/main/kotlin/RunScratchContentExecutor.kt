// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.CommonBundle
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.TabTitle
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Runs a process and prints the output in a content tab within the Run toolwindow.
 */
class RunScratchContentExecutor(private val myProject: Project, private val myProcess: ProcessHandler) : Disposable {
    private val myFilterList: MutableList<Filter?> = ArrayList()
    private var myRerunAction: Runnable? = null
    private var myStopAction: Runnable? = null
    private var myEditConfigAction: Runnable? = null
    private var myAfterCompletion: Runnable? = null
    private var myStopEnabled: Computable<Boolean?>? = null

    @TabTitle
    private var myTitle: @TabTitle String? = ExecutionBundle.message("output.tab.default.title")
    private var myHelpId: String? = null
    private var myActivateToolWindow = true
    private var myFocusToolWindow = true

    fun withTitle(@TabTitle title: @TabTitle String?): RunScratchContentExecutor {
        myTitle = title
        return this
    }

    fun withRerun(rerun: Runnable?): RunScratchContentExecutor {
        myRerunAction = rerun
        return this
    }

    fun withStop(stop: Runnable, stopEnabled: Computable<Boolean?>): RunScratchContentExecutor {
        myStopAction = stop
        myStopEnabled = stopEnabled
        return this
    }

    fun withConfig(editConfig: Runnable): RunScratchContentExecutor {
        myEditConfigAction = editConfig
        return this
    }

    private fun createConsole(): ConsoleView {
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(myProject)
        consoleBuilder.filters(myFilterList)
        val console = consoleBuilder.console

        if (myHelpId != null) {
            console.setHelpId(myHelpId!!)
        }
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val actions = DefaultActionGroup()

        val consolePanel = createConsolePanel(console, actions)
        val descriptor = RunContentDescriptor(console, myProcess, consolePanel, myTitle)
        descriptor.isActivateToolWindowWhenAdded = myActivateToolWindow
        descriptor.isAutoFocusContent = myFocusToolWindow

        Disposer.register(descriptor, this)
        Disposer.register(descriptor, console)

        actions.add(this.RerunAction(consolePanel))
        actions.add(this.StopAction())
        actions.add(CloseAction(executor, descriptor, myProject))
        actions.add(Separator())
        actions.add(this.RunConfigAction())

        RunContentManager.getInstance(myProject).showRunContent(executor, descriptor)
        return console
    }


    fun run() {
        FileDocumentManager.getInstance().saveAllDocuments()

        val view: ConsoleView = createConsole()
        view.attachToProcess(myProcess)
        if (myAfterCompletion != null) {
            myProcess.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    ApplicationManager.getApplication().invokeLater(myAfterCompletion!!)
                }
            })
        }
        myProcess.startNotify()
    }

    override fun dispose() {}

    private inner class RerunAction(consolePanel: JComponent?) : AnAction(
        CommonBundle.message("action.text.rerun"),
        CommonBundle.message("action.text.rerun"),
        AllIcons.Actions.Restart
    ) {
        init {
            registerCustomShortcutSet(CommonShortcuts.getRerun(), consolePanel)
        }

        override fun actionPerformed(e: AnActionEvent) {
            myRerunAction!!.run()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.setEnabledAndVisible(myRerunAction != null)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }

        override fun isDumbAware(): Boolean {
            return true
        }
    }

    private inner class StopAction : AnAction(
        ExecutionBundle.messagePointer("action.AnAction.text.stop"),
        ExecutionBundle.messagePointer("action.AnAction.description.stop"), AllIcons.Actions.Suspend
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            myStopAction!!.run()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.setVisible(myStopAction != null)
            e.presentation.setEnabled(myStopEnabled != null && myStopEnabled!!.compute() == true)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }
    }


    private inner class RunConfigAction : AnAction(
        ScratchExecutionBundle.messagePointer("action.AnAction.text.runscript.configuration"),
        ScratchExecutionBundle.messagePointer("action.AnAction.description.runscript.configuration"),
        AllIcons.Actions.More
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            myEditConfigAction?.run()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.setEnabledAndVisible(myEditConfigAction != null)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }
    }

    companion object {
        private fun createConsolePanel(view: ConsoleView, actions: ActionGroup): JComponent {
            val panel = JPanel()
            panel.setLayout(BorderLayout())
            panel.add(view.component, BorderLayout.CENTER)
            val actionToolbar = ActionManager.getInstance().createActionToolbar("RunContentExecutor", actions, false)
            actionToolbar.targetComponent = panel
            panel.add(actionToolbar.component, BorderLayout.WEST)
            return panel
        }
    }
}