object ReferenceTo {
    fun foo() {

    }

    private class MainConstructor(foo: Int)

    fun main() {
        ReferenceTo.foo()
        MainConstructor(1)
        // TODO getValue, setValue, contains, invoke
    }
}