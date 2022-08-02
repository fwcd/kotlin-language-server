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

    override fun print() {
        println("not implemented yet yo")
    }
}
