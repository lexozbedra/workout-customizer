package app.sports.core

/**
 * Небольшой JSON без зависимостей от Android: так формат плана и файлы данных проверяются тестами на JVM.
 * Объекты это LinkedHashMap, массивы это List, числа это Long или Double.
 */
object Json {
    class ParseException(msg: String) : Exception(msg)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i < text.length) throw ParseException("Лишний текст после конца JSON (позиция ${p.i})")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun fail(msg: String): Nothing = throw ParseException("$msg (позиция $i)")

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t' || s[i] == '﻿')) i++
        }

        fun value(): Any? {
            if (i >= s.length) fail("Неожиданный конец файла")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("Неожиданный символ '$c'")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("Ожидалось $word")
            i += word.length
            return v
        }

        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: fail("Неверное число '$t'")
        }

        fun str(): String {
            i++ // "
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("Строка не закрыта")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) fail("Строка не закрыта")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) fail("Неверная последовательность \\u")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("Неверная последовательность \\u"))
                                i += 4
                            }
                            else -> fail("Неверный экранированный символ '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws()
                out += value()
                ws()
                if (i >= s.length) fail("Массив не закрыт")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return out }
                fail("Ожидалась запятая или ]")
            }
        }

        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("Ожидалось имя поля в кавычках")
                val k = str()
                ws()
                if (i >= s.length || s[i] != ':') fail("Ожидалось двоеточие после \"$k\"")
                i++
                ws()
                out[k] = value()
                ws()
                if (i >= s.length) fail("Объект не закрыт")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return out }
                fail("Ожидалась запятая или }")
            }
        }
    }

    fun stringify(v: Any?, pretty: Boolean = true): String {
        val sb = StringBuilder()
        write(sb, v, if (pretty) 0 else -1)
        return sb.toString()
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    private fun scalar(v: Any?) = v == null || v is String || v is Number || v is Boolean

    private fun write(sb: StringBuilder, v: Any?, level: Int) {
        val pretty = level >= 0
        fun nl(l: Int) {
            if (pretty) { sb.append('\n'); repeat(l) { sb.append("  ") } }
        }
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v.toString())
            is Int, is Long -> sb.append(v.toString())
            is Number -> sb.append(if (v.toDouble() % 1.0 == 0.0 && Math.abs(v.toDouble()) < 1e15) v.toLong().toString() else v.toString())
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    nl(level + 1)
                    quote(sb, k.toString())
                    sb.append(if (pretty) ": " else ":")
                    write(sb, x, if (pretty) level + 1 else -1)
                }
                nl(level)
                sb.append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                if (v.all { scalar(it) }) { // короткие списки значений в одну строку
                    sb.append('[')
                    v.forEachIndexed { i, x -> if (i > 0) sb.append(if (pretty) ", " else ","); write(sb, x, -1) }
                    sb.append(']')
                    return
                }
                sb.append('[')
                v.forEachIndexed { i, x ->
                    if (i > 0) sb.append(',')
                    nl(level + 1)
                    write(sb, x, if (pretty) level + 1 else -1)
                }
                nl(level)
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }
}

/* Удобные чтения из разобранного JSON. */
@Suppress("UNCHECKED_CAST")
fun Any?.asObj(): Map<String, Any?>? = this as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Any?.asList(): List<Any?>? = this as? List<Any?>

fun Map<String, Any?>.str(k: String, def: String = ""): String = (this[k] as? String) ?: def
fun Map<String, Any?>.int(k: String, def: Int = 0): Int = (this[k] as? Number)?.toInt() ?: def
fun Map<String, Any?>.dbl(k: String, def: Double = 0.0): Double = (this[k] as? Number)?.toDouble() ?: def
fun Map<String, Any?>.bool(k: String, def: Boolean = false): Boolean = (this[k] as? Boolean) ?: def
