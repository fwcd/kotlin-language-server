package org.javacs.kt.j2k

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
    private val nextIndent: String = "".padStart((indentLevel + 1) * indentSize, ' ')

    private val String?.spacePrefixed: String
        get() = this?.let { " $it" } ?: ""

    /** Convenience method to perform construction, visit and translation in one call. */
    private fun PsiElement?.translate(indentDelta: Int = 0): String? = JavaElementConverter(indentLevel + indentDelta)
        .also { this?.accept(it) }
        .translatedKotlinCode

    override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
        super.visitAnonymousClass(aClass)
    }

    override fun visitArrayAccessExpression(expression: PsiArrayAccessExpression) {
        super.visitArrayAccessExpression(expression)
    }

    override fun visitArrayInitializerExpression(expression: PsiArrayInitializerExpression) {
        super.visitArrayInitializerExpression(expression)
    }

    override fun visitAssertStatement(statement: PsiAssertStatement) {
        super.visitAssertStatement(statement)
    }

    override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
        super.visitAssignmentExpression(expression)
    }

    override fun visitBinaryExpression(expression: PsiBinaryExpression) {
        super.visitBinaryExpression(expression)
    }

    override fun visitBlockStatement(statement: PsiBlockStatement) {
        super.visitBlockStatement(statement)
    }

    override fun visitBreakStatement(statement: PsiBreakStatement) {
        super.visitBreakStatement(statement)
    }

    override fun visitClass(aClass: PsiClass) {
        translatedKotlinCode = aClass.qualifiedName
    }

    override fun visitClassInitializer(initializer: PsiClassInitializer) {
        translatedKotlinCode = "class ${initializer.containingClass?.qualifiedName}${initializer.body.translate().spacePrefixed}"
    }

    override fun visitClassObjectAccessExpression(expression: PsiClassObjectAccessExpression) {
        super.visitClassObjectAccessExpression(expression)
    }

    override fun visitCodeBlock(block: PsiCodeBlock) {
        val indentedStatements = block.statements
            .map { "$nextIndent${it.translate(indentDelta = 1)}" }
            .joinToString(separator = "\n")
        translatedKotlinCode = "{$indentedStatements\n$indent}"
    }

    override fun visitConditionalExpression(expression: PsiConditionalExpression) {
        super.visitConditionalExpression(expression)
    }

    override fun visitContinueStatement(statement: PsiContinueStatement) {
        translatedKotlinCode = "continue${statement.labelIdentifier.translate().spacePrefixed}"
    }

    override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
        super.visitDeclarationStatement(statement)
    }

    override fun visitDocComment(comment: PsiDocComment) {
        super.visitDocComment(comment)
    }

    override fun visitDocTag(tag: PsiDocTag) {
        super.visitDocTag(tag)
    }

    override fun visitDocTagValue(value: PsiDocTagValue) {
        super.visitDocTagValue(value)
    }

    override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
        super.visitDoWhileStatement(statement)
    }

    override fun visitEmptyStatement(statement: PsiEmptyStatement) {
        translatedKotlinCode = ""
    }

    override fun visitExpression(expression: PsiExpression) {
        translatedKotlinCode = expression.text // Perform no conversion if no concrete visitor could be found
    }

    override fun visitExpressionList(list: PsiExpressionList) {
        super.visitExpressionList(list)
    }

    override fun visitExpressionListStatement(statement: PsiExpressionListStatement) {
        super.visitExpressionListStatement(statement)
    }

    override fun visitExpressionStatement(statement: PsiExpressionStatement) {
        super.visitExpressionStatement(statement)
    }

    override fun visitField(field: PsiField) {
        super.visitField(field)
    }

    override fun visitForStatement(statement: PsiForStatement) {
        super.visitForStatement(statement)
    }

    override fun visitForeachStatement(statement: PsiForeachStatement) {
        super.visitForeachStatement(statement)
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
    }

    override fun visitImportStatement(statement: PsiImportStatement) {
        super.visitImportStatement(statement)
    }

    override fun visitImportStaticStatement(statement: PsiImportStaticStatement) {
        super.visitImportStaticStatement(statement)
    }

    override fun visitInlineDocTag(tag: PsiInlineDocTag) {
        super.visitInlineDocTag(tag)
    }

    override fun visitInstanceOfExpression(expression: PsiInstanceOfExpression) {
        super.visitInstanceOfExpression(expression)
    }

    override fun visitJavaToken(token: PsiJavaToken) {
        super.visitJavaToken(token)
    }

    override fun visitKeyword(keyword: PsiKeyword) {
        super.visitKeyword(keyword)
    }

    override fun visitLabeledStatement(statement: PsiLabeledStatement) {
        super.visitLabeledStatement(statement)
    }

    override fun visitLiteralExpression(expression: PsiLiteralExpression) {
        super.visitLiteralExpression(expression)
    }

    override fun visitLocalVariable(variable: PsiLocalVariable) {
        super.visitLocalVariable(variable)
    }

    override fun visitMethod(method: PsiMethod) {
        super.visitMethod(method)
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        super.visitMethodCallExpression(expression)
    }

    override fun visitCallExpression(callExpression: PsiCallExpression) {
        super.visitCallExpression(callExpression)
    }

    override fun visitModifierList(list: PsiModifierList) {
        super.visitModifierList(list)
    }

    override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
    }

    override fun visitPackage(aPackage: PsiPackage) {
        super.visitPackage(aPackage)
    }

    override fun visitPackageStatement(statement: PsiPackageStatement) {
        super.visitPackageStatement(statement)
    }

    override fun visitParameter(parameter: PsiParameter) {
        super.visitParameter(parameter)
    }

    override fun visitReceiverParameter(parameter: PsiReceiverParameter) {
        super.visitReceiverParameter(parameter)
    }

    override fun visitParameterList(list: PsiParameterList) {
        super.visitParameterList(list)
    }

    override fun visitParenthesizedExpression(expression: PsiParenthesizedExpression) {
        super.visitParenthesizedExpression(expression)
    }

    override fun visitUnaryExpression(expression: PsiUnaryExpression) {
        super.visitUnaryExpression(expression)
    }

    override fun visitPostfixExpression(expression: PsiPostfixExpression) {
        super.visitPostfixExpression(expression)
    }

    override fun visitPrefixExpression(expression: PsiPrefixExpression) {
        super.visitPrefixExpression(expression)
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
        super.visitReferenceElement(reference)
    }

    override fun visitImportStaticReferenceElement(reference: PsiImportStaticReferenceElement) {
        super.visitImportStaticReferenceElement(reference)
    }

    override fun visitReferenceExpression(expression: PsiReferenceExpression) {}

    override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        super.visitMethodReferenceExpression(expression)
    }

    override fun visitReferenceList(list: PsiReferenceList) {
        super.visitReferenceList(list)
    }

    override fun visitReferenceParameterList(list: PsiReferenceParameterList) {
        super.visitReferenceParameterList(list)
    }

    override fun visitTypeParameterList(list: PsiTypeParameterList) {
        super.visitTypeParameterList(list)
    }

    override fun visitReturnStatement(statement: PsiReturnStatement) {
        super.visitReturnStatement(statement)
    }

    override fun visitStatement(statement: PsiStatement) {
        super.visitStatement(statement)
    }

    override fun visitSuperExpression(expression: PsiSuperExpression) {
        super.visitSuperExpression(expression)
    }

    override fun visitSwitchLabelStatement(statement: PsiSwitchLabelStatement) {
        super.visitSwitchLabelStatement(statement)
    }

    override fun visitSwitchLabeledRuleStatement(statement: PsiSwitchLabeledRuleStatement) {
        super.visitSwitchLabeledRuleStatement(statement)
    }

    override fun visitSwitchStatement(statement: PsiSwitchStatement) {
        super.visitSwitchStatement(statement)
    }

    override fun visitSynchronizedStatement(statement: PsiSynchronizedStatement) {
        super.visitSynchronizedStatement(statement)
    }

    override fun visitThisExpression(expression: PsiThisExpression) {
        super.visitThisExpression(expression)
    }

    override fun visitThrowStatement(statement: PsiThrowStatement) {
        super.visitThrowStatement(statement)
    }

    override fun visitTryStatement(statement: PsiTryStatement) {
        super.visitTryStatement(statement)
    }

    override fun visitCatchSection(section: PsiCatchSection) {
        super.visitCatchSection(section)
    }

    override fun visitResourceList(resourceList: PsiResourceList) {
        super.visitResourceList(resourceList)
    }

    override fun visitResourceVariable(variable: PsiResourceVariable) {
        super.visitResourceVariable(variable)
    }

    override fun visitResourceExpression(expression: PsiResourceExpression) {
        super.visitResourceExpression(expression)
    }

    override fun visitTypeElement(type: PsiTypeElement) {
        super.visitTypeElement(type)
    }

    override fun visitTypeCastExpression(expression: PsiTypeCastExpression) {
        super.visitTypeCastExpression(expression)
    }

    override fun visitVariable(variable: PsiVariable) {
        super.visitVariable(variable)
    }

    override fun visitWhileStatement(statement: PsiWhileStatement) {
        super.visitWhileStatement(statement)
    }

    override fun visitJavaFile(file: PsiJavaFile) {
        // TODO: Package declarations, imports, ....
        translatedKotlinCode = file.classes.asSequence()
            .flatMap { it.initializers.asSequence() }
            .map { it.translate() }
            .joinToString(separator = "\n")
    }

    override fun visitImplicitVariable(variable: ImplicitVariable) {
        super.visitImplicitVariable(variable)
    }

    override fun visitDocToken(token: PsiDocToken) {
        super.visitDocToken(token)
    }

    override fun visitTypeParameter(classParameter: PsiTypeParameter) {
        super.visitTypeParameter(classParameter)
    }

    override fun visitAnnotation(annotation: PsiAnnotation) {
        super.visitAnnotation(annotation)
    }

    override fun visitAnnotationParameterList(list: PsiAnnotationParameterList) {
        super.visitAnnotationParameterList(list)
    }

    override fun visitAnnotationArrayInitializer(initializer: PsiArrayInitializerMemberValue) {
        super.visitAnnotationArrayInitializer(initializer)
    }

    override fun visitNameValuePair(pair: PsiNameValuePair) {
        super.visitNameValuePair(pair)
    }

    override fun visitAnnotationMethod(method: PsiAnnotationMethod) {
        super.visitAnnotationMethod(method)
    }

    override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
        super.visitEnumConstant(enumConstant)
    }

    override fun visitEnumConstantInitializer(enumConstantInitializer: PsiEnumConstantInitializer) {
        super.visitEnumConstantInitializer(enumConstantInitializer)
    }

    override fun visitCodeFragment(codeFragment: JavaCodeFragment) {
        super.visitCodeFragment(codeFragment)
    }

    override fun visitPolyadicExpression(expression: PsiPolyadicExpression) {
        super.visitPolyadicExpression(expression)
    }

    override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        super.visitLambdaExpression(expression)
    }

    override fun visitSwitchExpression(expression: PsiSwitchExpression) {
        super.visitSwitchExpression(expression)
    }

    override fun visitModule(module: PsiJavaModule) {
        super.visitModule(module)
    }

    override fun visitModuleReferenceElement(refElement: PsiJavaModuleReferenceElement) {
        super.visitModuleReferenceElement(refElement)
    }

    override fun visitModuleStatement(statement: PsiStatement) {
        super.visitModuleStatement(statement)
    }

    override fun visitRequiresStatement(statement: PsiRequiresStatement) {
        super.visitRequiresStatement(statement)
    }

    override fun visitPackageAccessibilityStatement(statement: PsiPackageAccessibilityStatement) {
        super.visitPackageAccessibilityStatement(statement)
    }

    override fun visitUsesStatement(statement: PsiUsesStatement) {
        super.visitUsesStatement(statement)
    }

    override fun visitProvidesStatement(statement: PsiProvidesStatement) {
        super.visitProvidesStatement(statement)
    }
}

