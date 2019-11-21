private class FunctionScope {
    fun foo(anArgument: Int) {
        val aLocal = 1
        a
    }

    private val aClassVal = 1
    private fun aClassFun() = 1

    companion object {
        private val aCompanionVal = 1
        private fun aCompanionFun() = 1
    }
}