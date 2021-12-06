package quickfixes

class SomeSubclass : SomeSuperClass(), SomeInterface {
}

class SomeOtherSubclass : SomeSuperClass(), SomeInterface {
    override fun someSuperMethod(someParameter: String): Int { return 1 }
}

class YetAnotherSubclass : SomeSuperClass(), SomeInterface {
    override fun someInterfaceMethod() { }
}
