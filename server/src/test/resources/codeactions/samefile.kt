package test.kotlin.lsp

interface MyInterface {
    fun test(input: String, otherInput: Int)
}

class MyClass : MyInterface {
}


abstract class CanPrint {
    abstract fun print()
}

class PrintableClass : CanPrint(), MyInterface {}
