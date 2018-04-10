object ReferenceTo {
    fun foo() {

    }

    operator fun plus(value: Int) {

    }

    fun main() {
        ReferenceTo.foo()
        ReferenceTo + 1
    }
}