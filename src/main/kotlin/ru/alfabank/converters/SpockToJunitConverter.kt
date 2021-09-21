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
import ru.alfabank.*

private const val WHEN = "// when"
private const val GIVEN = "// given"
private const val THEN = "// then"
private const val EXPECT = "// expect"
private const val AND_LABEL = "// and"
private const val TEST_LIFECYCLE_METHOD = "// test lifecycle"

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

    private val replaceQueue = mutableListOf<Pair<PsiElement, PsiElement>>()

    fun transformToJunit() {
        deleteAllNewKeyWord()
// add runwith annotation and delete extends
        WriteCommandAction.runWriteCommandAction(project, null, null, Runnable {

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

        }, psiFile)

        WriteCommandAction.runWriteCommandAction(project, null, null, Runnable {

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

                field.convertVariableDeclaration()
            }

        }, psiFile)

//change method declaration
        WriteCommandAction.runWriteCommandAction(project, null, null, Runnable {

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

                    if (method.name == "setup") {
                        // add test lifecycle annotations
                        groovyFile.addImportStatement("org.junit.Before")
                        method.modifierList.addAnnotation("Before")
                    }
                }
            }

        }, psiFile)

        //change spock section labels with comment
        WriteCommandAction.runWriteCommandAction(project, null, null, Runnable {

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

        }, psiFile)


        // изменяем внутрянку метода
        WriteCommandAction.runWriteCommandAction(project, null, null, Runnable {

            for (method in typeDefinition.codeMethods) {
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

//                     изменяем объявления переменных


                    // изменяем выражения в блоках given when then expect


                    element.let {
                        when (it) {
                            is GrLabeledStatement -> {
                                if (it.firstChild.text != AND_LABEL) {
                                    //меняем контекст, так как для given when и then нужно по разному конвертировать выражения
                                    currentLabel = it.firstChild.text
                                }
                                (it.lastChild as? GrVariableDeclaration)?.variables?.first()
                                    ?.convertVariableDeclaration()
                                (it.lastChild as? GrMethodCallExpression)?.let { callExpr ->
                                    convertCallMethods(
                                        currentLabel,
                                        callExpr
                                    )
                                }
                                (it.lastChild as? GrRelationalExpressionImpl)?.let { callExpr ->
                                    convertAssertTwoOperand(
                                        currentLabel,
                                        callExpr
                                    )
                                }
                                (it.lastChild as? GrMultiplicativeExpressionImpl)?.let { callExpr ->
                                    convertAssertCallNumber(
                                        currentLabel,
                                        callExpr
                                    )
                                }
                                (it.lastChild as? GrShiftExpressionImpl)?.let { callExpr ->
                                    convertAssertCallNumberWithArguments(
                                        currentLabel,
                                        callExpr
                                    )
                                    convertMockCallMethod(currentLabel, callExpr)
                                }
                            }
                            is GrVariableDeclaration -> {
                                it.variables.first().convertVariableDeclaration()
                            }
                            is GrMethodCallExpression -> {
                                convertCallMethods(currentLabel, it)
                            }
                            is GrRelationalExpressionImpl -> {
                                convertAssertTwoOperand(currentLabel, it)
                            }
                            is GrMultiplicativeExpressionImpl -> {
                                convertAssertCallNumber(currentLabel, it)
                            }
                            is GrShiftExpressionImpl -> {
                                convertAssertCallNumberWithArguments(currentLabel, it)
                                convertMockCallMethod(currentLabel, it)
                            }
                            else -> {
                                print("изменяем внутрянку метода else блок для переменных")
                            }
                        }
                    }
                }
            }

            replaceQueue.forEach {
                it.first.replace(it.second)
            }
            replaceQueue.clear()

        }, psiFile)
    }

    private fun deleteAllNewKeyWord() {
        WriteCommandAction.runWriteCommandAction(project, null, null, {
            recursivelyVisitAllElements(psiFile) {
                if (it is GrNewExpression) {
                    it.navigationElement.firstChild.delete()
                }
            }
        }, psiFile)
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

    private fun convertCallMethods(currentLabel: String, expression: GrMethodCallExpression) {
        when (currentLabel) {
            TEST_LIFECYCLE_METHOD, GIVEN -> {
                val newArgumentsArray = expression.argumentList.allArguments.map {
                    val argumentMethodName = (it as? GrMethodCallExpressionImpl)?.navigationElement?.firstChild?.text
                    if (argumentMethodName == "Mock") {
                        val classArgument = it.argumentList.allArguments.getOrNull(0)?.text ?: "IgorekClassName"
                        "mock<$classArgument>()"
                    } else {
                        it.text
                    }
                }.joinToString(",")
                val argumentListStatement = groovyFactory.createArgumentListFromText("($newArgumentsArray)")
                //expression.argumentList.replace(argumentListStatement)
                replaceQueue.add(Pair(expression.argumentList, argumentListStatement))
                print("изменяем в GIVEN тут не надо ничего в самом вызове менять, но аргументы стоит проверить на передачу мок выражения")
            }
            WHEN -> {
                print("изменяем в WHEN")
            }
            EXPECT, THEN -> {
                print("изменяем в THEN")
                val newKotlinExpression = groovyFactory.createStatementFromText("assertTrue(${expression.text})")
                replaceQueue.add(Pair(expression, newKotlinExpression))
            }
            else -> {
                print("изменяем внутрянку метода else блок для методов")
            }
        }
    }

    private fun convertAssertTwoOperand(currentLabel: String, expression: GrRelationalExpressionImpl) {
        when (currentLabel) {
            TEST_LIFECYCLE_METHOD, GIVEN -> {
                print("изменяем в GIVEN")
            }
            WHEN -> {
                print("изменяем в WHEN")
            }
            EXPECT, THEN -> {
                print("изменяем в THEN")
                val newKotlinExpression =
                    groovyFactory.createExpressionFromText("assertEquals(${expression.leftOperand.text}, ${expression.rightOperand?.text})")
                replaceQueue.add(Pair(expression, newKotlinExpression))
            }
            else -> {
                print("изменяем внутрянку метода else блок для сравнения переменных")
            }
        }
    }

    private fun convertAssertCallNumber(currentLabel: String, expression: GrMultiplicativeExpressionImpl) {
        val methodCall = (expression.rightOperand as? GrMethodCall) ?: return

        when (currentLabel) {
            TEST_LIFECYCLE_METHOD, GIVEN -> {
                print("изменяем в GIVEN")
            }
            WHEN -> {
                print("изменяем в WHEN")
            }
            EXPECT, THEN -> {
                print("изменяем в THEN")

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

    private fun convertAssertCallNumberWithArguments(currentLabel: String, expression: GrShiftExpressionImpl) {
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
            val convertedMockExpression = convertMockCallMethod(GIVEN, mockExpression)
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
                    if (argument == SINGLE_ARGUMENT_WILDCARD) {
                        try {
                            assertStatementsInLambda.removeAt(0)
                        } catch (e: IndexOutOfBoundsException) {
                            e.printStackTrace()
                            print("the number of arguments is not equal to the number of checks")
                            SINGLE_ARGUMENT_WILDCARD
                        }
                    } else {
                        argument
                    }
                }
                wrapMethodArgumentsWithEquals(methodCallArgumentWithoutWildcards)
            } else {
                assertStatementsInLambda.joinToString(
                    separator = ",",
                    transform = { "eq($it)" }
                )
            }

        } else {
            wrapMethodArgumentsWithEquals(methodCallArgumentStrings)
        }

        val newKotlinExpression =
            groovyFactory.createExpressionFromText("verify($callObject$callsNumberVerifierString).$methodCallString($argumentsString)")

        replaceQueue.add(Pair(expression, newKotlinExpression))
    }

    private fun convertMockCallMethod(currentLabel: String, expression: GrShiftExpressionImpl): GrStatement? {
        val methodCall = (expression.leftOperand as? GrMethodCall) ?: return null
        val methodArguments = (methodCall.children[1] as? GrArgumentListImpl)?.allArguments ?: return null

        when (currentLabel) {
            TEST_LIFECYCLE_METHOD, GIVEN -> {
                print("изменяем в GIVEN")
// нужно проверять и обрабатывать случаи когда в given есть проверка вызова!!!
                val callObject = methodCall.children[0].children[0].text
                val methodCallString = methodCall.children[0].lastChild.text
                val methodCallArgumentStrings = methodArguments.map { it.text }

                val assertStatementsInLambda = (expression.rightOperand as? GrClosableBlock)?.statements?.mapNotNull {
                    // (it as? GrAssertStatement)?.assertion?.lastChild?.text
                    if (it is GrMethodCallExpression) {
                        print("если это вызов метода, то надо проверить и сконвертить его аргументы на предмет моков")
                        val newArgumentsArray = (it as GrMethodCallExpressionImpl).argumentList.allArguments.map {
                            val argumentMethodName =
                                (it as? GrMethodCallExpressionImpl)?.navigationElement?.firstChild?.text
                            if (argumentMethodName == "Mock") {
                                val classArgument = it.argumentList.allArguments.getOrNull(0)?.text ?: "IgorekClassName"
                                "mock<$classArgument>()"
                            } else {
                                it.text
                            }
                        }.joinToString(",")
                        val argumentListStatement = groovyFactory.createArgumentListFromText("($newArgumentsArray)")
                        it.argumentList.replace(argumentListStatement)
                        it.text
                        //convertCallMethods(currentLabel, methodCall)
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

            }
            WHEN -> {
                print("изменяем в WHEN")
                return null
            }
            THEN -> {
                return null
            }
            EXPECT -> {
                print("изменяем в EXPECT")
                return null
            }
            else -> {
                print("изменяем внутрянку метода else блок для сравнения переменных")
                return null
            }
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
        return arguments.joinToString(
            separator = ",",
            transform = {
                if (it == SINGLE_ARGUMENT_WILDCARD || it == MULTIPLE_ARGUMENTS_WILDCARD) {
                    "any()"
                } else {
                    "eq($it)"
                }
            }
        )
    }

    private fun replaceWildcardMethodArguments(arguments: List<String>): String {
        return arguments.joinToString(
            separator = ",",
            transform = {
                if (it == SINGLE_ARGUMENT_WILDCARD || it == MULTIPLE_ARGUMENTS_WILDCARD) {
                    "any()"
                } else {
                    it
                }
            }
        )
    }

    fun GrVariable.convertVariableDeclaration() {
        val variableDeclaration = (parent as GrVariableDeclaration)
        val factory = GroovyPsiElementFactory.getInstance(project)

        val valExpr = factory.createExpressionFromText("val")
        val fieldClass = variableDeclaration.getTypeElementGroovyForVariable(this)
        if (fieldClass != null) {

            if (initializerGroovy == null) {//PresenterRoboRule presenterRule
                val statement1 = factory.createExpressionFromText("$name = ${fieldClass.text}()")
                fieldClass.replace(valExpr)
                this.replace(statement1)
            } else {
//ShareUtils shareUtils = Mock()
                //тут проверить initializer это мок??
                if (initializerGroovy?.text == "null") {
                    //val statement1 = factory.createStatementFromText("$name: ${fieldClass.text}")

                    fieldClass.replace(valExpr)
                    replaceWithKotlinTypeDeclaration(this, name, fieldClass.text)
                    //this.replace(statement1)
                } else if (isMock()) {
                    convertMockStatement(
                        fieldClass,
                        (initializerGroovy as GrMethodCallExpressionImpl)
                    )
                } //else if (initializerGroovy is GrNewExpression) {
                //(initializerGroovy as GrNewExpression).navigationElement.firstChild.delete()
                // }

                fieldClass.replace(valExpr)
            }

        } else {
            //тут проверить initializer это мок?? def eer = new PresenterRoboRule()
            if (isMock()) {
                convertMockStatement(null, (initializerGroovy as GrMethodCallExpressionImpl))
            }

            // (initializerGroovy as? GrNewExpression)?.navigationElement?.firstChild?.delete()
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

                    if (shiftExpr.rightOperand is GrNewExpression) {
                        //   (shiftExpr.rightOperand as GrNewExpression).navigationElement.firstChild.delete()
                    } else if (shiftExpr.rightOperand is GrListOrMapImpl) {
                        val listOrMapElement = shiftExpr.rightOperand as GrListOrMapImpl
                        if (listOrMapElement.isEmpty) {
                            listOrMapElement.replace(groovyFactory.createStatementFromText("emptyList()"))
                        } else {
                            //(shiftExpr.rightOperand as GrListOrMapImpl).children.forEach { listExpression ->
//                                if (listExpression is GrNewExpression) {
//                                    (listExpression as? GrNewExpression)?.navigationElement?.firstChild?.delete()
//                                }
                            //}
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

    private fun GrVariable.isMock(): Boolean {
        val methodName = (initializerGroovy as? GrMethodCallExpressionImpl)?.navigationElement?.firstChild?.text
        return methodName == "Mock"
    }

    private fun GrMethod.isTestMethod(): Boolean {
        val containsTestAnnotation =
            this.modifierList.modifiers.any { it.text == "@Test" || it.text == "@Before" || it.text == "@After" }
        val methodBlock = this.block as GrOpenBlock
        return methodBlock.text.contains(GIVEN) ||
                methodBlock.text.contains(WHEN) ||
                methodBlock.text.contains(EXPECT) ||
                containsTestAnnotation
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
        val colonElement = getColonElement()
        val assignmentStatement = groovyFactory.createStatementFromText("$variableName= $type")
        val replacedElement = element.replace(assignmentStatement)
        // replace = with :
        replacedElement.firstChild.nextSibling.replace(colonElement)
    }

    private fun getColonElement(): PsiElement {
        return groovyFactory.createStatementFromText("label: labeled").firstChild.nextSibling
    }

    private fun getStarElement(): PsiElement {
        return groovyFactory.createStatementFromText("label* labeled").firstChild.nextSibling
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

        addedArgumentProviderClass.extendsClause?.firstChild?.replace(getColonElement())
        replaceWithKotlinStarGenericMethodReturnType(
            addedArgumentProviderClass.methods[0].parameterList.nextSibling,
            "Array<Array<*>>"
        )
        (addedArgumentProviderClass.methods[0].modifierList as? GrModifierList)?.replaceDefWith("fun")

        //перенести таблицу из блока where в сгенерированный класс

        val parametersSet = arrayListOf<String>()
        val methodArguments = arrayListOf<String>()

        val whereStatement = (methodStatements[whereStatementIndex] as GrLabeledStatementImpl).statement
        if (whereStatement is GrBitwiseExpressionImpl) {
            methodStatements.forEachIndexed { index, grStatement ->
                if (index >= whereStatementIndex) {
                    print("create 12212")
                    if (grStatement is GrLabeledStatement) {
                        val kotlinParameters = grStatement.lastChild.text.replace(" ", "")
                            .split('|')
                        methodArguments.addAll(kotlinParameters)
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
            methodArguments.addAll(arguments)

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

        methodArguments.forEach {
            val parameter = groovyFactory.createParameter(it, "Any", null)
            method.parameterList.add(parameter)
        }
        // convertMethodArguments(method) не нужно, дальше пойдет конвертация параметров для всех методов


        print("create argument provider")
    }

    private fun replaceWithKotlinStarGenericMethodReturnType(element: PsiElement, returnType: String) {
        val returnTypeWithStarLabel = returnType.replace("*", "Star")
        val starElement = getStarElement()
        val replacedElement = replaceWithKotlinMethodReturnType(element, returnTypeWithStarLabel)
        // replace Star with *

        recursivelyVisitAllElements(replacedElement) {
            if (it.text == "Star" && it is GrClassTypeElement) {
                it.replace(starElement)
            }
        }
    }

    private fun replaceWithKotlinMethodReturnType(element: PsiElement, returnType: String): PsiElement {
        val colonElement = getColonElement()
        val assignmentStatement = groovyFactory.createStatementFromText("variableName= $returnType")
        val replacedElement = element.replace(assignmentStatement)
        // replace = with : and delete variableName
        replacedElement.firstChild.nextSibling.replace(colonElement)
        replacedElement.firstChild.delete()
        return replacedElement
    }
}