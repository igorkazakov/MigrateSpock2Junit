package ru.alfabank.converters

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

interface Converter {

    fun convert(
        psiFile: PsiFile,
        typeDefinition: GrTypeDefinition,
        groovyFile: GroovyFile
    )
}