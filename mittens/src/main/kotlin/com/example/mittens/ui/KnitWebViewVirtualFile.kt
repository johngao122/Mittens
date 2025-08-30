package com.example.mittens.ui

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.InputStream
import java.io.OutputStream

class KnitWebViewVirtualFile : VirtualFile() {
    
    companion object {
        const val NAME = "Knit Dependency Graph"
        const val PATH = "/knit-dependency-graph"
    }
    
    override fun getName(): String = NAME
    
    override fun getFileSystem(): VirtualFileSystem = KnitWebViewFileSystem.instance
    
    override fun getPath(): String = PATH
    
    override fun isWritable(): Boolean = false
    
    override fun isDirectory(): Boolean = false
    
    override fun isValid(): Boolean = true
    
    override fun getParent(): VirtualFile? = null
    
    override fun getChildren(): Array<VirtualFile>? = null
    
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw UnsupportedOperationException("Not writable")
    }
    
    override fun contentsToByteArray(): ByteArray {
        return "Knit Dependency Graph Web View".toByteArray()
    }
    
    override fun getTimeStamp(): Long = 0
    
    override fun getLength(): Long = 0
    
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        // No-op for virtual file
    }
    
    override fun getInputStream(): InputStream {
        return contentsToByteArray().inputStream()
    }
    
    override fun equals(other: Any?): Boolean {
        return other is KnitWebViewVirtualFile
    }
    
    override fun hashCode(): Int {
        return PATH.hashCode()
    }
}