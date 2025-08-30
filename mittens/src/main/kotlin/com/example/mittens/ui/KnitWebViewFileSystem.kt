package com.example.mittens.ui

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.IOException

class KnitWebViewFileSystem : VirtualFileSystem() {
    
    companion object {
        const val PROTOCOL = "knit-webview"
        val instance = KnitWebViewFileSystem()
    }
    
    override fun getProtocol(): String = PROTOCOL
    
    override fun findFileByPath(path: String): VirtualFile? {
        return if (path == KnitWebViewVirtualFile.PATH) {
            KnitWebViewVirtualFile()
        } else null
    }
    
    override fun refresh(asynchronous: Boolean) {
        // No-op for virtual file system
    }
    
    override fun refreshAndFindFileByPath(path: String): VirtualFile? {
        return findFileByPath(path)
    }
    
    override fun addVirtualFileListener(listener: VirtualFileListener) {
        // No-op for virtual file system
    }
    
    override fun removeVirtualFileListener(listener: VirtualFileListener) {
        // No-op for virtual file system
    }
    
    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        throw IOException("Cannot delete virtual file")
    }
    
    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        throw IOException("Cannot move virtual file")
    }
    
    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        throw IOException("Cannot rename virtual file")
    }
    
    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        throw IOException("Cannot create files in virtual file system")
    }
    
    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        throw IOException("Cannot create directories in virtual file system")
    }
    
    override fun copyFile(requestor: Any?, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile {
        throw IOException("Cannot copy virtual file")
    }
    
    override fun isReadOnly(): Boolean = true
}