package org.javacs.kt.j2k

import org.javacs.kt.LOG
import org.jetbrains.kotlin.com.intellij.psi.*
import org.jetbrains.kotlin.com.intellij.psi.javadoc.*

/**
 * A Psi visitor that converts Java elements into
 * Kotlin code.
 */
class JavaElementConverter(
    private val indentLevel: Int = 0,
    private val indentSize: Int = 4 // spaces
) : JavaElementVisitor() {
    var translatedKotlinCode: String? = null
        private set
    private val indent: String = "".padStart(indentLevel * indentSize, ' ')

    private val String?.spacePrefixed: String
        get() = this?.let { " $it" } ?: ""

    /** Convenience method to perform construction, visit and translation in one call. */
    private fun PsiElement?.translate(indentDelta: Int = 0): String? = JavaElementConverter(indentLevel + indentDelta)
        .also { this?.accept(it) }
        .translatedKotlinCode

    private fun nextIndent(indentDelta: Int = 1): String = ""
        .padStart((indentLevel + indentDelta) * indentSize, ' ')

    private fun List<String>.buildCodeBlock(indentDelta: Int = 1): String {
        val indentedStatements = this
            .map { "${nextIndent(indentDelta)}$it" }
            .joinToString(separator = "\n")
        return "{$indentedStatements\n$indent}"
    }

    private fun PsiType.translate(): String = accept(JavaTypeConverter)

    override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
        super.visitAnonymousClass(aClass)
        LOG.warn("J2K can not convert AnonymousClass yet")
    }

    override fun visitArrayAccessExpression(expression: PsiArrayAccessExpression) {
        super.visitArrayAccessExpression(expression)
        LOG.warn("J2K can not convert ArrayAccessExpression yet")
    }

    override fun visitArrayInitializerExpression(expression: PsiArrayInitializerExpression) {
        super.visitArrayInitializerExpression(expression)
        LOG.warn("J2K can not convert ArrayInitializerExpression yet")
    }

    override fun visitAssertStatement(statement: PsiAssertStatement) {
        super.visitAssertStatement(statement)
        LOG.warn("J2K can not convert AssertStatement yet")
    }

    override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
        super.visitAssignmentExpression(expression)
        LOG.warn("J2K can not convert AssignmentExpression yet")
    }

    override fun visitBinaryExpression(expression: PsiBinaryExpression) {
        super.visitBinaryExpression(expression)
        LOG.warn("J2K can not convert BinaryExpression yet")
    }

    override fun visitBlockStatement(statement: PsiBlockStatement) {
        super.visitBlockStatement(statement)
        LOG.warn("J2K can not convert BlockStatement yet")
    }

    override fun visitBreakStatement(statement: PsiBreakStatement) {
        super.visitBreakStatement(statement)
        LOG.warn("J2K can not convert BreakStatement yet")
    }

    override fun visitClass(aClass: PsiClass) {
        val (staticMembers, instanceMembers) = aClass.children
            .mapNotNull { it as? PsiMember }
            .partition { it.hasModifierProperty(PsiModifier.STATIC) }

        val translatedInstanceMembers = instanceMembers
            .mapNotNull { it.translate(indentDelta = 1) }

        val translatedCompanion = if (!staticMembers.isEmpty()) {
            val translatedCompanionBlock = staticMembers
                .map { "@JvmStatic ${it.translate(indentDelta = 2)}" }
                .buildCodeBlock()
                .spacePrefixed
            "companion object$translatedCompanionBlock"
        } else ""

        val translatedBody = (listOf(translatedCompanion) + translatedInstanceMembers)
            .buildCodeBlock()
            .spacePrefixed

        translatedKotlinCode = "class ${aClass.qualifiedName}$translatedBody"
    }

    override fun visitClassInitializer(initializer: PsiClassInitializer) {
        translatedKotlinCode = "init${initializer.body.translate().spacePrefixed}"
    }

    override fun visitClassObjectAccessExpression(expression: PsiClassObjectAccessExpression) {
        super.visitClassObjectAccessExpression(expression)
        LOG.warn("J2K can not convert ClassObjectAccessExpression yet")
    }

    override fun visitCodeBlock(block: PsiCodeBlock) {
        val translated = block.statements.mapNotNull { it.translate(indentDelta = 1) }
        translatedKotlinCode = translated.buildCodeBlock()
    }

    override fun visitConditionalExpression(expression: PsiConditionalExpression) {
        super.visitConditionalExpression(expression)
        LOG.warn("J2K can not convert ConditionalExpression yet")
    }

    override fun visitContinueStatement(statement: PsiContinueStatement) {
        translatedKotlinCode = "continue${statement.labelIdentifier.translate().spacePrefixed}"
    }

    override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
        super.visitDeclarationStatement(statement)
        LOG.warn("J2K can not convert DeclarationStatement yet")
    }

    override fun visitDocComment(comment: PsiDocComment) {
        super.visitDocComment(comment)
        LOG.warn("J2K can not convert DocComment yet")
    }

    override fun visitDocTag(tag: PsiDocTag) {
        super.visitDocTag(tag)
        LOG.warn("J2K can not convert DocTag yet")
    }

    override fun visitDocTagValue(value: PsiDocTagValue) {
        super.visitDocTagValue(value)
        LOG.warn("J2K can not convert DocTagValue yet")
    }

    override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
        super.visitDoWhileStatement(statement)
        LOG.warn("J2K can not convert DoWhileStatement yet")
    }

    override fun visitEmptyStatement(statement: PsiEmptyStatement) {
        translatedKotlinCode = ""
    }

    override fun visitExpression(expression: PsiExpression) {
        translatedKotlinCode = expression.text // Perform no conversion if no concrete visitor could be found
    }

    override fun visitExpressionList(list: PsiExpressionList) {
        super.visitExpressionList(list)
        LOG.warn("J2K can not convert ExpressionList yet")
    }

    override fun visitExpressionListStatement(statement: PsiExpressionListStatement) {
        super.visitExpressionListStatement(statement)
        LOG.warn("J2K can not convert ExpressionListStatement yet")
    }

    override fun visitExpressionStatement(statement: PsiExpressionStatement) {
        super.visitExpressionStatement(statement)
        LOG.warn("J2K can not convert ExpressionStatement yet")
    }

    override fun visitField(field: PsiField) {
        super.visitField(field)
        LOG.warn("J2K can not convert Field yet")
    }

    override fun visitForStatement(statement: PsiForStatement) {
        super.visitForStatement(statement)
        LOG.warn("J2K can not convert ForStatement yet")
    }

    override fun visitForeachStatement(statement: PsiForeachStatement) {
        super.visitForeachStatement(statement)
        LOG.warn("J2K can not convert ForeachStatement yet")
    }

    override fun visitIdentifier(identifier: PsiIdentifier) {
        translatedKotlinCode = identifier.text
    }

    override fun visitIfStatement(statement: PsiIfStatement) {
        val translatedIf = "if (${statement.condition.translate()})${statement.thenBranch.translate().spacePrefixed}"
        val translatedElse = statement.elseBranch.translate()?.let { "else $it" }.spacePrefixed
        translatedKotlinCode = translatedIf + translatedElse
    }

    override fun visitImportList(list: PsiImportList) {
        super.visitImportList(list)
        LOG.warn("J2K can not convert ImportList yet")
    }

    override fun visitImportStatement(statement: PsiImportStatement) {
        super.visitImportStatement(statement)
        LOG.warn("J2K can not convert ImportStatement yet")
    }

    override fun visitImportStaticStatement(statement: PsiImportStaticStatement) {
        super.visitImportStaticStatement(statement)
        LOG.warn("J2K can not convert ImportStaticStatement yet")
    }

    override fun visitInlineDocTag(tag: PsiInlineDocTag) {
        super.visitInlineDocTag(tag)
        LOG.warn("J2K can not convert InlineDocTag yet")
    }

    override fun visitInstanceOfExpression(expression: PsiInstanceOfExpression) {
        super.visitInstanceOfExpression(expression)
        LOG.warn("J2K can not convert InstanceOfExpression yet")
    }

    override fun visitJavaToken(token: PsiJavaToken) {
        super.visitJavaToken(token)
        LOG.warn("J2K can not convert JavaToken yet")
    }

    override fun visitKeyword(keyword: PsiKeyword) {
        super.visitKeyword(keyword)
        LOG.warn("J2K can not convert Keyword yet")
    }

    override fun visitLabeledStatement(statement: PsiLabeledStatement) {
        super.visitLabeledStatement(statement)
        LOG.warn("J2K can not convert LabeledStatement yet")
    }

    override fun visitLiteralExpression(expression: PsiLiteralExpression) {
        super.visitLiteralExpression(expression)
        LOG.warn("J2K can not convert LiteralExpression yet")
    }

    override fun visitLocalVariable(variable: PsiLocalVariable) {
        super.visitLocalVariable(variable)
        LOG.warn("J2K can not convert LocalVariable yet")
    }

    override fun visitMethod(method: PsiMethod) {
        // TODO: Type parameters, annotations, modifiers, ...
        val translatedBody = method.body.translate().spacePrefixed
        translatedKotlinCode = "fun ${method.name}(${method.parameterList.translate()})$translatedBody"
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        super.visitMethodCallExpression(expression)
        LOG.warn("J2K can not convert MethodCallExpression yet")
    }

    override fun visitCallExpression(callExpression: PsiCallExpression) {
        super.visitCallExpression(callExpression)
        LOG.warn("J2K can not convert CallExpression yet")
    }

    override fun visitModifierList(list: PsiModifierList) {
        super.visitModifierList(list)
        LOG.warn("J2K can not convert ModifierList yet")
    }

    override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        LOG.warn("J2K can not convert NewExpression yet")
    }

    override fun visitPackage(aPackage: PsiPackage) {
        super.visitPackage(aPackage)
        LOG.warn("J2K can not convert Package yet")
    }

    override fun visitPackageStatement(statement: PsiPackageStatement) {
        super.visitPackageStatement(statement)
        LOG.warn("J2K can not convert PackageStatement yet")
    }

    override fun visitParameter(parameter: PsiParameter) {
        // TODO: Varargs, ...
        translatedKotlinCode = "${parameter.name}: ${parameter.type.translate()}"
    }

    override fun visitReceiverParameter(parameter: PsiReceiverParameter) {
        super.visitReceiverParameter(parameter)
        LOG.warn("J2K can not convert ReceiverParameter yet")
    }

    override fun visitParameterList(list: PsiParameterList) {
        translatedKotlinCode = list.parameters
            .mapNotNull { it.translate() }
            .joinToString(separator = ", ")
    }

    override fun visitParenthesizedExpression(expression: PsiParenthesizedExpression) {
        super.visitParenthesizedExpression(expression)
        LOG.warn("J2K can not convert ParenthesizedExpression yet")
    }

    override fun visitUnaryExpression(expression: PsiUnaryExpression) {
        super.visitUnaryExpression(expression)
        LOG.warn("J2K can not convert UnaryExpression yet")
    }

    override fun visitPostfixExpression(expression: PsiPostfixExpression) {
        super.visitPostfixExpression(expression)
        LOG.warn("J2K can not convert PostfixExpression yet")
    }

    override fun visitPrefixExpression(expression: PsiPrefixExpression) {
        super.visitPrefixExpression(expression)
        LOG.warn("J2K can not convert PrefixExpression yet")
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
        super.visitReferenceElement(reference)
        LOG.warn("J2K can not convert ReferenceElement yet")
    }

    override fun visitImportStaticReferenceElement(reference: PsiImportStaticReferenceElement) {
        super.visitImportStaticReferenceElement(reference)
        LOG.warn("J2K can not convert ImportStaticReferenceElement yet")
    }

    override fun visitReferenceExpression(expression: PsiReferenceExpression) {}

    override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        super.visitMethodReferenceExpression(expression)
        LOG.warn("J2K can not convert MethodReferenceExpression yet")
    }

    override fun visitReferenceList(list: PsiReferenceList) {
        super.visitReferenceList(list)
        LOG.warn("J2K can not convert ReferenceList yet")
    }

    override fun visitReferenceParameterList(list: PsiReferenceParameterList) {
        super.visitReferenceParameterList(list)
        LOG.warn("J2K can not convert ReferenceParameterList yet")
    }

    override fun visitTypeParameterList(list: PsiTypeParameterList) {
        super.visitTypeParameterList(list)
        LOG.warn("J2K can not convert TypeParameterList yet")
    }

    override fun visitReturnStatement(statement: PsiReturnStatement) {
        super.visitReturnStatement(statement)
        LOG.warn("J2K can not convert ReturnStatement yet")
    }

    override fun visitStatement(statement: PsiStatement) {
        super.visitStatement(statement)
        LOG.warn("J2K can not convert Statement yet")
    }

    override fun visitSuperExpression(expression: PsiSuperExpression) {
        super.visitSuperExpression(expression)
        LOG.warn("J2K can not convert SuperExpression yet")
    }

    override fun visitSwitchLabelStatement(statement: PsiSwitchLabelStatement) {
        super.visitSwitchLabelStatement(statement)
        LOG.warn("J2K can not convert SwitchLabelStatement yet")
    }

    override fun visitSwitchLabeledRuleStatement(statement: PsiSwitchLabeledRuleStatement) {
        super.visitSwitchLabeledRuleStatement(statement)
        LOG.warn("J2K can not convert SwitchLabeledRuleStatement yet")
    }

    override fun visitSwitchStatement(statement: PsiSwitchStatement) {
        super.visitSwitchStatement(statement)
        LOG.warn("J2K can not convert SwitchStatement yet")
    }

    override fun visitSynchronizedStatement(statement: PsiSynchronizedStatement) {
        super.visitSynchronizedStatement(statement)
        LOG.warn("J2K can not convert SynchronizedStatement yet")
    }

    override fun visitThisExpression(expression: PsiThisExpression) {
        super.visitThisExpression(expression)
        LOG.warn("J2K can not convert ThisExpression yet")
    }

    override fun visitThrowStatement(statement: PsiThrowStatement) {
        super.visitThrowStatement(statement)
        LOG.warn("J2K can not convert ThrowStatement yet")
    }

    override fun visitTryStatement(statement: PsiTryStatement) {
        super.visitTryStatement(statement)
        LOG.warn("J2K can not convert TryStatement yet")
    }

    override fun visitCatchSection(section: PsiCatchSection) {
        super.visitCatchSection(section)
        LOG.warn("J2K can not convert CatchSection yet")
    }

    override fun visitResourceList(resourceList: PsiResourceList) {
        super.visitResourceList(resourceList)
        LOG.warn("J2K can not convert ResourceList yet")
    }

    override fun visitResourceVariable(variable: PsiResourceVariable) {
        super.visitResourceVariable(variable)
        LOG.warn("J2K can not convert ResourceVariable yet")
    }

    override fun visitResourceExpression(expression: PsiResourceExpression) {
        super.visitResourceExpression(expression)
        LOG.warn("J2K can not convert ResourceExpression yet")
    }

    override fun visitTypeElement(type: PsiTypeElement) {
        super.visitTypeElement(type)
        LOG.warn("J2K can not convert TypeElement yet")
    }

    override fun visitTypeCastExpression(expression: PsiTypeCastExpression) {
        super.visitTypeCastExpression(expression)
        LOG.warn("J2K can not convert TypeCastExpression yet")
    }

    override fun visitVariable(variable: PsiVariable) {
        super.visitVariable(variable)
        LOG.warn("J2K can not convert Variable yet")
    }

    override fun visitWhileStatement(statement: PsiWhileStatement) {
        super.visitWhileStatement(statement)
        LOG.warn("J2K can not convert WhileStatement yet")
    }

    override fun visitJavaFile(file: PsiJavaFile) {
        // TODO: Package declarations, imports, ....
        translatedKotlinCode = file.children.asSequence()
            .mapNotNull { it.translate() }
            .joinToString(separator = "\n")
    }

    override fun visitImplicitVariable(variable: ImplicitVariable) {
        super.visitImplicitVariable(variable)
        LOG.warn("J2K can not convert ImplicitVariable yet")
    }

    override fun visitDocToken(token: PsiDocToken) {
        super.visitDocToken(token)
        LOG.warn("J2K can not convert DocToken yet")
    }

    override fun visitTypeParameter(classParameter: PsiTypeParameter) {
        super.visitTypeParameter(classParameter)
        LOG.warn("J2K can not convert TypeParameter yet")
    }

    override fun visitAnnotation(annotation: PsiAnnotation) {
        super.visitAnnotation(annotation)
        LOG.warn("J2K can not convert Annotation yet")
    }

    override fun visitAnnotationParameterList(list: PsiAnnotationParameterList) {
        super.visitAnnotationParameterList(list)
        LOG.warn("J2K can not convert AnnotationParameterList yet")
    }

    override fun visitAnnotationArrayInitializer(initializer: PsiArrayInitializerMemberValue) {
        super.visitAnnotationArrayInitializer(initializer)
        LOG.warn("J2K can not convert AnnotationArrayInitializer yet")
    }

    override fun visitNameValuePair(pair: PsiNameValuePair) {
        super.visitNameValuePair(pair)
        LOG.warn("J2K can not convert NameValuePair yet")
    }

    override fun visitAnnotationMethod(method: PsiAnnotationMethod) {
        super.visitAnnotationMethod(method)
        LOG.warn("J2K can not convert AnnotationMethod yet")
    }

    override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
        super.visitEnumConstant(enumConstant)
        LOG.warn("J2K can not convert EnumConstant yet")
    }

    override fun visitEnumConstantInitializer(enumConstantInitializer: PsiEnumConstantInitializer) {
        super.visitEnumConstantInitializer(enumConstantInitializer)
        LOG.warn("J2K can not convert EnumConstantInitializer yet")
    }

    override fun visitCodeFragment(codeFragment: JavaCodeFragment) {
        super.visitCodeFragment(codeFragment)
        LOG.warn("J2K can not convert CodeFragment yet")
    }

    override fun visitPolyadicExpression(expression: PsiPolyadicExpression) {
        super.visitPolyadicExpression(expression)
        LOG.warn("J2K can not convert PolyadicExpression yet")
    }

    override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        super.visitLambdaExpression(expression)
        LOG.warn("J2K can not convert LambdaExpression yet")
    }

    override fun visitSwitchExpression(expression: PsiSwitchExpression) {
        super.visitSwitchExpression(expression)
        LOG.warn("J2K can not convert SwitchExpression yet")
    }

    override fun visitModule(module: PsiJavaModule) {
        super.visitModule(module)
        LOG.warn("J2K can not convert Module yet")
    }

    override fun visitModuleReferenceElement(refElement: PsiJavaModuleReferenceElement) {
        super.visitModuleReferenceElement(refElement)
        LOG.warn("J2K can not convert ModuleReferenceElement yet")
    }

    override fun visitModuleStatement(statement: PsiStatement) {
        super.visitModuleStatement(statement)
        LOG.warn("J2K can not convert ModuleStatement yet")
    }

    override fun visitRequiresStatement(statement: PsiRequiresStatement) {
        super.visitRequiresStatement(statement)
        LOG.warn("J2K can not convert RequiresStatement yet")
    }

    override fun visitPackageAccessibilityStatement(statement: PsiPackageAccessibilityStatement) {
        super.visitPackageAccessibilityStatement(statement)
        LOG.warn("J2K can not convert PackageAccessibilityStatement yet")
    }

    override fun visitUsesStatement(statement: PsiUsesStatement) {
        super.visitUsesStatement(statement)
        LOG.warn("J2K can not convert UsesStatement yet")
    }

    override fun visitProvidesStatement(statement: PsiProvidesStatement) {
        super.visitProvidesStatement(statement)
        LOG.warn("J2K can not convert ProvidesStatement yet")
    }
}

