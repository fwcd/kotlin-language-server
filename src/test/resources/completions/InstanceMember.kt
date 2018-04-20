private fun foo() {
    val instance = SomeClass()
    instance.f
}

private class SomeClass {
    fun instanceFoo() = "Foo"
    private fun privateInstanceFoo() = "Foo"
}

private fun SomeClass.extensionFoo() = "Bar"