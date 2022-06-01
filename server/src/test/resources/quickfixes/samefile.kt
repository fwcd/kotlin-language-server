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

class OtherPrintableClass : CanPrint(), MyInterface {
    override fun test(input: String, otherInput: Int) {}
}

interface NullMethodAndReturn<T> {
    fun myMethod(myStr: T?): T?
}

class NullClass : NullMethodAndReturn<String> {}
