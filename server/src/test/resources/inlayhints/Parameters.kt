package inlayhints

data class Vec(
    val x: Double,
    val y: Double,
    val z: Int
)

fun print(d: Double, vararg ints: Int, cond: Boolean) {}

val calc = { v: Vec  -> v.x + v.y * v.z }

val vec = Vec(
    2.0,
    2.2,
    1,
)
val t = print(calc(vec), 1,2,3, cond = true)
val m = listOf(0,0).map { num -> num.toDouble() }