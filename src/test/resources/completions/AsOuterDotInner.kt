private fun test(p: Any) {
    p as OuterClass.I
}

private class OuterClass {
    class InnerClass {

    }
}