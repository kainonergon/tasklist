package tasklist

import kotlinx.datetime.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

object TaskList {

    private const val fileName = "tasklist.json"
    private var isOn = true
    private val tasks = mutableListOf<Task>()

    class Task {

        private var date: String = "1970-01-01"
        private var time: String = "00:00"
        private var priority: TaskPriority = TaskPriority.N
        private var description: List<String> = listOf("Empty task")
        private val dueTag: DueTag
            get() = DueTag.getByDate(date)

        companion object {
            const val lineWidth = 44
            private const val horLine =
                "+----+------------+-------+---+---+--------------------------------------------+"
            private const val headerText =
                "| N  |    Date    | Time  | P | D |                   Task                     |"
            const val emptyCells =
                "|    |            |       |   |   |"
            const val header = "$horLine\n$headerText\n$horLine"

            fun read(): Task {
                val task = Task()
                task.readPriority()
                task.readDate()
                task.readTime()
                task.readDescription()
                return task
            }
        }

        enum class TaskPriority (val colorCode: ColorCode) {
            C(ColorCode.RED), // Critical
            H(ColorCode.YELLOW), // High
            N(ColorCode.GREEN), // Normal
            L(ColorCode.BLUE); // Low
            companion object {
                private val prompt = "Input the task priority " +
                        values().joinToString(", ", "(", "):")
                fun read(): TaskPriority {
                    while (true) {
                        println(prompt)
                        try {
                            return valueOf(readln().trim().uppercase())
                        } catch (_: IllegalArgumentException) {}
                    }
                }
            }
        }

        enum class DueTag (val colorCode: ColorCode) {
            I(ColorCode.GREEN), // In time
            T(ColorCode.YELLOW), // Today
            O(ColorCode.RED); // Overdue
            companion object {
                fun getByDate(date: String): DueTag {
                    val currentDate = Clock.System.now().toLocalDateTime(TimeZone.of("UTC+0")).date
                    val numberOfDays = currentDate.daysUntil(date.toLocalDate())
                    return when {
                        numberOfDays > 0 -> I
                        numberOfDays == 0 -> T
                        else -> O
                    }
                }
            }
        }

        enum class ColorCode (private val code: String) {
            RED("\u001B[101m \u001B[0m"), // light red
            GREEN("\u001B[102m \u001B[0m"), // light green
            YELLOW("\u001B[103m \u001B[0m"), // yellow
            BLUE("\u001B[104m \u001B[0m"); // light blue
            override fun toString(): String = code
        }

        fun readDate() {
            while (true) {
                println("Input the date (yyyy-mm-dd):")
                val input = readln().trim()
                try {
                    require(input.matches("""\d{4}-\d{1,2}-\d{1,2}""".toRegex()))
                    val (yyyy, mm, dd) = input.split("-").map(String::toInt)
                    date = LocalDate(yyyy, mm, dd).toString()
                    break
                } catch (_: IllegalArgumentException) {
                    println("The input date is invalid")
                }
            }
        }

        fun readTime() {
            while (true) {
                println("Input the time (hh:mm):")
                val input = readln().trim()
                try {
                    require(input.matches("""\d{1,2}:\d{1,2}""".toRegex()))
                    val (hh, mm) = input.split(":").map(String::toInt)
                    require(hh in 0 until 24 && mm in 0 until 60)
                    time = String.format("%02d:%02d", hh, mm)
                    break
                } catch (_: IllegalArgumentException) {
                    println("The input time is invalid")
                }
            }
        }

        fun readPriority() { priority = TaskPriority.read() }

        fun readDescription() {
            println("Input a new task (enter a blank line to end):")
            description = buildList {
                while (true) {
                    val input = readln().trim()
                    if (input.isEmpty()) break
                    add(input)
                }
                check(this.isNotEmpty()) { "The task is blank" }
            }
        }

        enum class Field(val action: Task.() -> Unit) {
            PRIORITY(Task::readPriority),
            DATE(Task::readDate),
            TIME(Task::readTime),
            TASK(Task::readDescription);
            companion object {
                private val prompt = "Input a field to edit " +
                        values().joinToString(", ", "(", "):").lowercase()
                fun read(): Field {
                    while (true) {
                        println(prompt)
                        try {
                            return valueOf(readln().trim().uppercase())
                        } catch (_: IllegalArgumentException) {
                            println("Invalid field")
                        }
                    }
                }
            }
        }

        fun edit() {
            (Field.read().action)()
        }

        fun toString(n: Int): String = buildString {
            val chunkedDescription =
                description.map { line -> line.chunked(lineWidth) { it.padEnd(lineWidth)} }.flatten()
            append("| ${n.toString().padEnd(3)}")
            append("| $date ")
            append("| $time ")
            append("| ${priority.colorCode} ")
            append("| ${dueTag.colorCode} ")
            appendLine("|${chunkedDescription.first()}|")
            for (index in 1..chunkedDescription.lastIndex) {
                appendLine("$emptyCells${chunkedDescription[index]}|")
            }
            appendLine(horLine)
        }
    }

    override fun toString(): String = buildString {
        check(tasks.isNotEmpty()) { "No tasks have been input" }
        appendLine(Task.header)
        tasks.forEachIndexed { index, task -> append(task.toString(index + 1)) }
        appendLine()
    }

    private fun readTaskIndex(): Int {
        print(TaskList)
        while (true) {
            println("Input the task number (1-${tasks.size}):")
            try {
                val index = readln().trim().toInt() - 1
                require(index in tasks.indices)
                return index
            } catch (_: IllegalArgumentException) {
                println("Invalid task number")
            }
        }
    }

    private fun editTask() {
        tasks[readTaskIndex()].edit()
        println("The task is changed")
    }

    private fun deleteTask() {
        tasks.removeAt(readTaskIndex())
        println("The task is deleted")
    }

    enum class Menu(val action: () -> Unit) {
        ADD({ tasks.add(Task.read()) }),
        PRINT({ print(TaskList) }),
        EDIT(::editTask),
        DELETE(::deleteTask),
        END({ isOn = false ; println("Tasklist exiting!") });
        companion object {
            private val prompt = "Input an action " +
                    values().joinToString(", ", "(", "):").lowercase()
            fun read(): Menu {
                while (true) {
                    println(prompt)
                    try {
                        return Menu.valueOf(readln().trim().uppercase())
                    } catch (_: IllegalArgumentException) {
                        println("The input action is invalid")
                    }
                }
            }
        }
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val type = Types.newParameterizedType(
        MutableList::class.java,
        Task::class.java,
        Task.TaskPriority::class.java
    )
    private val tasksAdapter = moshi.adapter<MutableList<Task>>(type)
    private val jsonFile = File(fileName)
    private fun loadTasks() {
        if (jsonFile.isFile) {
            tasksAdapter.fromJson(jsonFile.readText())?.let(tasks::addAll)
        }
    }
    private fun saveTasks() {
        jsonFile.writeText(tasksAdapter.toJson(tasks))
    }

    init {
        loadTasks()
        while (isOn) {
            try {
                Menu.read().action()
            } catch (e: RuntimeException) {
                println(e.message)
            }
        }
        saveTasks()
    }
}

fun main() { TaskList }
