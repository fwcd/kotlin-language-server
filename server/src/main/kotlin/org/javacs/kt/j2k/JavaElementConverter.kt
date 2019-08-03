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
    /**
     * Contains the translated code. If code has multiple lines,
     * the first line is *not* indented, all subsequent lines are
     * indented by 'indentLevel' levels.
     */
    var translatedKotlinCode: String? = null
        private set
    private val indent: String = "".padStart(indentLevel * indentSize, ' ')

    // =================
    // Extension methods
    // =================

    private val String?.spacePrefixed: String
        get() = this?.let { " $it" } ?: ""

    /** Convenience method to perform construction, visit and translation in one call. */
    private fun PsiElement?.translate(indentDelta: Int = 0): String? = JavaElementConverter(indentLevel + indentDelta)
        .also { this?.accept(it) }
        .translatedKotlinCode

    /** Fetches the indented indent. */
    private fun nextIndent(indentDelta: Int = 1): String = ""
        .padStart((indentLevel + indentDelta) * indentSize, ' ')

    /**
     * Constructs a code block from a list of statements.
     * Note that the 'indentDelta' refers to the layer
     * of indentation *inside* the block.
     */
    private fun List<String>.buildCodeBlock(indentDelta: Int = 1): String {
        val indentedStatements = this
            .map { "${nextIndent(indentDelta)}$it" }
            .joinToString(separator = "\n")
        return "{\n$indentedStatements\n$indent}"
    }

    /** Converts a PsiType to its Kotlin representation. */
    private fun PsiType.translate(): String = accept(JavaTypeConverter)

    /** Converts a list of type arguments to its Kotlin representation. */
    private fun PsiCallExpression.translateTypeArguments(): String = "<${typeArgumentList.translate()}>"

    private fun j2kTODO(type: String) {
        LOG.warn("J2K can not convert $type yet")
        translatedKotlinCode = "/*$type*/"
    }

    // =================
    // Visitor methods
    // =================

    override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
        super.visitAnonymousClass(aClass)
        j2kTODO("AnonymousClass")
    }

    override fun visitArrayAccessExpression(expression: PsiArrayAccessExpression) {
        super.visitArrayAccessExpression(expression)
        j2kTODO("ArrayAccessExpression")
    }

    override fun visitArrayInitializerExpression(expression: PsiArrayInitializerExpression) {
        super.visitArrayInitializerExpression(expression)
        j2kTODO("ArrayInitializerExpression")
    }

    override fun visitAssertStatement(statement: PsiAssertStatement) {
        super.visitAssertStatement(statement)
        j2kTODO("AssertStatement")
    }

    override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
        super.visitAssignmentExpression(expression)
        j2kTODO("AssignmentExpression")
    }

    override fun visitBinaryExpression(expression: PsiBinaryExpression) {
        super.visitBinaryExpression(expression)
        j2kTODO("BinaryExpression")
    }

    override fun visitBlockStatement(statement: PsiBlockStatement) {
        super.visitBlockStatement(statement)
        j2kTODO("BlockStatement")
    }

    override fun visitBreakStatement(statement: PsiBreakStatement) {
        super.visitBreakStatement(statement)
        j2kTODO("BreakStatement")
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
                .buildCodeBlock(indentDelta = 2)
                .spacePrefixed
            "companion object$translatedCompanionBlock"
        } else ""

        val translatedBody = (listOf(translatedCompanion) + translatedInstanceMembers)
            .buildCodeBlock(indentDelta = 1)
            .spacePrefixed

        translatedKotlinCode = "class ${aClass.qualifiedName}$translatedBody"
    }

    override fun visitClassInitializer(initializer: PsiClassInitializer) {
        translatedKotlinCode = "init${initializer.body.translate().spacePrefixed}"
    }

    override fun visitClassObjectAccessExpression(expression: PsiClassObjectAccessExpression) {
        super.visitClassObjectAccessExpression(expression)
        j2kTODO("ClassObjectAccessExpression")
    }

    override fun visitCodeBlock(block: PsiCodeBlock) {
        val translated = block.statements.mapNotNull { it.translate(indentDelta = 1) }
        translatedKotlinCode = translated.buildCodeBlock()
    }

    override fun visitConditionalExpression(expression: PsiConditionalExpression) {
        super.visitConditionalExpression(expression)
        j2kTODO("ConditionalExpression")
    }

    override fun visitContinueStatement(statement: PsiContinueStatement) {
        translatedKotlinCode = "continue${statement.labelIdentifier.translate().spacePrefixed}"
    }

    override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
        super.visitDeclarationStatement(statement)
        j2kTODO("DeclarationStatement")
    }

    override fun visitDocComment(comment: PsiDocComment) {
        super.visitDocComment(comment)
        j2kTODO("DocComment")
    }

    override fun visitDocTag(tag: PsiDocTag) {
        super.visitDocTag(tag)
        j2kTODO("DocTag")
    }

    override fun visitDocTagValue(value: PsiDocTagValue) {
        super.visitDocTagValue(value)
        j2kTODO("DocTagValue")
    }

    override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
        super.visitDoWhileStatement(statement)
        j2kTODO("DoWhileStatement")
    }

    override fun visitEmptyStatement(statement: PsiEmptyStatement) {
        translatedKotlinCode = ""
    }

    override fun visitExpression(expression: PsiExpression) {
        translatedKotlinCode = expression.text // Perform no conversion if no concrete visitor could be found
    }

    override fun visitExpressionList(list: PsiExpressionList) {
        translatedKotlinCode = list.expressions.asSequence().map { it.translate() }.joinToString(separator = ", ")
    }

    override fun visitExpressionListStatement(statement: PsiExpressionListStatement) {
        visitExpressionList(statement.expressionList)
    }

    override fun visitExpressionStatement(statement: PsiExpressionStatement) {
        translatedKotlinCode = statement.expression.translate()
    }

    override fun visitField(field: PsiField) {
        visitVariable(field)
    }

    override fun visitForStatement(statement: PsiForStatement) {
        super.visitForStatement(statement)
        j2kTODO("ForStatement")
    }

    override fun visitForeachStatement(statement: PsiForeachStatement) {
        super.visitForeachStatement(statement)
        j2kTODO("ForeachStatement")
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
        j2kTODO("ImportList")
    }

    override fun visitImportStatement(statement: PsiImportStatement) {
        super.visitImportStatement(statement)
        j2kTODO("ImportStatement")
    }

    override fun visitImportStaticStatement(statement: PsiImportStaticStatement) {
        super.visitImportStaticStatement(statement)
        j2kTODO("ImportStaticStatement")
    }

    override fun visitInlineDocTag(tag: PsiInlineDocTag) {
        super.visitInlineDocTag(tag)
        j2kTODO("InlineDocTag")
    }

    override fun visitInstanceOfExpression(expression: PsiInstanceOfExpression) {
        super.visitInstanceOfExpression(expression)
        j2kTODO("InstanceOfExpression")
    }

    override fun visitJavaToken(token: PsiJavaToken) {
        super.visitJavaToken(token)
        j2kTODO("JavaToken")
    }

    override fun visitKeyword(keyword: PsiKeyword) {
        super.visitKeyword(keyword)
        j2kTODO("Keyword")
    }

    override fun visitLabeledStatement(statement: PsiLabeledStatement) {
        super.visitLabeledStatement(statement)
        j2kTODO("LabeledStatement")
    }

    override fun visitLiteralExpression(expression: PsiLiteralExpression) {
        super.visitLiteralExpression(expression)
        j2kTODO("LiteralExpression")
    }

    override fun visitLocalVariable(variable: PsiLocalVariable) {
        super.visitLocalVariable(variable)
        j2kTODO("LocalVariable")
    }

    override fun visitMethod(method: PsiMethod) {
        // TODO: Type parameters, annotations, modifiers, ...
        val translatedBody = method.body.translate().spacePrefixed
        translatedKotlinCode = "fun ${method.name}(${method.parameterList.translate()})$translatedBody"
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        translatedKotlinCode = "${expression.methodExpression.translate()}(${expression.argumentList.translate()})"
    }

    override fun visitCallExpression(callExpression: PsiCallExpression) {
        super.visitCallExpression(callExpression)
        j2kTODO("CallExpression")
    }

    override fun visitModifierList(list: PsiModifierList) {
        super.visitModifierList(list)
        j2kTODO("ModifierList")
    }

    override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        j2kTODO("NewExpression")
    }

    override fun visitPackage(aPackage: PsiPackage) {
        super.visitPackage(aPackage)
        j2kTODO("Package")
    }

    override fun visitPackageStatement(statement: PsiPackageStatement) {
        super.visitPackageStatement(statement)
        j2kTODO("PackageStatement")
    }

    override fun visitParameter(parameter: PsiParameter) {
        // TODO: Varargs, ...
        translatedKotlinCode = "${parameter.name}: ${parameter.type.translate()}"
    }

    override fun visitReceiverParameter(parameter: PsiReceiverParameter) {
        super.visitReceiverParameter(parameter)
        j2kTODO("ReceiverParameter")
    }

    override fun visitParameterList(list: PsiParameterList) {
        translatedKotlinCode = list.parameters
            .mapNotNull { it.translate() }
            .joinToString(separator = ", ")
    }

    override fun visitParenthesizedExpression(expression: PsiParenthesizedExpression) {
        super.visitParenthesizedExpression(expression)
        j2kTODO("ParenthesizedExpression")
    }

    override fun visitUnaryExpression(expression: PsiUnaryExpression) {
        super.visitUnaryExpression(expression)
        j2kTODO("UnaryExpression")
    }

    override fun visitPostfixExpression(expression: PsiPostfixExpression) {
        super.visitPostfixExpression(expression)
        j2kTODO("PostfixExpression")
    }

    override fun visitPrefixExpression(expression: PsiPrefixExpression) {
        super.visitPrefixExpression(expression)
        j2kTODO("PrefixExpression")
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
        super.visitReferenceElement(reference)
        j2kTODO("ReferenceElement")
    }

    override fun visitImportStaticReferenceElement(reference: PsiImportStaticReferenceElement) {
        super.visitImportStaticReferenceElement(reference)
        j2kTODO("ImportStaticReferenceElement")
    }

    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        translatedKotlinCode = expression.referenceNameElement.translate()
    }

    override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        super.visitMethodReferenceExpression(expression)
        j2kTODO("MethodReferenceExpression")
    }

    override fun visitReferenceList(list: PsiReferenceList) {
        super.visitReferenceList(list)
        j2kTODO("ReferenceList")
    }

    override fun visitReferenceParameterList(list: PsiReferenceParameterList) {
        super.visitReferenceParameterList(list)
        j2kTODO("ReferenceParameterList")
    }

    override fun visitTypeParameterList(list: PsiTypeParameterList) {
        super.visitTypeParameterList(list)
        j2kTODO("TypeParameterList")
    }

    override fun visitReturnStatement(statement: PsiReturnStatement) {
        super.visitReturnStatement(statement)
        j2kTODO("ReturnStatement")
    }

    override fun visitStatement(statement: PsiStatement) {
        super.visitStatement(statement)
        j2kTODO("Statement")
    }

    override fun visitSuperExpression(expression: PsiSuperExpression) {
        super.visitSuperExpression(expression)
        j2kTODO("SuperExpression")
    }

    override fun visitSwitchLabelStatement(statement: PsiSwitchLabelStatement) {
        super.visitSwitchLabelStatement(statement)
        j2kTODO("SwitchLabelStatement")
    }

    override fun visitSwitchLabeledRuleStatement(statement: PsiSwitchLabeledRuleStatement) {
        super.visitSwitchLabeledRuleStatement(statement)
        j2kTODO("SwitchLabeledRuleStatement")
    }

    override fun visitSwitchStatement(statement: PsiSwitchStatement) {
        super.visitSwitchStatement(statement)
        j2kTODO("SwitchStatement")
    }

    override fun visitSynchronizedStatement(statement: PsiSynchronizedStatement) {
        super.visitSynchronizedStatement(statement)
        j2kTODO("SynchronizedStatement")
    }

    override fun visitThisExpression(expression: PsiThisExpression) {
        super.visitThisExpression(expression)
        j2kTODO("ThisExpression")
    }

    override fun visitThrowStatement(statement: PsiThrowStatement) {
        super.visitThrowStatement(statement)
        j2kTODO("ThrowStatement")
    }

    override fun visitTryStatement(statement: PsiTryStatement) {
        super.visitTryStatement(statement)
        j2kTODO("TryStatement")
    }

    override fun visitCatchSection(section: PsiCatchSection) {
        super.visitCatchSection(section)
        j2kTODO("CatchSection")
    }

    override fun visitResourceList(resourceList: PsiResourceList) {
        super.visitResourceList(resourceList)
        j2kTODO("ResourceList")
    }

    override fun visitResourceVariable(variable: PsiResourceVariable) {
        super.visitResourceVariable(variable)
        j2kTODO("ResourceVariable")
    }

    override fun visitResourceExpression(expression: PsiResourceExpression) {
        super.visitResourceExpression(expression)
        j2kTODO("ResourceExpression")
    }

    override fun visitTypeElement(type: PsiTypeElement) {
        super.visitTypeElement(type)
        j2kTODO("TypeElement")
    }

    override fun visitTypeCastExpression(expression: PsiTypeCastExpression) {
        translatedKotlinCode = "() as $"
    }

    override fun visitVariable(variable: PsiVariable) {
        // TODO: Nullability
        val keyword = if (variable.hasModifierProperty(PsiModifier.FINAL)) "val" else "var"
        val translatedInitializer = variable.initializer.translate()?.let { " = $it" } ?: ""
        translatedKotlinCode = "$keyword ${variable.name}: ${variable.type.translate()}$translatedInitializer"
    }

    override fun visitWhileStatement(statement: PsiWhileStatement) {
        super.visitWhileStatement(statement)
        j2kTODO("WhileStatement")
    }

    override fun visitJavaFile(file: PsiJavaFile) {
        // This is the entry point for the J2K converter
        // TODO: Package declarations, imports, ....
        translatedKotlinCode = file.children.asSequence()
            .mapNotNull { it.translate() }
            .joinToString(separator = "\n")
    }

    override fun visitImplicitVariable(variable: ImplicitVariable) {
        super.visitImplicitVariable(variable)
        j2kTODO("ImplicitVariable")
    }

    override fun visitDocToken(token: PsiDocToken) {
        super.visitDocToken(token)
        j2kTODO("DocToken")
    }

    override fun visitTypeParameter(classParameter: PsiTypeParameter) {
        super.visitTypeParameter(classParameter)
        j2kTODO("TypeParameter")
    }

    override fun visitAnnotation(annotation: PsiAnnotation) {
        super.visitAnnotation(annotation)
        j2kTODO("Annotation")
    }

    override fun visitAnnotationParameterList(list: PsiAnnotationParameterList) {
        super.visitAnnotationParameterList(list)
        j2kTODO("AnnotationParameterList")
    }

    override fun visitAnnotationArrayInitializer(initializer: PsiArrayInitializerMemberValue) {
        super.visitAnnotationArrayInitializer(initializer)
        j2kTODO("AnnotationArrayInitializer")
    }

    override fun visitNameValuePair(pair: PsiNameValuePair) {
        super.visitNameValuePair(pair)
        j2kTODO("NameValuePair")
    }

    override fun visitAnnotationMethod(method: PsiAnnotationMethod) {
        super.visitAnnotationMethod(method)
        j2kTODO("AnnotationMethod")
    }

    override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
        super.visitEnumConstant(enumConstant)
        j2kTODO("EnumConstant")
    }

    override fun visitEnumConstantInitializer(enumConstantInitializer: PsiEnumConstantInitializer) {
        super.visitEnumConstantInitializer(enumConstantInitializer)
        j2kTODO("EnumConstantInitializer")
    }

    override fun visitCodeFragment(codeFragment: JavaCodeFragment) {
        super.visitCodeFragment(codeFragment)
        j2kTODO("CodeFragment")
    }

    override fun visitPolyadicExpression(expression: PsiPolyadicExpression) {
        super.visitPolyadicExpression(expression)
        j2kTODO("PolyadicExpression")
    }

    override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        super.visitLambdaExpression(expression)
        j2kTODO("LambdaExpression")
    }

    override fun visitSwitchExpression(expression: PsiSwitchExpression) {
        super.visitSwitchExpression(expression)
        j2kTODO("SwitchExpression")
    }

    override fun visitModule(module: PsiJavaModule) {
        super.visitModule(module)
        j2kTODO("Module")
    }

    override fun visitModuleReferenceElement(refElement: PsiJavaModuleReferenceElement) {
        super.visitModuleReferenceElement(refElement)
        j2kTODO("ModuleReferenceElement")
    }

    override fun visitModuleStatement(statement: PsiStatement) {
        super.visitModuleStatement(statement)
        j2kTODO("ModuleStatement")
    }

    override fun visitRequiresStatement(statement: PsiRequiresStatement) {
        super.visitRequiresStatement(statement)
        j2kTODO("RequiresStatement")
    }

    override fun visitPackageAccessibilityStatement(statement: PsiPackageAccessibilityStatement) {
        super.visitPackageAccessibilityStatement(statement)
        j2kTODO("PackageAccessibilityStatement")
    }

    override fun visitUsesStatement(statement: PsiUsesStatement) {
        super.visitUsesStatement(statement)
        j2kTODO("UsesStatement")
    }

    override fun visitProvidesStatement(statement: PsiProvidesStatement) {
        super.visitProvidesStatement(statement)
        j2kTODO("ProvidesStatement")
    }
}

