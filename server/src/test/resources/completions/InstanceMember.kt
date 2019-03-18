private fun foo() {
    val instance = SomeClass()
    instance.f
}

private fun findListExtensionFunctions() {
    val list = listOf(1, 2, 3)
    list.coun
}

private fun findFunctionReference() {
    val instance = SomeClass()
    instance::f
}

private fun findUnqualifiedFunctionReference() {
    ::f
}

private fun completeIdentifierInsideCall() {
    val instance = SomeClass()
    instance.instanceFoo(f)
}

private fun findCompletionsForLettersInFullMethod() {
    SomeClass().instanceFoo()
}

private class SomeClass {
    fun instanceFoo() = "Foo"
    fun instanceFee() = "Fee"
    private fun privateInstanceFoo() = "Foo"
    var fooVar = 1
}

private fun SomeClass.extensionFoo() = "Bar"
