private fun foo() {
    val instance = SomeClass()
    instance.f
}

private fun findListExtensionFunctions() {
    val list = listOf(1, 2, 3)
    list.coun
}

private class SomeClass {
    fun instanceFoo() = "Foo"
    private fun privateInstanceFoo() = "Foo"
}

private fun SomeClass.extensionFoo() = "Bar"