private class ReferenceConstructor(mainConstructor: String) {
    constructor(secondaryConstructor: String, partTwo: String): this(secondaryConstructor + partTwo) {
    }
}

private fun main() {
    ReferenceConstructor("foo")
    ReferenceConstructor("foo", "bar")
}
