private class ReferenceOperator {
    override operator fun equals(other: Any?): Boolean = TODO()
    operator fun compareTo(other: ReferenceOperator): Int = TODO()
    operator fun inc(): ReferenceOperator = TODO()
    operator fun dec(): ReferenceOperator = TODO()
    operator fun plus(value: Int): Int = TODO()
    operator fun minus(value: Int): Int = TODO()
    operator fun not(): Int = TODO()
}

private class ReferenceEquals {
    override fun equals(other: Any?): Boolean = TODO()
}

private fun main() {
    var example = ReferenceOperator()

    assert(example == example)
    example > example
    example++
    example--
    example + 1
    example - 1
    !example

    val example2 = ReferenceEquals()

    assert(example2 == example2)
}