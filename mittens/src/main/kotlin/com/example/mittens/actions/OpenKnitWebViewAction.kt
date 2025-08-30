package com.example.mittens.actions

import com.example.mittens.ui.KnitWebViewVirtualFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager

class OpenKnitWebViewAction : AnAction("Open Knit Dependency Graph", "Open the interactive dependency graph web view", null) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        // Create the virtual file and open it in an editor tab
        val virtualFile = KnitWebViewVirtualFile()
        val fileEditorManager = FileEditorManager.getInstance(project)
        
        // Check if the file is already open
        val existingEditor = fileEditorManager.getSelectedEditor(virtualFile)
        if (existingEditor != null) {
            // If already open, just focus on that tab
            fileEditorManager.openFile(virtualFile, true)
        } else {
            // Open in a new tab
            fileEditorManager.openFile(virtualFile, true)
        }
    }
}