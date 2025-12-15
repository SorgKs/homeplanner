package com.homeplanner.debug

/**
 * Ревизия словаря сообщений.
 * 
 * Используется для версионирования словаря логирования.
 * При изменении словаря нужно обновить ревизию.
 */
data class DictionaryRevision(
    val major: Int,  // Мажорная версия
    val minor: Int   // Минорная версия
) {
    override fun toString(): String = "$major.$minor"
    
    companion object {
        /**
         * Текущая ревизия словаря.
         * Увеличивать minor при добавлении новых кодов.
         * Увеличивать major при изменении или удалении существующих кодов.
         */
        val CURRENT = DictionaryRevision(major = 1, minor = 0)
    }
}
