package inlayhints

val lambda = { n: Int, m: Double -> "$n -> $m" }

class Box<T>(t: T) {
    var value = this
}

data class Type(
    val f: Float,
    val d: Double,
)

fun destructure() {
    val type: Type
    type = Type(1.0f, 2.0)

    val (x, y) = type
}

val box = Box(0)

fun <T> toStr(b: Box<T>) = b.value.toString()
