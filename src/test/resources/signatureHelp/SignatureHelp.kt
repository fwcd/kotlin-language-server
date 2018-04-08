private object SignatureHelp {
    fun main(param: String) {
        Target.foo()
        Constructor()
        Target.multiParam("1", )
        Target.oneOrTwoArgs("1", )
    }
}

private object Target {
    /**
     * Call foo with a String
     * @param bar String param
     */
    fun foo(bar: String) {
    }

    /**
     * Call foo with an Int
     * @param bar Int param
     */
    fun foo(bar: Int) {
    }

    fun multiParam(first: Int, second: Int) {
    }

    fun multiParam(first: String, second: String) {
    }

    fun oneOrTwoArgs(first: String) {
    }

    fun oneOrTwoArgs(first: String, second: String) {
    }
}

/**
 * Construct with a String
 * @param bar String param
 */
private class Constructor(bar: String) {
    /**
     * Construct with an Int
     * @param bar Int param
     */
    constructor(bar: Int): this(bar.toString())
}