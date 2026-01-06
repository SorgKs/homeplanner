package com.homeplanner.debug

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

/**
 * Хранилище бинарных логов по дням (чанкам).
 *
 * Реализует концепцию «чанк = 1 день» из `LOGGING_FORMAT.md`:
 * - для каждого дня создаётся отдельный бинарный файл;
 * - в заголовке файла (версия 1.1) фиксируются: дата, ревизия словаря, версия формата, deviceId и chunkId;
 * - каждая запись кодируется через `BinaryLogEncoder` с относительным временем от начала дня.
 */
class BinaryLogStorage(
    private val context: Context,
    private val dictionaryRevision: DictionaryRevision = DictionaryRevision.CURRENT,
) {

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val logsDir: File = File(context.filesDir, "debug_logs_bin")

    private var currentDate: LocalDate? = null
    private var currentStream: FileOutputStream? = null
    private var currentChunkId: Long = 0L

    // Статистика по чанкам для отображения в настройках:
    // - общее количество файлов чанков (завершённых и текущего);
    // - суммарный размер всех файлов чанков в байтах.
    @Volatile
    private var totalChunksCount: Int = 0

    @Volatile
    private var totalChunksSizeBytes: Long = 0L
    
    // Счётчик для генерации уникальных chunkId
    private val chunkIdGenerator: AtomicLong by lazy {
        val prefs = context.getSharedPreferences("binary_log_prefs", Context.MODE_PRIVATE)
        val lastChunkId = prefs.getLong("last_chunk_id", 0L)
        AtomicLong(lastChunkId)
    }

    init {
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        // Инициализируем статистику при создании хранилища.
        recalculateStats()
    }

    /**
     * Добавить запись лога в соответствующий дневной чанк.
     *
     * Потокобезопасен на уровне экземпляра.
     */
    @Synchronized
    fun append(entry: BinaryLogEntry) {
        // Используем текущую дату для упрощения
        val entryDate = LocalDate.now(zoneId)

        if (currentDate != entryDate || currentStream == null) {
            openChunkForDate(entryDate)
        }

        val dayStartMillis = entryDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val encoded = BinaryLogEncoder.encodeEntry(entry, dayStartMillis)

        try {
            val stream = currentStream
            val fileBeforeSize = getCurrentFileLength()
            stream?.write(encoded)
            stream?.flush()
            // Обновляем статистику размера только для текущего файла.
            val fileAfterSize = getCurrentFileLength()
            val delta = fileAfterSize - fileBeforeSize
            if (delta > 0) {
                totalChunksSizeBytes += delta
            }
        } catch (_: Exception) {
            // Искомое поведение: не логируем ошибки, чтобы не зациклиться.
        }
    }

    /**
     * Закрыть текущий чанк и освободить ресурсы.
     */
    @Synchronized
    fun close() {
        try {
            currentStream?.flush()
            currentStream?.close()
        } catch (_: Exception) {
            // Ошибки при закрытии игнорируем.
        } finally {
            currentStream = null
            currentDate = null
            currentChunkId = 0L
            // При закрытии активного файла пересчитываем статистику целиком.
            recalculateStats()
        }
    }
    
    /**
     * Получить ID текущего чанка для отправки на сервер.
     */
    @Synchronized
    fun getCurrentChunkId(): Long? {
        return if (currentDate != null) currentChunkId else null
    }
    
    /**
     * Проверить, есть ли записи в текущем активном чанке.
     * Возвращает true, если текущий чанк существует и содержит данные (размер больше заголовка).
     */
    @Synchronized
    fun hasEntriesInCurrentChunk(): Boolean {
        val fileLength = getCurrentFileLength()
        // Заголовок занимает минимум ~20 байт, если есть записи - размер будет больше
        return fileLength > 20
    }
    
    /**
     * Закрыть текущий чанк для отправки.
     * После закрытия чанк станет доступен в getCompletedChunks().
     * 
     * @return ID закрытого чанка или null, если чанк не был открыт.
     */
    @Synchronized
    fun closeCurrentChunk(): Long? {
        val chunkId = currentChunkId
        if (currentDate != null && currentStream != null) {
            close()
            return chunkId
        }
        return null
    }
    
    /**
     * Получить список завершённых чанков (файлов логов).
     * Возвращает список пар (файл, chunkId).
     */
    @Synchronized
    fun getCompletedChunks(): List<Pair<File, Long>> {
        val chunks = mutableListOf<Pair<File, Long>>()

        logsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("debug_logs_") && file.name.endsWith(".bin")) {
                // Извлекаем chunkId из имени файла (debug_logs_YYYY-MM-DD_chunkId.bin)
                val parts = file.nameWithoutExtension.split("_")
                if (parts.size >= 4) {
                    val chunkIdStr = parts.drop(3).joinToString("_")
                    val chunkId = chunkIdStr.toLongOrNull()
                    if (chunkId != null) {
                        chunks.add(file to chunkId)
                    }
                }
            }
        }

        // Обновляем статистику: количество и суммарный размер файлов.
        totalChunksCount = chunks.size
        totalChunksSizeBytes = chunks.sumOf { it.first.length().coerceAtLeast(0L) }

        return chunks.sortedBy { it.first.lastModified() }
    }

    /**
     * Получить статистику по чанкам для отображения в UI.
     *
     * @return пара (количество чанков, суммарный размер в байтах).
     */
    @Synchronized
    fun getChunksStats(): Pair<Int, Long> {
        // Ленивая инициализация: если по каким-то причинам статистика не была
        // рассчитана (например, после удаления файлов извне), пересчитываем её.
        if (totalChunksCount == 0 && totalChunksSizeBytes == 0L) {
            recalculateStats()
        }
        return totalChunksCount to totalChunksSizeBytes
    }

    /**
     * Полный пересчёт статистики по всем файловым чанкам.
     */
    @Synchronized
    private fun recalculateStats() {
        val files = logsDir.listFiles()?.filter { file ->
            file.name.startsWith("debug_logs_") && file.name.endsWith(".bin")
        } ?: emptyList()

        totalChunksCount = files.size
        totalChunksSizeBytes = files.sumOf { it.length().coerceAtLeast(0L) }
    }

    /**
     * Получить текущий размер активного файла чанка (если есть).
     */
    @Synchronized
    private fun getCurrentFileLength(): Long {
        val date = currentDate ?: return 0L
        if (!logsDir.exists()) {
            return 0L
        }
        val prefix = "debug_logs_${date}_"
        val file = logsDir.listFiles()?.firstOrNull { f ->
            f.name.startsWith(prefix) && f.name.endsWith(".bin")
        }
        return file?.length()?.coerceAtLeast(0L) ?: 0L
    }

    private fun openChunkForDate(date: LocalDate) {
        // Закрываем предыдущий файл, если он был.
        close()

        currentDate = date
        
        // Генерируем новый уникальный chunkId
        currentChunkId = chunkIdGenerator.incrementAndGet()
        
        // Сохраняем последний chunkId в SharedPreferences
        val prefs = context.getSharedPreferences("binary_log_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_chunk_id", currentChunkId).apply()
        
        val fileName = "debug_logs_${date}_${currentChunkId}.bin"
        val file = File(logsDir, fileName)
        val isNewFile = !file.exists()

        currentStream = FileOutputStream(file, true)

        if (isNewFile) {
            writeHeader(currentStream!!, date, currentChunkId)
        }
    }

    /**
     * Записать заголовок файла логов.
     *
     * Формат заголовка версии 1.1 (компактный, фиксированной структуры):
     * - 4 байта: ASCII-магик "HDBG"
     * - 1 байт: мажорная версия формата (1 для версии 1.1)
     * - 1 байт: минорная версия формата (1 для версии 1.1)
     * - 2 байта: год (unsigned short, little-endian)
     * - 1 байт: месяц (1–12)
     * - 1 байт: день (1–31)
     * - 1 байт: мажорная ревизия словаря
     * - 1 байт: минорная ревизия словаря
     * - 1 байт: длина deviceId (0–255)
     * - N байт: deviceId в UTF‑8 (если есть)
     * - 8 байт: chunkId (unsigned long, little-endian)
     */
    private fun writeHeader(stream: FileOutputStream, date: LocalDate, chunkId: Long) {
        try {
            // Magic "HDBG"
            stream.write(byteArrayOf('H'.code.toByte(), 'D'.code.toByte(), 'B'.code.toByte(), 'G'.code.toByte()))

            // Format version 1.1
            stream.write(1) // major
            stream.write(1) // minor

            // Date
            val year = date.year
            val month = date.monthValue
            val day = date.dayOfMonth
            stream.write(year and 0xFF)
            stream.write((year ushr 8) and 0xFF)
            stream.write(month and 0xFF)
            stream.write(day and 0xFF)

            // Dictionary revision
            stream.write(dictionaryRevision.major and 0xFF)
            stream.write(dictionaryRevision.minor and 0xFF)

            // Device ID (optional)
            val deviceId = getDeviceId()
            if (deviceId != null) {
                val bytes = deviceId.toByteArray(Charsets.UTF_8)
                val length = bytes.size.coerceAtMost(255)
                stream.write(length)
                stream.write(bytes, 0, length)
            } else {
                stream.write(0)
            }
            
            // Chunk ID (8 bytes, little-endian)
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putLong(chunkId)
            stream.write(buffer.array())

            stream.flush()
        } catch (_: Exception) {
            // Ошибки заголовка игнорируем, чтобы не мешать работе приложения.
        }
    }

    private fun getDeviceId(): String? {
        return try {
            DeviceIdHelper.getDeviceId(context)
        } catch (_: Exception) {
            null
        }
    }
}



