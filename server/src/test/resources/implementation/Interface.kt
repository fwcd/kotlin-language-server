interface Animal {
    fun speak(): String
    val name: String
}

abstract class Pet : Animal {
    abstract fun play(): String
}

class Dog : Pet() {
    override fun speak(): String = "Woof"
    override val name: String = "Dog"
    override fun play(): String = "Fetch"
}

class Cat : Animal {
    override fun speak(): String = "Meow"
    override val name: String = "Cat"
}

fun main() {
    val animal: Animal = Dog()
    animal.speak()
}
