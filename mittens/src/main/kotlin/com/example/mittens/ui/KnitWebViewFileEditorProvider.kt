package com.example.mittens.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class KnitWebViewFileEditorProvider : FileEditorProvider, DumbAware {
    
    companion object {
        const val EDITOR_TYPE_ID = "knit-webview-editor"
    }
    
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is KnitWebViewVirtualFile
    }
    
    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return KnitWebViewFileEditor(project, file)
    }
    
    override fun getEditorTypeId(): String = EDITOR_TYPE_ID
    
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}