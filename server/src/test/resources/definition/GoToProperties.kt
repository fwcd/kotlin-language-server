package definition

object O {
    const val objectScopeNumber: Int = 5
}

class C {
    val classScopeNumber: Int = 1
}

const val globalNumber: Int = 2

fun main() {
    val localNumber = 3
    println(O.objectScopeNumber)
    println(C().classScopeNumber)
    println(globalNumber)
    println(localNumber)
}
