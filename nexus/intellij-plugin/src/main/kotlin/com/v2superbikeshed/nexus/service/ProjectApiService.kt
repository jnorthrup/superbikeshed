package com.v2superbikeshed.nexus.service

import borg.trikeshed.lib.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiFile

@Service(Service.Level.PROJECT)
class ProjectApiService(internal val project: Project) {
    
    data class ProjectInfo(
        val name: String,
        val basePath: String?,
        val modules: Indexed<String>,
        val contentRoots: Indexed<String>
    )
    
    fun getProjectInfo(): ProjectInfo {
        val moduleNames = mutableListOf<String>()
        val roots = mutableListOf<String>()
        
        val rootManager = ProjectRootManager.getInstance(project)
        rootManager.contentRoots.forEach { root ->
            roots.add(root.path)
        }
        
        return ProjectInfo(
            name = project.name,
            basePath = project.basePath,
            modules = \1 j { \2: Int -> moduleNames[i] },
            contentRoots = \1 j { \2: Int -> roots[i] }
        )
    }
    
    fun getProjectFiles(path: String? = null): Indexed<String> {
        val files = mutableListOf<String>()
        val basePath = path ?: project.basePath ?: return \1 j { \2: Int -> "" }
        
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath)
        if (virtualFile != null) {
            collectFiles(virtualFile, files)
        }
        
        return \1 j { \2: Int -> files[i] }
    }
    
    internal fun collectFiles(file: VirtualFile, collector: MutableList<String>) {
        if (file.isDirectory) {
            file.children.forEach { child ->
                collectFiles(child, collector)
            }
        } else {
            collector.add(file.path)
        }
    }
    
    fun getPsiFile(path: String): PsiFile? {
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
        return virtualFile?.let { PsiManager.getInstance(project).findFile(it) }
    }
}