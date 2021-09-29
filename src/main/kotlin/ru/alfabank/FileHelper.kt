package ru.alfabank

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.IOException
import java.util.*

object FileHelper {
    private val LOG = Logger.getInstance(FileHelper::class.java)

    fun createKotlinRootAndMoveFile(
        project: Project,
        currentFile: VirtualFile,
        postJavaRootCreationActions: (PsiFile) -> Unit
    ) {
        val javaSourceRoot = createJavaSourceRoot(project, currentFile)

        if (javaSourceRoot != null) {
            /*  creating javaSourceRoot creates indexing therefore the following
                actions must be deferred
             */
            DumbService.getInstance(project).runWhenSmart {
                val groovyPsiFile = requireNotNull(PsiManager.getInstance(project).findFile(currentFile))
                //!!! взять psi файл грувишный и отдать на разбор
                postJavaRootCreationActions(groovyPsiFile)

                renameAndMoveToJava(currentFile, javaSourceRoot, project)
            }
        }
    }

    private fun createJavaSourceRoot(project: Project?, currentFile: VirtualFile): VirtualFile? {
        val sourceRootForCurrentFile = getSourceRootForCurrentFile(project, currentFile)
        if (sourceRootForCurrentFile.isPresent) {
            val file = sourceRootForCurrentFile.get()
            val sourceDirectory = file.parent

            val kotlinRoot = sourceDirectory.findChild("java")
            if (kotlinRoot != null && kotlinRoot.exists()) {
                return kotlinRoot
            }

            val yesNo = Messages.showYesNoDialog(
                project,
                "Java source root is not present, do you want to create it?",
                "Spock to JUnit Converter",
                Messages.getQuestionIcon()
            )
            if (yesNo == Messages.NO) {
                return null
            }
            try {
                WriteAction.run<IOException> { sourceDirectory.createChildDirectory(this, "java") }
            } catch (e: IOException) {
                val message = "Error while creating java directory"
                Messages.showErrorDialog(e.message, message)
                LOG.error(message, e)
            }

            val createdKotlinRoot = sourceDirectory.findChild("java")
            val module = getCurrentModule(project)
            ModuleRootModificationUtil.updateModel(module) { modifiableRootModel ->
                val contentEntries = modifiableRootModel.contentEntries
                assert(contentEntries.size == 1) // I'm not sure what's the use case for several content entries
                contentEntries[0].addSourceFolder(createdKotlinRoot!!, true)
            }
            createdKotlinRoot!!.refresh(false, false)

            return createdKotlinRoot
        } else {
            // TODO exception handling
        }
        return null
    }

    private fun getCurrentModule(project: Project?): Module {
        // FIXME determine correct module
        return ModuleManager.getInstance(project!!).modules[0]
    }

    private fun getSourceRootForCurrentFile(project: Project?, currentFile: VirtualFile): Optional<VirtualFile> {
        val projectRootManager = ProjectRootManager.getInstance(project!!)
        val sourceRoots = projectRootManager.contentSourceRoots
        return Arrays.stream(sourceRoots).filter { sourceRoot -> VfsUtilCore.isAncestor(sourceRoot, currentFile, true) }.findAny()
    }

    private fun renameAndMoveToJava(currentFile: VirtualFile, javaSourcesRoot: VirtualFile, project: Project?) {
        assert(javaSourcesRoot.exists())

        val sourceRootForCurrentFile = getSourceRootForCurrentFile(project, currentFile)

        assert(sourceRootForCurrentFile.isPresent)

        val relativePathForPackageName = VfsUtilCore
            .getRelativePath(currentFile.parent, sourceRootForCurrentFile.get(), '.')

        try {
            WriteAction.run<IOException> {
                val kotlinFilename = currentFile.name.replace(".groovy", ".kt")
                currentFile.rename(this, kotlinFilename)
                var lastCreatedDir = javaSourcesRoot

                for (packageElement in relativePathForPackageName!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val childWithPackageName = lastCreatedDir.findChild(packageElement)
                    lastCreatedDir = if (childWithPackageName != null && childWithPackageName.isDirectory) {
                        childWithPackageName
                    } else {
                        lastCreatedDir.createChildDirectory(this, packageElement)
                    }
                }
                //val newFile = currentFile.copy(this, lastCreatedDir, currentFile.name)
                //val kotlinFilename = newFile.name.replace(".groovy", ".kt")
                currentFile.move(this, lastCreatedDir)
            }
        } catch (e: IOException) {
            LOG.error(e) // fixme macht es Sinn hier noch weiter zu machen?
        }
    }
}