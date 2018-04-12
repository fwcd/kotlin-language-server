private class ReferenceComponents {
    operator fun component1(): Int = TODO()
    operator fun component2(): Int = TODO()
}

private fun main() {
    val (a, b) = ReferenceComponents()
    val c = ReferenceComponents().component1()
}