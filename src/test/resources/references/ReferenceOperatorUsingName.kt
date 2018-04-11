private class ReferenceOperatorUsingName {
    override operator fun equals(other: Any?): Boolean = TODO()
    operator fun compareTo(other: ReferenceOperatorUsingName): Int = TODO()
    operator fun inc(): ReferenceOperatorUsingName = TODO()
    operator fun not(): Int = TODO()
}

private fun main() {
    var example = ReferenceOperatorUsingName()

    example.equals(example)
    example.compareTo(example)
    example.inc()
    example.not()
}