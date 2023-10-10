package inlayhints


class A<T>(private val self: List<T>) {
    fun a(): B<T> {
        return B(self)
    }
}

class B<T>(private val self: List<T>) {
    fun b(): List<T> {
        return self
    }
}

val foo = listOf(1, 2, 3, 4)

val bar = listOf("hello", "world")
    .map { it.length * 2 }
    .toTypedArray()
    .contains(2)

val baz = A(foo)
    .a()
    .b()
