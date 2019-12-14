private class ReferenceInvoke {
    operator fun invoke(): Int = TODO()
}

private fun main() {
    val example = ReferenceInvoke()
    example()
}