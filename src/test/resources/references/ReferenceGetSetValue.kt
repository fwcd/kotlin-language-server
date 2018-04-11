import kotlin.reflect.KProperty

private class ReferenceGetSetValue {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = TODO()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int): Unit = TODO()
}

private class Main {
    var x: Int by ReferenceGetSetValue()

    private fun main() {
        x = 1
    }
}