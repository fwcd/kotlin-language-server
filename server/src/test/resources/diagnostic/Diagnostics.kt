@Deprecated("")
private class Foo {}

private class Diagnostics {
    fun foo() {
        Foo()
        return 1
    }
}
