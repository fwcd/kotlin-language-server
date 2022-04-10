package test.my.companion

val SOME_GLOBAL_CONSTANT = 42

fun multiplyByOne(num: Int) = num*1

class SweetPotato {
    companion object {
        @JvmStatic
        fun main() {
            println("42 multiplied by 1: ${multiplyByOne(42)}")
        }
    }
}
