private fun foo(): List<String> {
    return listOf("Foo", "Bar").filter(::isFoo)
}

private fun isFoo(s: String) =
        s == "Foo"