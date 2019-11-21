private class Types {
    fun returnsType(): S

    class SomeInnerClass()
    object SomeInnerObject {
    }
}

private typealias SomeAlias = Types.SomeInnerClass