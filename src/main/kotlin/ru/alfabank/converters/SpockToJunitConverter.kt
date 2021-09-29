package ru.alfabank.converters

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import org.jetbrains.plugins.groovy.lang.psi.*
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrListOrMapImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrLabeledStatementImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentListImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrClosableBlockImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.GrReturnStatementImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrMultiplicativeExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrShiftExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrBitwiseExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrMethodCallExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrRelationalExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrExtendsClauseImpl
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import ru.alfabank.*

const val WHEN = "// when"
const val GIVEN = "// given"
const val THEN = "// then"
const val EXPECT = "// expect"
const val AND_LABEL = "// and"
const val TEST_LIFECYCLE_METHOD = "// test lifecycle"

private const val SINGLE_ARGUMENT_WILDCARD = "_"
private const val MULTIPLE_ARGUMENTS_WILDCARD = "*_"

private val ARGUMENT_WILDCARD_PATTERN = "^_*\$".toRegex()

class SpockToJunitConverter(
    private val project: Project,
    private val editor: Editor,
    private val psiFile: PsiFile
) {

    private val typeDefinition = psiFile.getPsiClass() as GrTypeDefinition

    private val groovyFactory
        get() = GroovyPsiElementFactory.getInstance(project)

    private var givenBlockFirstElement: PsiElement? = null

    private val groovyFile
        get() = typeDefinition.containingFile as GroovyFile

    fun transformToJunit() {
        WriteCommandAction.runWriteCommandAction(project, null, null, {

            deleteAllNewKeyWord()

            convertTestClassDeclaration()

            convertPropertiesAnnotations()

            convertMethodsDeclaration()

            convertSpockTestLabels()

            convertMethodsBody()

            addMethodsReturnType()

        }, psiFile)
    }

    private fun convertMethodsBody() {
        // изменяем внутрянку метода
        for (method in typeDefinition.codeMethods) {
            val replaceQueue = mutableListOf<Pair<PsiElement, PsiElement>>()
            convertMethodArguments(method)

            if (!method.isTestMethod()) continue
            var element = method.body?.firstBodyElement ?: continue

            givenBlockFirstElement = if ((element.nextSibling.firstChild as? PsiCommentImpl)?.text == GIVEN) {
                element.nextSibling
            } else {
                element
            }
            var currentLabel = TEST_LIFECYCLE_METHOD
            while (element.nextSibling != null) {
                element = element.nextSibling

                // изменяем выражения в блоках given when then expect
                element.let {
                    when (it) {
                        is GrLabeledStatement -> {
                            if (it.firstChild.text != AND_LABEL) {
                                //меняем контекст, так как для given when и then нужно по разному конвертировать выражения,
                                // для and не нужно менять контекст!
                                currentLabel = it.firstChild.text
                            }
                            (it.lastChild as? GrVariableDeclaration)?.variables?.first()?.let { variable ->
                                convertVariableDeclaration(variable)
                            }

                            (it.lastChild as? GrMethodCallExpression)?.let { callExpr ->
                                convertCallMethods(currentLabel, callExpr, replaceQueue)
                            }
                            (it.lastChild as? GrRelationalExpressionImpl)?.let { callExpr ->
                                convertAssertTwoOperand(currentLabel, callExpr, replaceQueue)
                            }
                            (it.lastChild as? GrMultiplicativeExpressionImpl)?.let { callExpr ->
                                convertAssertCallNumber(currentLabel, callExpr, replaceQueue)
                            }
                            (it.lastChild as? GrShiftExpressionImpl)?.let { callExpr ->
                                convertAssertCallNumberWithArguments(currentLabel, callExpr, replaceQueue)
                                convertMockCallMethod(currentLabel, callExpr, replaceQueue)
                                convertMockGetter(currentLabel, callExpr, replaceQueue)
                            }
                        }
                        is GrVariableDeclaration -> {
                            convertVariableDeclaration(it.variables.first())
                        }
                        is GrMethodCallExpression -> {
                            convertCallMethods(currentLabel, it, replaceQueue)
                        }
                        is GrRelationalExpressionImpl -> {
                            convertAssertTwoOperand(currentLabel, it, replaceQueue)
                        }
                        is GrMultiplicativeExpressionImpl -> {
                            convertAssertCallNumber(currentLabel, it, replaceQueue)
                        }
                        is GrShiftExpressionImpl -> {
                            convertAssertCallNumberWithArguments(currentLabel, it, replaceQueue)
                            convertMockCallMethod(currentLabel, it, replaceQueue)
                            convertMockGetter(currentLabel, it, replaceQueue)
                        }
                        else -> {
                            print("изменяем внутрянку метода else блок для переменных")
                        }
                    }
                }
            }

            replaceQueue.forEach {
                it.first.replace(it.second)
            }
            replaceQueue.clear()
        }
    }

    private fun convertSpockTestLabels() {
        //change spock section labels with comment
        for (method in typeDefinition.codeMethods) {
            var element = method.body?.firstBodyElement ?: continue
            while (element.nextSibling != null) {
                element = element.nextSibling
                if (element is GrLabeledStatement) {
                    val comment = element.createCommentElement(element.firstChild.text)
                    element.firstChild.nextSibling.delete()
                    element.firstChild.replace(comment)
                }
            }
        }
    }

    private fun convertMethodsDeclaration() {
        //change method declaration
        for (method in typeDefinition.codeMethods) {

            //change def with fun
            method.modifierList.replaceDefWith("fun")

            //change method name
            method.deleteSingleQuotesFromMethodName()

            //check is the test????
            val methodBlock = method.block as GrOpenBlock
            if (methodBlock.text.contains("given:") ||
                methodBlock.text.contains("when:") ||
                methodBlock.text.contains("expect:")
            ) {
                // delete all annotations
                method.modifierList.annotations.forEach {
                    it.nextSibling.delete()
                    it.delete()
                }

                if (methodBlock.text.contains("where:")) {
                    //convert @Unroll !!!!
                    createArgumentProvider(method)
                } else {
                    // add test annotation
                    groovyFile.addImportStatement("org.junit.Test")
                    method.modifierList.addAnnotation("Test")
                }
            } else {
                // not test methods

                when (method.name) {
                    "setup" -> {
                        // add test lifecycle annotations
                        groovyFile.addImportStatement("org.junit.Before")
                        method.modifierList.addAnnotation("Before")
                    }
                    "cleanupSpec" -> {
                        // add test lifecycle annotations
                        groovyFile.addImportStatement("org.junit.After")
                        method.modifierList.addAnnotation("After")
                    }
                }
            }
        }
    }

    private fun convertPropertiesAnnotations() {
        for (field in typeDefinition.codeFields) {
            //change properties annotations
            field.annotations.map { annotation ->

                val newAnnotation = when (annotation.text) {
                    "@ClassRule" -> {
                        groovyFile.addImportStatement("org.junit.Rule")
                        groovyFactory.createAnnotationFromText("@Rule")
                    }
                    "@Shared" -> {
                        groovyFile.addImportStatement("kotlin.jvm.JvmField")
                        groovyFactory.createAnnotationFromText("@JvmField")
                    }
                    else -> groovyFactory.createAnnotationFromText("@JvmField777")
                }
                annotation.replace(newAnnotation)
            }

            //change fields syntax
            convertVariableDeclaration(field)
        }
    }

    private fun convertTestClassDeclaration() {
        // add runwith annotation and delete extends
        val parentClass = (typeDefinition.extendsClause as GrExtendsClauseImpl).lastChild.text

        val testRunnerClass = if (parentClass == "Specification") {
            "AlfaJUnit4Runner"
        } else {
            "AlfaRobolectricRunner"
        }

        typeDefinition.modifierList?.addAnnotation("RunWith($testRunnerClass::class)")

        groovyFile.addImportStatement("org.junit.runner.RunWith")
        groovyFile.addImportStatement("ru.alfabank.stubs.$testRunnerClass")

        (typeDefinition.extendsClause as GrExtendsClauseImpl).delete()
    }

    private fun addMethodsReturnType() {
        for (method in typeDefinition.codeMethods) {

            val returnStatement = method.getReturnStatement()
            if (returnStatement != null) {
                // как то достать тип из return statement
                val throwElement = groovyFactory.createThrownList(arrayOf(psiFile.getPsiClass()?.type()))
                val replacedElement = method.throwsList.replace(throwElement)
                val returnType = returnStatement.returnValue?.type?.presentableText ?: "Any"
                replaceWithKotlinMethodReturnType(replacedElement, returnType)
            }
        }
    }

    private fun deleteAllNewKeyWord() {
        recursivelyVisitAllElements(psiFile) {
            if (it is GrNewExpression) {
                it.navigationElement.firstChild.delete()
            }
        }
    }

    private fun recursivelyVisitAllElements(source: PsiElement, visitAction: (element: GroovyPsiElement) -> Unit) {
        source.accept(
            GroovyPsiElementVisitor(
                object : GroovyRecursiveElementVisitor() {

                    override fun visitElement(element: GroovyPsiElement) {
                        super.visitElement(element)
                        visitAction(element)
                    }
                }
            )
        )
    }

    private fun convertCallMethods(
        currentLabel: String,
        expression: GrMethodCallExpression,
        replaceQueue: MutableList<Pair<PsiElement, PsiElement>>
    ) {
        when (currentLabel) {
            TEST_LIFECYCLE_METHOD, GIVEN -> {
                val newArgumentsArray = expression.argumentList.allArguments.joinToString(",") {
                    convertMockMethodArgument(it)
                }
                val argumentListStatement = groovyFactory.createArgumentListFromText("($newArgumentsArray)")
                replaceQueue.add(Pair(expression.argumentList, argumentListStatement))
                print("изменяем в GIVEN тут не надо ничего в самом вызове менять, но аргументы стоит проверить на передачу мок выражения")
            }
            WHEN -> {
                print("изменяем в WHEN")
            }
            EXPECT, THEN -> {
                print("изменяем в THEN")
                val newKotlinExpression = groovyFactory.createExpressionFromText("assertTrue(${expression.text})")
                replaceQueue.add(Pair(expression, newKotlinExpression))
            }
            else -> {
                print("изменяем внутрянку метода else блок для методов")
            }
        }
    }

    private fun convertMockMethodArgument(element: GroovyPsiElement): String {
        val argumentMethodName = (element as? GrMethodCallExpressionImpl)?.navigationElement?.firstChild?.text
        return if (argumentMethodName == "Mock") {
            val classArgument = element.argumentList.allArguments.getOrNull(0)?.text ?: "UnknownClassName"
            "mock<$classArgument>()"
        } else {
            element.text
        }
    }

    private fun convertAssertTwoOperand(
        currentLabel: String,
        expression: GrRelationalExpressionImpl,
        replaceQueue: MutableList<Pair<PsiElement, PsiElement>>
    ) {
        if (currentLabel == EXPECT || currentLabel == THEN) {
            print("изменяем в THEN")
            val newKotlinExpression =
                groovyFactory.createExpressionFromText("assertEquals(${expression.leftOperand.text}, ${expression.rightOperand?.text})")
            replaceQueue.add(Pair(expression, newKotlinExpression))
        }
    }

    private fun convertAssertCallNumber(
        currentLabel: String,
        expression: GrMultiplicativeExpressionImpl,
        replaceQueue: MutableList<Pair<PsiElement, PsiElement>>
    ) {
        when (currentLabel) {
            TEST_LIFECYCLE_METHOD, GIVEN -> {
                // если вдруг есть проверка вызова в блоке given
//                when (val methodCall = expression.rightOperand) {
//                    is GrMethodCallExpression -> {
//                        convertCallMethods(GIVEN, methodCall, replaceQueue)
//                        expression.leftOperand.delete()
//                    }
//                    is GrShiftExpressionImpl -> { convertMockCallMethod(GIVEN, methodCall, replaceQueue) }
//                }
            }
            EXPECT, THEN -> {
                print("изменяем в THEN")
                val methodCall = (expression.rightOperand as? GrMethodCallExpression) ?: return
                val callsNumber = (expression.leftOperand as? GrLiteralImpl)?.value ?: return
                val callsNumberVerifierString = callsNumberVerifierString(callsNumber)

                val callObject = methodCall.children[0].children[0].text
                val methodCallString = methodCall.children[0].lastChild.text

                val newKotlinExpression =
                    groovyFactory.createExpressionFromText("verify($callObject$callsNumberVerifierString).$methodCallString()")
                replaceQueue.add(Pair(expression, newKotlinExpression))
            }
            else -> {
                print("изменяем внутрянку метода else блок для сравнения переменных")
            }
        }
    }

    private fun convertAssertCallNumberWithArguments(
        currentLabel: String,
        expression: GrShiftExpressionImpl,
        replaceQueue: MutableList<Pair<PsiElement, PsiElement>>
    ) {
        // если попалось выражение 2 * object.methodCall() в блоке given или в setup(), то это ошибка и генерировать verify не нужно
        if (currentLabel == TEST_LIFECYCLE_METHOD || currentLabel == GIVEN) return

        val methodCall =
            ((expression.leftOperand as? GrMultiplicativeExpressionImpl)?.rightOperand as? GrMethodCall) ?: return
        val methodArguments = (methodCall.children[1] as? GrArgumentListImpl)?.allArguments ?: return

        val callsNumber =
            ((expression.leftOperand as? GrMultiplicativeExpressionImpl)?.leftOperand as? GrLiteralImpl)?.value
                ?: return
        val callsNumberVerifierString = callsNumberVerifierString(callsNumber)

        val callObject = methodCall.children[0].children[0].text
        val methodCallString = methodCall.children[0].lastChild.text
        val methodCallArgumentStrings = methodArguments.map { it.text }

        val isMixedArgumentsWithWildcard =
            methodCallArgumentStrings.size > 1 && methodCallArgumentStrings.any { it == SINGLE_ARGUMENT_WILDCARD }

        val lastExpressionInLambda = (expression.rightOperand as? GrClosableBlock)?.statements?.lastOrNull()
        if (lastExpressionInLambda !is GrAssertStatement &&
            lastExpressionInLambda !is GrRelationalExpressionImpl &&
            lastExpressionInLambda !is GrAssignmentExpression
        ) {
            val mockExpression =
                groovyFactory.createStatementFromText("${methodCall.text} >> ${lastExpressionInLambda?.text}") as GrShiftExpressionImpl
            val convertedMockExpression = convertMockCallMethod(GIVEN, mockExpression, replaceQueue)
            convertedMockExpression?.let {
                givenBlockFirstElement?.addAfter(it)
            }
        }
        val assertStatementsInLambda = (expression.rightOperand as? GrClosableBlock)?.statements?.mapNotNull {
            (it as? GrAssertStatement)?.assertion?.lastChild?.text
        }?.toMutableList()

        val argumentsString = if (!assertStatementsInLambda.isNullOrEmpty()) {

            if (isMixedArgumentsWithWildcard) {

                val methodCallArgumentWithoutWildcards = methodCallArgumentStrings.map { argument ->
                    // если аргумент это вайлдкард, то попробовать заменить его на переменную из assert в лямбде
                    if (argument == SINGLE_ARGUMENT_WILDCARD) {
                        if (assertStatementsInLambda.isNotEmpty()) {
                            assertStatementsInLambda.removeAt(0)
                        } else {
                            SINGLE_ARGUMENT_WILDCARD
                        }
                    } else {
                        argument
                    }
                }
                wrapMethodArgumentsWithEquals(methodCallArgumentWithoutWildcards)
            } else {
                assertStatementsInLambda.joinToString(separator = ",") { "eq($it)" }
            }
        } else {
            wrapMethodArgumentsWithEquals(methodCallArgumentStrings)
        }

        val newKotlinExpression =
            groovyFactory.createExpressionFromText("verify($callObject$callsNumberVerifierString).$methodCallString($argumentsString)")

        replaceQueue.add(Pair(expression, newKotlinExpression))
    }

    private fun convertMockCallMethod(
        currentLabel: String,
        expression: GrShiftExpressionImpl,
        replaceQueue: MutableList<Pair<PsiElement, PsiElement>>
    ): GrStatement? {
        val methodCall = (expression.leftOperand as? GrMethodCall) ?: return null
        val methodArguments = (methodCall.children[1] as? GrArgumentListImpl)?.allArguments ?: return null

        if (currentLabel == TEST_LIFECYCLE_METHOD || currentLabel == GIVEN) {
            print("изменяем в GIVEN")
            // нужно проверять и обрабатывать случаи когда в given есть проверка вызова!!!
            val callObject = methodCall.children[0].children[0].text
            val methodCallString = methodCall.children[0].lastChild.text
            val methodCallArgumentStrings = methodArguments.map { it.text }

            val assertStatementsInLambda = (expression.rightOperand as? GrClosableBlock)?.statements?.mapNotNull {
                // (it as? GrAssertStatement)?.assertion?.lastChild?.text
                if (it is GrMethodCallExpression) {
                    print("если это вызов метода, то надо проверить и сконвертить его аргументы на предмет моков")
                    val newArgumentsArray =
                        (it as GrMethodCallExpressionImpl).argumentList.allArguments.joinToString(",") {
                            convertMockMethodArgument(it)
                        }
                    val argumentListStatement = groovyFactory.createArgumentListFromText("($newArgumentsArray)")
                    it.argumentList.replace(argumentListStatement)
                    it.text
                } else {
                    it.text
                }
            }?.toMutableList()

            val kotlinLambdaParametersString =
                (expression.rightOperand as? GrClosableBlock)?.allParameters?.mapIndexed { index, grParameter ->

                    val parameterName = grParameter.name
                    if (!parameterName.matches(ARGUMENT_WILDCARD_PATTERN)) {
                        val parameterType = grParameter.declaredType?.canonicalText
                        val parameterTypeString = if (parameterType != null) {
                            "as $parameterType"
                        } else {
                            ""
                        }
                        "val $parameterName = it.arguments[$index] $parameterTypeString"
                    } else {
                        ""
                    }

                }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

            assertStatementsInLambda?.addAll(0, kotlinLambdaParametersString)
            val convertLambdaStatements = assertStatementsInLambda?.joinToString("\n")

            val argumentsString = wrapMethodArgumentsWithEquals(methodCallArgumentStrings)

            val newKotlinExpression = if (expression.rightOperand is GrClosableBlock) {
                groovyFactory.createStatementFromText("whenever($callObject.$methodCallString($argumentsString)) doAnswer { \n$convertLambdaStatements \n}")
            } else {
                groovyFactory.createStatementFromText("whenever($callObject.$methodCallString($argumentsString)).doReturn(${expression.rightOperand?.text})")
            }

            replaceQueue.add(Pair(expression, newKotlinExpression))
            return newKotlinExpression

        } else {
            print("изменяем внутрянку метода else блок для сравнения переменных")
            return null
        }
    }

    private fun convertMockGetter(
        currentLabel: String,
        expression: GrShiftExpressionImpl,
        replaceQueue: MutableList<Pair<PsiElement, PsiElement>>
    ) {
        //storage.preferences122 >> 33
        val referenceExpression = (expression.leftOperand as? GrReferenceExpression) ?: return

        if (currentLabel == TEST_LIFECYCLE_METHOD || currentLabel == GIVEN) {
            print("изменяем в GIVEN")
            val callObject = referenceExpression.firstChild.text
            val propertyString = referenceExpression.lastChild.text

            val newKotlinExpression = groovyFactory.createStatementFromText(
                "whenever($callObject.$propertyString) doAnswer { ${expression.rightOperand?.text} }"
            )

            replaceQueue.add(Pair(expression, newKotlinExpression))
        }
    }

    private fun callsNumberVerifierString(callsNumber: Any): String {
        return when (callsNumber) {
            1 -> ""
            0 -> ", never()"
            else -> ", times($callsNumber)"
        }
    }

    private fun wrapMethodArgumentsWithEquals(arguments: List<String>): String {
        return arguments.joinToString(separator = ",") {
            if (it == SINGLE_ARGUMENT_WILDCARD || it == MULTIPLE_ARGUMENTS_WILDCARD) {
                "any()"
            } else {
                "eq($it)"
            }
        }
    }

    private fun convertVariableDeclaration(variable: GrVariable) {
        val variableDeclaration = (variable.parent as GrVariableDeclaration)
        val factory = GroovyPsiElementFactory.getInstance(project)

        val valExpr = factory.createExpressionFromText("val")
        val fieldClass = variableDeclaration.getTypeElementGroovyForVariable(variable)
        if (fieldClass != null) {

            if (variable.initializerGroovy == null) {//PresenterRoboRule presenterRule
                val statement1 = factory.createExpressionFromText("${variable.name} = ${fieldClass.text}()")
                fieldClass.replace(valExpr)
                variable.replace(statement1)
            } else {
                //ShareUtils shareUtils = Mock()
                //тут проверить initializer это мок??
                if (variable.initializerGroovy?.text == "null") {
                    fieldClass.replace(valExpr)
                    replaceWithKotlinTypeDeclaration(variable, variable.name, fieldClass.text)
                } else if (variable.isMock()) {
                    convertMockStatement(
                        fieldClass,
                        (variable.initializerGroovy as GrMethodCallExpressionImpl)
                    )
                }

                fieldClass.replace(valExpr)
            }

        } else {
            //тут проверить initializer это мок?? def eer = new PresenterRoboRule()
            if (variable.isMock()) {
                convertMockStatement(null, (variable.initializerGroovy as GrMethodCallExpressionImpl))
            }

            variableDeclaration.modifierList.replaceDefWith("val")
        }
    }

    private fun convertMockStatement(
        fieldClass: GrTypeElement?,
        methodCallExpression: GrMethodCallExpressionImpl
    ) {

        val newKotlinExpression = if (fieldClass != null) {
            print("ClassName prop = Mock()")
            groovyFactory.createExpressionFromText("mock<${fieldClass.text}>()")
        } else {
            val classArgument = methodCallExpression.argumentList.allArguments.getOrNull(0)?.text ?: "IgorekClassName"
            print("def prop = Mock(ClassName)")
            groovyFactory.createExpressionFromText("mock<$classArgument>()")
        }

        val mockLambda = methodCallExpression.closureArguments.getOrNull(0) as? GrClosableBlockImpl
        if (mockLambda != null) {
            print("есть лямбда")
            val kotlinLambdaStatements = mockLambda.statements.mapNotNull { it ->
                val shiftExpr = it as? GrShiftExpressionImpl
                if (shiftExpr != null) {

                    if (shiftExpr.rightOperand is GrListOrMapImpl) {
                        val listOrMapElement = shiftExpr.rightOperand as GrListOrMapImpl
                        if (listOrMapElement.isEmpty) {
                            listOrMapElement.replace(groovyFactory.createStatementFromText("emptyList()"))
                        } else {
                            val kotlinListStatement =
                                listOrMapElement.children.joinToString(",", "listOf(", ")") {
                                    it.text
                                }

                            listOrMapElement.replace(
                                groovyFactory.createStatementFromText(kotlinListStatement)
                            )
                        }
                    }

                    val methodCallArgumentStrings =
                        (shiftExpr.leftOperand as GrMethodCall).argumentList.allArguments.map { it.text }
                    // проверить, нужны ли eq() проверки для аргумента мок методов для глобального мок объекта или можно везде any() писать????
                    val kotlinArgumentsString = wrapMethodArgumentsWithEquals(methodCallArgumentStrings)
                    val kotlinArgumentsList = groovyFactory.createArgumentListFromText("($kotlinArgumentsString)")
                    (shiftExpr.leftOperand as GrMethodCall).argumentList.replace(kotlinArgumentsList)
                    "on { ${shiftExpr.leftOperand.text} } doReturn ${shiftExpr.rightOperand?.text}"
                } else {
                    null
                }
            }

            print("заменить Mock() на mock<Mock> отдельно и лямбду отдельно")
            methodCallExpression.firstChild.replace(newKotlinExpression)

            val kotlinMockLambdaString = kotlinLambdaStatements.joinToString("\n", "{ ", " }")
            val kotlinMockLambda = groovyFactory.createStatementFromText(kotlinMockLambdaString)
            mockLambda.replace(kotlinMockLambda)
        } else {
            print("нет лямбды, заменяю весь initializer компонент")
            methodCallExpression.replace(newKotlinExpression)
        }

    }

    private fun convertMethodArguments(method: GrMethod) {
        method.parameterList.parameters.forEach {
            if (!it.text.contains(':')) {
                replaceWithKotlinTypeDeclaration(it, it.name, it.typeElement?.text.orEmpty())
            }
        }
    }

    private fun replaceWithKotlinTypeDeclaration(element: PsiElement, variableName: String, variableType: String) {
        val type = variableType.ifEmpty { "Any" }
        val colonElement = groovyFactory.createColonElement()
        val assignmentStatement = groovyFactory.createStatementFromText("$variableName= $type")
        val replacedElement = element.replace(assignmentStatement)
        // replace = with :
        replacedElement.firstChild.nextSibling.replace(colonElement)
    }

    private fun createArgumentProvider(method: GrMethod) {
        val methodStatements = method.block?.statements ?: return
        val whereStatementIndex = methodStatements.indexOfFirst { it.text.contains("where") }
        if (whereStatementIndex < 0) return

        val paramsProviderClassName = method.name.split('_').joinToString("", postfix = "Provider") {
            it.capitalize()
        }
        val argumentProviderClass = groovyFactory.createTypeDefinition(
            """
            class $paramsProviderClassName extends ArgumentsProvider {
            
                def getArguments() {
                    return true
                }
            }
        """
        )

        method.modifierList.addAnnotation("TestWithParameters($paramsProviderClassName::Class)")

        // добавить сгенерированный класс над методом с аннотацией Unroll
        val linebreak = groovyFactory.createLineTerminator(2)
        argumentProviderClass.addAfter(linebreak, argumentProviderClass.lastChild)
        val addedArgumentProviderClass =
            method.addBefore(argumentProviderClass, method.modifierList) as GrTypeDefinition

        addedArgumentProviderClass.extendsClause?.firstChild?.replace(groovyFactory.createColonElement())
        replaceWithKotlinStarGenericMethodReturnType(
            addedArgumentProviderClass.methods[0].parameterList.nextSibling,
            "Array<Array<*>>"
        )
        (addedArgumentProviderClass.methods[0].modifierList as? GrModifierList)?.replaceDefWith("fun")

        //перенести таблицу из блока where в сгенерированный класс

        val parametersSet = arrayListOf<String>()
        val testMethodArguments = arrayListOf<String>()

        val whereStatement = (methodStatements[whereStatementIndex] as GrLabeledStatementImpl).statement
        if (whereStatement is GrBitwiseExpressionImpl) {
            methodStatements.forEachIndexed { index, grStatement ->
                if (index >= whereStatementIndex) {
                    if (grStatement is GrLabeledStatement) {
                        val kotlinParameters = grStatement.lastChild.text.replace(" ", "")
                            .split('|')
                        testMethodArguments.addAll(kotlinParameters)
                    } else {
                        val kotlinWhereSet = grStatement.text.replace(" ", "")
                            .split('|')
                            .joinToString(", ", "arrayOf(", ")")
                        parametersSet.add(kotlinWhereSet)
                    }
                    grStatement.delete()
                }
            }

        } else if (whereStatement is GrShiftExpressionImpl) {
            val arguments = (whereStatement.leftOperand as GrListOrMapImpl).text.trim('[', ']').split(',')
            testMethodArguments.addAll(arguments)

            (whereStatement.rightOperand as GrListOrMapImpl).children.forEach {
                val kotlinParameters = it.text.replace("""\n +|\[+|\]+""".toRegex(), "")
                    .split(',')
                    .joinToString(", ", "arrayOf(", ")")
                parametersSet.add(kotlinParameters)
            }
            whereStatement.delete()
        }

        val parametersSetString = parametersSet.joinToString(",\n", "arrayOf(\n", "\n)")
        val parametersSetStatement = groovyFactory.createStatementFromText(parametersSetString)

        (addedArgumentProviderClass.codeMethods[0].block?.statements?.get(0) as? GrReturnStatementImpl)?.returnValue
            ?.replace(parametersSetStatement)

        testMethodArguments.forEach {
            val parameter = groovyFactory.createParameter(it, "Any", null)
            method.parameterList.add(parameter)
        }
    }

    private fun replaceWithKotlinStarGenericMethodReturnType(element: PsiElement, returnType: String) {
        val returnTypeWithStarLabel = returnType.replace("*", "Star")
        val starElement = groovyFactory.createStarElement()
        val replacedElement = replaceWithKotlinMethodReturnType(element, returnTypeWithStarLabel)
        // replace Star with *

        recursivelyVisitAllElements(replacedElement) {
            if (it.text == "Star" && it is GrClassTypeElement) {
                it.replace(starElement)
            }
        }
    }

    private fun replaceWithKotlinMethodReturnType(element: PsiElement, returnType: String): PsiElement {
        val colonElement = groovyFactory.createColonElement()
        val assignmentStatement = groovyFactory.createStatementFromText("variableName= $returnType")
        val replacedElement = element.replace(assignmentStatement)
        // replace = with : and delete variableName
        replacedElement.firstChild.nextSibling.replace(colonElement)
        replacedElement.firstChild.delete()
        return replacedElement
    }
}