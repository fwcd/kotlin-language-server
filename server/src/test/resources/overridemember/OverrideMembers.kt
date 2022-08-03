interface Printable {
    val text: String

    fun print() {
        println("not implemented yet yo")
    }
}

class MyPrintable: Printable {}

class OtherPrintable: Printable {
    override val text: String = "you had me at lasagna"
}

class CompletePrintable: Printable {
    override val text: String = "something something something darkside"

    override fun equals(other: Any?): Boolean { return true }

    override fun hashCode(): Int { return 1 }

    override fun toString(): String {
        return "something something complete"
    }

    override fun print() {
        println("not implemented yet yo")
    }
}

open class MyOpen {
    open fun numOpenDoorsWithName(input: String): Int {
        return 2
    }
}

class Closed: MyOpen() {}

class MyThread: Thread {}
