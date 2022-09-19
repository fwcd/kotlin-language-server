val globalval = 23

fun somefunc(input: String) {
    val calculation = globalval + 1
    val otherval = input.length + calculation + somevalinotherfile
    
    println(otherval)
    println(globalval)
    println(somevalinotherfile)
}

// test shadowing of the global variable
fun somefunc2(globalval: String) {
    println(globalval)
    somefunc("")
}
