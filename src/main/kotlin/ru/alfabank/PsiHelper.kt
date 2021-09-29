package ru.alfabank

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrSyntheticExpression
import org.jetbrains.plugins.groovy.lang.psi.util.childrenOfType

private val LOG = Logger.getInstance("junitspock.PsiHelper")

fun PsiElement.replaceElement(replacement: PsiElement) {
    this.parent.addAfter(replacement, this)
    this.delete()
}

fun PsiElement.addAfter(element: PsiElement): PsiElement {
    return this.parent.addAfter(element, this)
}

fun GrMethod.deleteSingleQuotesFromMethodName() {
    val factory = GroovyPsiElementFactory.getInstance(project)
    val methodName = name.replace(" #+| +|#+".toRegex(), "_")
    val methodFromText = factory.createMethodFromText("def $methodName() {}")
    nameIdentifierGroovy.replace(methodFromText.nameIdentifierGroovy)
}

fun GrModifierList.replaceDefWith(modifier: String) {
    val funModifier = GroovyPsiElementFactory.getInstance(project).createModifierFromText(modifier)
    val defModifier = this.modifiers.firstOrNull { it.text == "def" }
    add(funModifier)
    defModifier?.delete()
}

fun PsiFile.getPsiClass(): PsiClass? {
    if (this is PsiClassOwner) {
        val psiClasses = this.classes
        if (psiClasses.size == 1) {
            return psiClasses[0]
        } else {
            LOG.error("More or less that one PSI class. Found: " +
                    psiClasses.map { psiClass -> psiClass.qualifiedName }.joinToString()
            )
        }
    }
    return null
}

fun GroovyFile.addImportStatement(importString: String) {
    val factory = GroovyPsiElementFactory.getInstance(this.project)
    val importAlreadyExist = imports.allNamedImports.find { it.fullyQualifiedName == importString }
    if (importAlreadyExist != null) return

    val thatImportStatement = factory.createImportStatement(
        importString,
        false,
        false,
        null,
        null
    )
    addImport(thatImportStatement)
}

fun PsiElement.createCommentElement(text: String): PsiElement {
    val factory = GroovyPsiElementFactory.getInstance(this.project)
    // linebreak required otherwise -> def foo () {expression // "comment"}
    val methodBody = factory.createMethodBodyFromText("""
                                    expression // $text
                                """)
    return methodBody.childrenOfType<PsiComment>()[0]
}

fun GrMethod.getReturnStatement(): GrReturnStatement? {
    return this.block?.statements?.lastOrNull() as? GrReturnStatement
}