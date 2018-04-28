private fun test(p: Any) {
    p as MyOuterClass.I
}

private fun staticDot() {
    MyOuterClass.I
}

private class MyOuterClass {
    class InnerClass {

    }
}