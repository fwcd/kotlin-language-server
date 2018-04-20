private fun foo() {
    val instance = SomeClass()
    instance.i
}

private class SomeClass {
    fun instanceFoo() = "Foo"
    private fun privateInstanceFoo() = "Foo"
}