private open class Visibility : VisibilitySuper {
    fun test() {
        p
    }

    private fun privateThisFun() { }
    protected fun protectedThisFun() { }
    fun publicThisFun() { }
    
    companion object {
        private fun privateThisCompanionFun() { }
        protected fun protectedThisCompanionFun() { }
        fun publicThisCompanionFun() { }
    }
}

private open class VisibilitySuper {
    private fun privateSuperFun() { }
    protected fun protectedSuperFun() { }
    fun publicSuperFun() { }

    companion object {
        private fun privateSuperCompanionFun() { }
        protected fun protectedSuperCompanionFun() { }
        fun publicSuperCompanionFun() { }
    }
}

private fun privateTopLevelFun() { }

fun String.publicExtensionFun() { }
