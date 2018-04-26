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

private class SomeClass {
    fun instanceFoo() = "Foo"
    private fun privateInstanceFoo() = "Foo"
    var fooVar = 1
}

private fun SomeClass.extensionFoo() = "Bar"