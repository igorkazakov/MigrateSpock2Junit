package ru.alfabank.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbService
import org.jetbrains.plugins.groovy.GroovyFileType
import ru.alfabank.FileHelper
import ru.alfabank.converters.SpockToJunitConverter

class ConvertSpockToJunit : AnAction() {

    override fun update(event: AnActionEvent) {
        val file = event.getData(PlatformDataKeys.VIRTUAL_FILE)
        val editor = event.getData(PlatformDataKeys.EDITOR)

        val enabled = file != null &&
                editor != null &&
                (GroovyFileType.GROOVY_FILE_TYPE == file.fileType)

        event.presentation.isEnabled = enabled
                && !DumbService.isDumb(event.project!!)

    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = requireNotNull(event.project)
        val currentFile = event.getRequiredData(PlatformDataKeys.VIRTUAL_FILE)
        val editor = event.getRequiredData(PlatformDataKeys.EDITOR)

//        val newfile = currentFile.copy(currentFile, currentFile.parent, "qwerty123")
//
//        val newfile22 = KotlinPsiFileFactory().acquirePsiFileFactory().createFileFromText("qwertyKotlin.kt", KotlinLanguage.INSTANCE, "")
       // val groovyPsiFile = requireNotNull(PsiManager.getInstance(project).findFile(currentFile))

        FileHelper.createKotlinRootAndMoveFile(project, currentFile) { groovyPsiFile ->
            SpockToJunitConverter(project, editor, groovyPsiFile).transformToJunit()
        }
    }
}