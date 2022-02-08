sealed class SealedClass {
    class Test: SealedClass() {}
}

fun sealedWhenFunc() {
    val value: SealedClass = SealedClass.Test()

    when (value) {
        is SealedClass.
    }
}
