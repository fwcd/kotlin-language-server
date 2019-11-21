package j2k

import java.util.Arrays

class JavaJSONConverter<T> {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            System.out.println("JSON: " + JavaJSONConverter<Int>().toJSONArray(Arrays.asList(98, 23, 34)))
        }
    }

    fun toJSONArray(list: MutableList<out T>): String {
        var str: StringBuilder = StringBuilder("[")
        var size: Int = list.size()
        var i: Int = 0
        while (i < size) {
            str.append(list.get(i))
            if (i != (size - 1)) {
                str.append(", ")
            }
            i++
        }
        return str.append("]").toString()
    }
}
