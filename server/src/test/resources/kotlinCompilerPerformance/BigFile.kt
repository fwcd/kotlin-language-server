import java.util.*
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

object BigFile {
    /**
     * In this example, `val` denotes a declaration of a read-only local variable,
     * that is assigned an pattern matching expression.
     * See http://kotlinlang.org/docs/reference/control-flow.html#when-expression
     */

    fun main7(args: Array<String>) {
        val language = if (args.size == 0) "EN" else args[0]
        println(
                when (language) {
                    "EN" -> "Hello!"
                    "FR" -> "Salut!"
                    "IT" -> "Ciao!"
                    else -> "Sorry, I can't greet you in $language yet"
                })
    }

    /**
     * `if` is an expression, i.e. it returns a value.
     * Therefore there is no ternary operator (condition ? then : else),
     * because ordinary `if` works fine in this role.
     * See http://kotlinlang.org/docs/reference/control-flow.html#if-expression
     */
    fun main8(args: Array<String>) {
        println(max(args[0].toInt(), args[1].toInt()))
    }

    fun max(a: Int, b: Int): Int = if (a > b) a else b

    // Return null if str does not hold a number
    fun parseInt(str: String): Int? {
        try {
            return str.toInt()
        } catch (e: NumberFormatException) {
            println("One of the arguments isn't Int")
        }
        return null
    }

    /**
     * The `is` operator checks if an expression is an instance of a type and more.
     * If we is-checked an immutable local variable or property, there's no need
     * to cast it explicitly to the is-checked type.
     * See this pages for details:
     * http://kotlinlang.org/docs/reference/classes.html#classes-and-inheritance
     * http://kotlinlang.org/docs/reference/typecasts.html#smart-casts
     */
    fun main9(args: Array<String>) {
        println(getStringLength("aaa"))
        println(getStringLength(1))
    }

    fun getStringLength(obj: Any): Int? {
        if (obj is String) return obj.length // no cast to String is needed
        return null
    }

    fun main10(args: Array<String>) {
        if (args.size < 2) {
            println("No number supplied")
        } else {
            val x = parseInt(args[0])
            val y = parseInt(args[1])

            // We cannot say 'x * y' now because they may hold nulls
            if (x != null && y != null) {
                print(x * y) // Now we can
            } else {
                println("One of the arguments is null")
            }
        }
    }

    /**
     * `while` and `do..while` work as usual.
     * See http://kotlinlang.org/docs/reference/control-flow.html#while-loops
     */
    fun main11(args: Array<String>) {
        var i = 0
        while (i < args.size) println(args[i++])
    }

    /**
     * For loop iterates through anything that provides an iterator.
     * See http://kotlinlang.org/docs/reference/control-flow.html#for-loops
     */
    fun main12(args: Array<String>) {
        for (arg in args) println(arg)

        // or
        println()
        for (i in args.indices) println(args[i])
    }

    /**
     * Check if a number lies within a range.
     * Check if a number is out of range.
     * Check if a collection contains an object.
     * See http://kotlinlang.org/docs/reference/ranges.html#ranges
     */

    fun main13(args: Array<String>) {
        val x = args[0].toInt()
        //Check if a number lies within a range:
        val y = 10
        if (x in 1..y - 1) println("OK")

        //Iterate over a range:
        for (a in 1..5) print("${a} ")

        //Check if a number is out of range:
        println()
        val array = arrayListOf<String>()
        array.add("aaa")
        array.add("bbb")
        array.add("ccc")

        if (x !in 0..array.size - 1) println("Out: array has only ${array.size} elements. x = ${x}")

        //Check if a collection contains an object:
        if ("aaa" in array) // collection.contains(obj) is called
            println("Yes: array contains aaa")

        if ("ddd" in array) // collection.contains(obj) is called
            println("Yes: array contains ddd")
        else println("No: array doesn't contains ddd")
    }

    /**
     * See http://kotlinlang.org/docs/reference/control-flow.html#when-expression
     */

    fun main14(args: Array<String>) {
        cases("Hello")
        cases(1)
        cases(0L)
        cases(MyClass())
        cases("hello")
    }

    fun cases(obj: Any) {
        when (obj) {
            1 -> println("One")
            "Hello" -> println("Greeting")
            is Long -> println("Long")
            !is String -> println("Not a string")
            else -> println("Unknown")
        }
    }

    class MyClass() {}

    /**
     * This example introduces a concept that we call destructuring declarations.
     * It creates multiple variable at once. Anything can be on the right-hand
     * side of a destructuring declaration, as long as the required number of component
     * functions can be called on it.
     * See http://kotlinlang.org/docs/reference/multi-declarations.html#multi-declarations
     */

    fun main15(args: Array<String>) {
        val pair = Pair(1, "one")

        val (num, name) = pair

        println("num = $num, name = $name")
    }

    class Pair<K, V>(val first: K, val second: V) {
        operator fun component1(): K {
            return first
        }

        operator fun component2(): V {
            return second
        }
    }

    /**
     *  Data class gets component functions, one for each property declared
     *  in the primary constructor, generated automatically, same for all the
     *  other goodies common for data: toString(), equals(), hashCode() and copy().
     *  See http://kotlinlang.org/docs/reference/data-classes.html#data-classes
     */

    data class User1(val name: String, val id: Int)

    fun getUser(): User1 {
        return User1("Alex", 1)
    }

    fun main16(args: Array<String>) {
        val user = getUser()
        println("name = ${user.name}, id = ${user.id}")

        // or

        val (name, id) = getUser()
        println("name = $name, id = $id")

        // or

        println("name = ${getUser().component1()}, id = ${getUser().component2()}")
    }

    /**
     *  Kotlin Standard Library provide component functions for Map.Entry
     */

    fun main17(args: Array<String>) {
        val map = hashMapOf<String, Int>()
        map.put("one", 1)
        map.put("two", 2)

        for ((key, value) in map) {
            println("key = $key, value = $value")
        }
    }

    /**
     * Data class gets next functions, generated automatically:
     * component functions, toString(), equals(), hashCode() and copy().
     * See http://kotlinlang.org/docs/reference/data-classes.html#data-classes
     */

    data class User2(val name: String, val id: Int)

    fun main18(args: Array<String>) {
        val user = User2("Alex", 1)
        println(user) // toString()

        val secondUser = User2("Alex", 1)
        val thirdUser = User2("Max", 2)

        println("user == secondUser: ${user == secondUser}")
        println("user == thirdUser: ${user == thirdUser}")

        // copy() function
        println(user.copy())
        println(user.copy("Max"))
        println(user.copy(id = 2))
        println(user.copy("Max", 2))
    }

    /**
     * There's some new syntax: you can say `val 'property name': 'Type' by 'expression'`.
     * The expression after by is the delegate, because get() and set() methods
     * corresponding to the property will be delegated to it.
     * Property delegates don't have to implement any interface, but they have
     * to provide methods named getValue() and setValue() to be called.</p>
     */

    class Example {
        var p: String by Delegate()

        override fun toString() = "Example Class"
    }

    class Delegate() {
        operator fun getValue(thisRef: Any?, prop: KProperty<*>): String {
            return "$thisRef, thank you for delegating '${prop.name}' to me!"
        }

        operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: String) {
            println("$value has been assigned to ${prop.name} in $thisRef")
        }
    }

    fun main19(args: Array<String>) {
        val e = Example()
        println(e.p)
        e.p = "NEW"
    }

    /**
     * Delegates.lazy() is a function that returns a delegate that implements a lazy property:
     * the first call to get() executes the lambda expression passed to lazy() as an argument
     * and remembers the result, subsequent calls to get() simply return the remembered result.
     * If you want thread safety, use blockingLazy() instead: it guarantees that the values will
     * be computed only in one thread, and that all threads will see the same value.
     */

    class LazySample {
        val lazy: String by lazy {
            println("computed!")
            "my lazy"
        }
    }

    fun main20(args: Array<String>) {
        val sample = LazySample()
        println("lazy = ${sample.lazy}")
        println("lazy = ${sample.lazy}")
    }

    /**
     * The observable() function takes two arguments: initial value and a handler for modifications.
     * The handler gets called every time we assign to `name`, it has three parameters:
     * a property being assigned to, the old value and the new one. If you want to be able to veto
     * the assignment, use vetoable() instead of observable().
     */

    class User3 {
        var name: String by Delegates.observable("no name") { d, old, new ->
            println("$old - $new")
        }
    }

    fun main21(args: Array<String>) {
        val user = User3()
        user.name = "Carl"
    }

    /**
     * Users frequently ask what to do when you have a non-null var, but you don't have an
     * appropriate value to assign to it in constructor (i.e. it must be assigned later)?
     * You can't have an uninitialized non-abstract property in Kotlin. You could initialize it
     * with null, but then you'd have to check every time you access it. Now you have a delegate
     * to handle this. If you read from this property before writing to it, it throws an exception,
     * after the first assignment it works as expected.
     */

    class User4 {
        var name: String by Delegates.notNull()

        fun init(name: String) {
            this.name = name
        }
    }

    fun main22(args: Array<String>) {
        val user = User4()
        // user.name -> IllegalStateException
        user.init("Carl")
        println(user.name)
    }

    /**
     * Properties stored in a map. This comes up a lot in applications like parsing JSON
     * or doing other "dynamic" stuff. Delegates take values from this map (by the string keys -
     * names of properties). Of course, you can have var's as well,
     * that will modify the map upon assignment (note that you'd need MutableMap instead of read-only Map).
     */

    class User5(val map: Map<String, Any?>) {
        val name: String by map
        val age: Int     by map
    }

    fun main23(args: Array<String>) {
        val user = User5(
                mapOf(
                        "name" to "John Doe", "age" to 25))

        println("name = ${user.name}, age = ${user.age}")
    }

    /**
     * "Callable References" or "Feature Literals", i.e. an ability to pass
     * named functions or properties as values. Users often ask
     * "I have a foo() function, how do I pass it as an argument?".
     * The answer is: "you prefix it with a `::`".
     */

    fun main1(args: Array<String>) {
        val numbers = listOf(1, 2, 3)
        println(numbers.filter(::isOdd))
    }

    fun isOdd(x: Int) = x % 2 != 0

    /**
     * The composition function return a composition of two functions passed to it:
     * compose(f, g) = f(g(*)).
     * Now, you can apply it to callable references.
     */

    fun main2(args: Array<String>) {
        val oddLength = compose(::isOdd, ::length)
        val strings = listOf("a", "ab", "abc")
        println(strings.filter(oddLength))
    }

    fun length(s: String) = s.length

    fun <A, B, C> compose(f: (B) -> C, g: (A) -> B): (A) -> C {
        return { x -> f(g(x)) }
    }

    /**
     * This example implements the famous "99 Bottles of Beer" program
     * See http://99-bottles-of-beer.net/
     *
     * The point is to print out a song with the following lyrics:
     *
     *     The "99 bottles of beer" song
     *
     *     99 bottles of beer on the wall, 99 bottles of beer.
     *     Take one down, pass it around, 98 bottles of beer on the wall.
     *
     *     98 bottles of beer on the wall, 98 bottles of beer.
     *     Take one down, pass it around, 97 bottles of beer on the wall.
     *
     *       ...
     *
     *     2 bottles of beer on the wall, 2 bottles of beer.
     *     Take one down, pass it around, 1 bottle of beer on the wall.
     *
     *     1 bottle of beer on the wall, 1 bottle of beer.
     *     Take one down, pass it around, no more bottles of beer on the wall.
     *
     *     No more bottles of beer on the wall, no more bottles of beer.
     *     Go to the store and buy some more, 99 bottles of beer on the wall.
     *
     * Additionally, you can pass the desired initial number of bottles to use (rather than 99)
     * as a command-line argument
     */

    fun main3(args: Array<String>) {
        if (args.isEmpty) {
            printBottles(99)
        } else {
            try {
                printBottles(args[0].toInt())
            } catch (e: NumberFormatException) {
                println("You have passed '${args[0]}' as a number of bottles, " + "but it is not a valid integer number")
            }
        }
    }

    fun printBottles(bottleCount: Int) {
        if (bottleCount <= 0) {
            println("No bottles - no song")
            return
        }

        println("The \"${bottlesOfBeer(bottleCount)}\" song\n")

        var bottles = bottleCount
        while (bottles > 0) {
            val bottlesOfBeer = bottlesOfBeer(bottles)
            print("$bottlesOfBeer on the wall, $bottlesOfBeer.\nTake one down, pass it around, ")
            bottles--
            println("${bottlesOfBeer(bottles)} on the wall.\n")
        }
        println(
                "No more bottles of beer on the wall, no more bottles of beer.\n" + "Go to the store and buy some more, ${bottlesOfBeer(
                        bottleCount)} on the wall.")
    }

    fun bottlesOfBeer(count: Int): String = when (count) {
                                                0 -> "no more bottles"
                                                1 -> "1 bottle"
                                                else -> "$count bottles"
                                            } + " of beer"

/*
 * An excerpt from the Standard Library
 */

    // This is an extension property, i.e. a property that is defined for the
// type Array<T>, but does not sit inside the class Array
    val <T> Array<T>.isEmpty: Boolean get() = size == 0

    /**
     * This is an example of a Type-Safe Groovy-style Builder
     *
     * Builders are good for declaratively describing data in your code.
     * In this example we show how to describe an HTML page in Kotlin.
     *
     * See this page for details:
     * http://kotlinlang.org/docs/reference/type-safe-builders.html
     */

    fun main4(args: Array<String>) {
        val result = html {
            head {
                title { +"HTML encoding with Kotlin" }
            }
            body {
                h1 { +"HTML encoding with Kotlin" }
                p { +"this format can be used as an alternative markup to HTML" }

                // an element with attributes and text content
                a(href = "http://jetbrains.com/kotlin") { +"Kotlin" }

                // mixed content
                p {
                    +"This is some"
                    b { +"mixed" }
                    +"text. For more see the"
                    a(href = "http://jetbrains.com/kotlin") { +"Kotlin" }
                    +"project"
                }
                p { +"some text" }

                // content generated from command-line arguments
                p {
                    +"Command line arguments were:"
                    ul {
                        for (arg in args) li { +arg }
                    }
                }
            }
        }
        println(result)
    }

    interface Element {
        fun render(builder: StringBuilder, indent: String)
    }

    class TextElement(val text: String) : Element {
        override fun render(builder: StringBuilder, indent: String) {
            builder.append("$indent$text\n")
        }
    }

    abstract class Tag(val name: String) : Element {
        val children = arrayListOf<Element>()
        val attributes = hashMapOf<String, String>()

        protected fun <T : Element> initTag(tag: T, init: T.() -> Unit): T {
            tag.init()
            children.add(tag)
            return tag
        }

        override fun render(builder: StringBuilder, indent: String) {
            builder.append("$indent<$name${renderAttributes()}>\n")
            for (c in children) {
                c.render(builder, indent + "  ")
            }
            builder.append("$indent</$name>\n")
        }

        private fun renderAttributes(): String? {
            val builder = StringBuilder()
            for (a in attributes.keys) {
                builder.append(" $a=\"${attributes[a]}\"")
            }
            return builder.toString()
        }

        override fun toString(): String {
            val builder = StringBuilder()
            render(builder, "")
            return builder.toString()
        }
    }

    abstract class TagWithText(name: String) : Tag(name) {
        operator fun String.unaryPlus() {
            children.add(TextElement(this))
        }
    }

    class HTML() : TagWithText("html") {
        fun head(init: Head.() -> Unit) = initTag(Head(), init)

        fun body(init: Body.() -> Unit) = initTag(Body(), init)
    }

    class Head() : TagWithText("head") {
        fun title(init: Title.() -> Unit) = initTag(Title(), init)
    }

    class Title() : TagWithText("title")

    abstract class BodyTag(name: String) : TagWithText(name) {
        fun b(init: B.() -> Unit) = initTag(B(), init)
        fun p(init: P.() -> Unit) = initTag(P(), init)
        fun h1(init: H1.() -> Unit) = initTag(H1(), init)
        fun ul(init: UL.() -> Unit) = initTag(UL(), init)
        fun a(href: String, init: A.() -> Unit) {
            val a = initTag(A(), init)
            a.href = href
        }
    }

    class Body() : BodyTag("body")
    class UL() : BodyTag("ul") {
        fun li(init: LI.() -> Unit) = initTag(LI(), init)
    }

    class B() : BodyTag("b")
    class LI() : BodyTag("li")
    class P() : BodyTag("p")
    class H1() : BodyTag("h1")

    class A() : BodyTag("a") {
        public var href: String
            get() = attributes["href"]!!
            set(value) {
                attributes["href"] = value
            }
    }

    fun html(init: HTML.() -> Unit): HTML {
        val html = HTML()
        html.init()
        return html
    }

    /**
     * This is a straightforward implementation of The Game of Life
     * See http://en.wikipedia.org/wiki/Conway's_Game_of_Life
     */

/*
 * A field where cells live. Effectively immutable
 */
    class Field(
            val width: Int, val height: Int,
            // This function tells the constructor which cells are alive
            // if init(i, j) is true, the cell (i, j) is alive
            init: (Int, Int) -> Boolean) {
        private val live: Array<Array<Boolean>> = Array(height) { i -> Array(width) { j -> init(i, j) } }

        private fun liveCount(i: Int, j: Int) = if (i in 0..height - 1 && j in 0..width - 1 && live[i][j]) 1 else 0

        // How many neighbors of (i, j) are alive?
        fun liveNeighbors(i: Int, j: Int) = liveCount(i - 1, j - 1) + liveCount(i - 1, j) + liveCount(
                i - 1,
                j + 1) + liveCount(
                i,
                j - 1) + liveCount(i, j + 1) + liveCount(i + 1, j - 1) + liveCount(i + 1, j) + liveCount(i + 1, j + 1)

        // You can say field[i, j], and this function gets called
        operator fun get(i: Int, j: Int) = live[i][j]
    }

    /**
     * This function takes the present state of the field
     * and returns a new field representing the next moment of time
     */
    fun next(field: Field): Field {
        return Field(field.width, field.height) { i, j ->
            val n = field.liveNeighbors(i, j)
            if (field[i, j])
            // (i, j) is alive
                n in 2..3 // It remains alive iff it has 2 or 3 neighbors
            else
            // (i, j) is dead
                n == 3 // A new cell is born if there are 3 neighbors alive
        }
    }

    /** A few colony examples here */
    fun main5(args: Array<String>) {
        // Simplistic demo
        runGameOfLife("***", 3)
        // "Star burst"
        runGameOfLife(
                """
        _______
        ___*___
        __***__
        ___*___
        _______
    """, 10)
        // Stable colony
        runGameOfLife(
                """
        _____
        __*__
        _*_*_
        __*__
        _____
    """, 3)
        // Stable from the step 2
        runGameOfLife(
                """
        __**__
        __**__
        __**__
    """, 3)
        // Oscillating colony
        runGameOfLife(
                """
        __**____
        __**____
        ____**__
        ____**__
    """, 6)
        // A fancier oscillating colony
        runGameOfLife(
                """
        -------------------
        -------***---***---
        -------------------
        -----*----*-*----*-
        -----*----*-*----*-
        -----*----*-*----*-
        -------***---***---
        -------------------
        -------***---***---
        -----*----*-*----*-
        -----*----*-*----*-
        -----*----*-*----*-
        -------------------
        -------***---***---
        -------------------
    """, 10)
    }

// UTILITIES

    fun runGameOfLife(fieldText: String, steps: Int) {
        var field = makeField(fieldText)
        for (step in 1..steps) {
            println("Step: $step")
            for (i in 0..field.height - 1) {
                for (j in 0..field.width - 1) {
                    print(if (field[i, j]) "*" else " ")
                }
                println("")
            }
            field = next(field)
        }
    }

    fun makeField(s: String): Field {
        val lines = s.replace(" ", "").split('\n').filter({ it.isNotEmpty() })
        val longestLine = lines.toList().maxBy { it.length } ?: ""

        return Field(longestLine.length, lines.size) { i, j -> lines[i][j] == '*' }
    }

    /**
     * Let's Walk Through a Maze.
     *
     * Imagine there is a maze whose walls are the big 'O' letters.
     * Now, I stand where a big 'I' stands and some cool prize lies
     * somewhere marked with a '$' sign. Like this:
     *
     *    OOOOOOOOOOOOOOOOO
     *    O               O
     *    O$  O           O
     *    OOOOO           O
     *    O               O
     *    O  OOOOOOOOOOOOOO
     *    O           O I O
     *    O               O
     *    OOOOOOOOOOOOOOOOO
     *
     * I want to get the prize, and this program helps me do so as soon
     * as I possibly can by finding a shortest path through the maze.
     */

    /**
     * Declare a point class.
     */
    data class Point(val i: Int, val j: Int)

    /**
     * This function looks for a path from max.start to maze.end through
     * free space (a path does not go through walls). One can move only
     * straight up, down, left or right, no diagonal moves allowed.
     */
    fun findPath(maze: Maze): List<Point>? {
        val previous = hashMapOf<Point, Point>()

        val queue = LinkedList<Point>()
        val visited = hashSetOf<Point>()

        queue.offer(maze.start)
        visited.add(maze.start)
        while (!queue.isEmpty()) {
            val cell = queue.poll()
            if (cell == maze.end) break

            for (newCell in maze.neighbors(cell.i, cell.j)) {
                if (newCell in visited) continue
                previous.put(newCell, cell)
                queue.offer(newCell)
                visited.add(cell)
            }
        }

        if (previous[maze.end] == null) return null

        val path = arrayListOf<Point>()
        var current = previous[maze.end]!!
        while (current != maze.start) {
            path.add(0, current)
            current = previous[current]!!
        }
        return path
    }

    /**
     * Find neighbors of the (i, j) cell that are not walls
     */
    fun Maze.neighbors(i: Int, j: Int): List<Point> {
        val result = arrayListOf<Point>()
        addIfFree(i - 1, j, result)
        addIfFree(i, j - 1, result)
        addIfFree(i + 1, j, result)
        addIfFree(i, j + 1, result)
        return result
    }

    fun Maze.addIfFree(i: Int, j: Int, result: MutableList<Point>) {
        if (i !in 0..height - 1) return
        if (j !in 0..width - 1) return
        if (walls[i][j]) return

        result.add(Point(i, j))
    }

    /**
     * A data class that represents a maze
     */
    class Maze(
            // Number or columns
            val width: Int,
            // Number of rows
            val height: Int,
            // true for a wall, false for free space
            val walls: Array<BooleanArray>,
            // The starting point (must not be a wall)
            val start: Point,
            // The target point (must not be a wall)
            val end: Point) {}

    /** A few maze examples here */
    fun main6(args: Array<String>) {
        walkThroughMaze("I  $")
        walkThroughMaze("I O $")
        walkThroughMaze(
                """
    O  $
    O
    O
    O
    O           I
  """)
        walkThroughMaze(
                """
    OOOOOOOOOOO
    O $       O
    OOOOOOO OOO
    O         O
    OOOOO OOOOO
    O         O
    O OOOOOOOOO
    O        OO
    OOOOOO   IO
  """)
        walkThroughMaze(
                """
    OOOOOOOOOOOOOOOOO
    O               O
    O$  O           O
    OOOOO           O
    O               O
    O  OOOOOOOOOOOOOO
    O           O I O
    O               O
    OOOOOOOOOOOOOOOOO
  """)
    }

// UTILITIES

    fun walkThroughMaze(str: String) {
        val maze = makeMaze(str)

        println("Maze:")
        val path = findPath(maze)
        for (i in 0..maze.height - 1) {
            for (j in 0..maze.width - 1) {
                val cell = Point(i, j)
                print(
                        if (maze.walls[i][j]) "O"
                        else if (cell == maze.start) "I"
                        else if (cell == maze.end) "$"
                        else if (path != null && path.contains(cell)) "~"
                        else " ")
            }
            println("")
        }
        println("Result: " + if (path == null) "No path" else "Path found")
        println("")
    }

    /**
     * A maze is encoded in the string s: the big 'O' letters are walls.
     * I stand where a big 'I' stands and the prize is marked with
     * a '$' sign.
     *
     * Example:
     *
     *    OOOOOOOOOOOOOOOOO
     *    O               O
     *    O$  O           O
     *    OOOOO           O
     *    O               O
     *    O  OOOOOOOOOOOOOO
     *    O           O I O
     *    O               O
     *    OOOOOOOOOOOOOOOOO
     */
    fun makeMaze(s: String): Maze {
        val lines = s.split('\n')
        val longestLine = lines.toList().maxBy { it.length } ?: ""
        val data = Array(lines.size) { BooleanArray(longestLine.length) }

        var start: Point? = null
        var end: Point? = null

        for (line in lines.indices) {
            for (x in lines[line].indices) {
                val c = lines[line][x]
                when (c) {
                    'O' -> data[line][x] = true
                    'I' -> start = Point(line, x)
                    '$' -> end = Point(line, x)
                }
            }
        }

        return Maze(
                longestLine.length,
                lines.size,
                data,
                start ?: throw IllegalArgumentException("No starting point in the maze (should be indicated with 'I')"),
                end
                ?: throw IllegalArgumentException("No goal point in the maze (should be indicated with a '$' sign)"))
    }
}