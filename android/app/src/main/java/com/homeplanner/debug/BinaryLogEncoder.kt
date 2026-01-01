package com.homeplanner.debug

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Энкодер для компактного бинарного формата логов.
 *
 * Реализует структуру записи согласно разделу
 * «Компактный бинарный формат» в `LOGGING_FORMAT.md`:
 *
 * 1. messageCode: 2 байта (unsigned short, little-endian)
 * 2. timestamp: 3 байта (unsigned 24-bit integer, little-endian),
 *    количество 10‑мс интервалов с начала дня
 * 3. Данные контекста: последовательность значений без ключей.
 */
object BinaryLogEncoder {

    /** Максимальное количество интервалов 10 мс в сутках. */
    private const val MAX_INTERVALS_PER_DAY: Long = 8_640_000L

    /**
     * Закодировать одну запись лога в бинарный формат.
     *
     * @param entry Запись лога.
     * @param dayStartMillis Время начала суток (00:00:00.000) в миллисекундах,
     *                       относительно которого считается относительный timestamp.
     */
    fun encodeEntry(entry: BinaryLogEntry, dayStartMillis: Long): ByteArray {
        val output = ByteArrayOutputStream()

        // 1. messageCode (2 байта, unsigned short, little-endian)
        writeUnsignedShortLE(output, entry.messageCode.toInt())

        // 2. timestamp (3 байта, unsigned 24-bit, little-endian)
        val relativeMillis: Long = (entry.timestamp - dayStartMillis).coerceAtLeast(0L)
        val intervals: Long = (relativeMillis / 10L)
            .coerceAtMost(MAX_INTERVALS_PER_DAY - 1)
            .coerceAtMost(0xFFFFFFL)
        writeUnsigned24BitLE(output, intervals.toInt())

        // 3. Данные контекста (значения без ключей, в порядке схемы)
        // ВАЖНО: порядок записи должен совпадать с порядком схемы на сервере!
        // Записываем все значения контекста как есть
        for (value in entry.context) {
            writeContextValue(output, value)
        }

        return output.toByteArray()
    }

    /**
     * Записать одно значение контекста согласно типу.
     *
     * Поддерживаемые типы:
     * - Int: 4 байта (little-endian)
     * - Long: 8 байт (little-endian)
     * - Float: 4 байта (IEEE 754, little-endian)
     * - Double: 8 байт (IEEE 754, little-endian)
     * - Boolean: 1 байт (0 = false, 1 = true)
     * - String: 1 байт длины (0–255) + UTF‑8 байты
     *
     * Прочие типы приводятся к строке через `toString()`.
     */
    private fun writeContextValue(output: ByteArrayOutputStream, value: Any?) {
        when (value) {
            is Int -> writeIntLE(output, value)
            is Long -> writeLongLE(output, value)
            is Float -> writeIntLE(output, java.lang.Float.floatToIntBits(value))
            is Double -> writeLongLE(output, java.lang.Double.doubleToLongBits(value))
            is Boolean -> output.write(if (value) 1 else 0)
            is String -> writeShortString(output, value)
            null -> {
                // Нулевые значения кодируем как строку "null" для простоты.
                writeShortString(output, "null")
            }

            else -> {
                // Для сложных объектов сохраняем строковое представление.
                writeShortString(output, value.toString())
            }
        }
    }

    /** Записать 2‑байтовое беззнаковое число в little-endian. */
    private fun writeUnsignedShortLE(output: ByteArrayOutputStream, value: Int) {
        val v = value and 0xFFFF
        output.write(v and 0xFF)
        output.write((v ushr 8) and 0xFF)
    }

    /** Записать 24‑битное беззнаковое число в little-endian. */
    private fun writeUnsigned24BitLE(output: ByteArrayOutputStream, value: Int) {
        val v = value and 0xFFFFFF
        output.write(v and 0xFF)
        output.write((v ushr 8) and 0xFF)
        output.write((v ushr 16) and 0xFF)
    }

    /** Записать Int в little-endian. */
    private fun writeIntLE(output: ByteArrayOutputStream, value: Int) {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(value)
        output.write(buffer.array())
    }

    /** Записать Long в little-endian. */
    private fun writeLongLE(output: ByteArrayOutputStream, value: Long) {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(value)
        output.write(buffer.array())
    }

    /**
     * Записать короткую строку:
     * 1 байт длины (0–255) + UTF‑8 байты (усечённые до 255 байт).
     */
    private fun writeShortString(output: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val length = bytes.size.coerceAtMost(255)
        output.write(length)
        output.write(bytes, 0, length)
    }
}



