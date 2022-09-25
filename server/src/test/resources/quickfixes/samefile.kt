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

abstract class MyAbstract {
    val otherValToTestAbstractOverride = 1
    
    abstract val name: String

    abstract fun myFun()
}

class MyImplClass : MyAbstract() {}

class My2ndClass : MyAbstract() {
    override val name = "Nils"
}


// defect GH-366, part of the solution
interface IThing {
    fun behaviour
}

class Thing : IThing
