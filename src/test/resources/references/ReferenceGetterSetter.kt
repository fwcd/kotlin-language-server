private class ReferenceGetterSetter {
    operator fun get(index: Int): Int = TODO()
    operator fun set(index: Int, value: Int) { }
}

private fun main() {
    val example = ReferenceGetterSetter()
    example[1]
    example[1] = 2
}