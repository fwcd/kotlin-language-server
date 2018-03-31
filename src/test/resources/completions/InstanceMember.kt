private fun foo() {
    val instance = SomeClass()
    instance.i
}

class SomeClass {
    fun instanceFoo() = "Foo"
}