package ru.alfabank.converters

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrExtendsClauseImpl
import ru.alfabank.addImportStatement
import ru.alfabank.getPsiClass

object TestClassDeclarationConverter : Converter {

    override fun convert(
        psiFile: PsiFile,
        typeDefinition: GrTypeDefinition,
        groovyFile: GroovyFile
    ) {
        val parentClass = (typeDefinition.extendsClause as GrExtendsClauseImpl).lastChild.text

        val testRunnerClass = if (parentClass == "Specification") {
            "AlfaJUnit4Runner"
        } else {
            "AlfaRobolectricRunner"
        }

        typeDefinition.modifierList?.addAnnotation("RunWith($testRunnerClass::class)")

        groovyFile.addImportStatement("org.junit.runner.RunWith")
        groovyFile.addImportStatement("ru.alfabank.mobile.android.test.$testRunnerClass")

        val firstExtendsElement = (typeDefinition.extendsClause as GrExtendsClauseImpl).firstChild
        val lastExtendsElement = (typeDefinition.extendsClause as GrExtendsClauseImpl).lastChild
        psiFile.deleteChildRange(firstExtendsElement, lastExtendsElement)
    }
}