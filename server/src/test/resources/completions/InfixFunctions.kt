class Q {
    fun cmpA(): Double = 1.0
    infix fun cmpB(x: Int): Int { return 1 }
}

infix fun Int.funcA(x: Int): Boolean = x == this
infix fun Int.andTo(v: Int) = v

private fun memberFunc() {
    Q() cm
}

private fun stdlibFunc() {
    val v = 1
    v and
}

private fun extensionFunc() {
    2 fu 3
}

enum class FOO { T, U }
infix fun FOO.ord(n: Int) = this.ordinal == n

private fun globalEnumFunc() {
    FOO.U
}

private fun globalBinaryExpFunc() {
    4  4
}

