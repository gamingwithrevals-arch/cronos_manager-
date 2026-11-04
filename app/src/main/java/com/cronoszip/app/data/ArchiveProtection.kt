package com.cronoszip.app.data

/**
 * Asks the engine whether archives need a password, without extracting anything.
 * These calls block, so run them off the main thread.
 */
object ArchiveProtection {
    private val repository = ArchiveRepository()

    /** The archives in [paths] that cannot be opened without a password. */
    fun protectedArchives(paths: List<String>): List<String> =
        paths.filter { path ->
            try {
                repository.check(CheckConfig(archive = path))
                false
            } catch (error: EngineException) {
                error.code == "PASSWORD_NEEDED"
            } catch (error: Exception) {
                false
            }
        }

    /** False only when the engine says this password is wrong for [path]. */
    fun unlocks(path: String, password: String): Boolean =
        try {
            repository.check(CheckConfig(archive = path, password = password))
            true
        } catch (error: EngineException) {
            error.code != "WRONG_PASSWORD"
        } catch (error: Exception) {
            true
        }
}
