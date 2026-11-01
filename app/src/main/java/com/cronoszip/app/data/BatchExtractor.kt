package com.cronoszip.app.data

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where one archive stands in a batch. */
enum class BatchStatus { Waiting, Working, Done, Skipped, Failed, Paused }

data class BatchItem(
    val path: String,
    val name: String,
    val status: BatchStatus = BatchStatus.Waiting,
    val message: String = "",
)

data class BatchState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val items: List<BatchItem> = emptyList(),
)

/**
 * Extracts several archives one after another, each in its own folder, using the default
 * settings. It uses the same engine calls as single extraction. Password-protected archives,
 * archives that need the Keep option, and archives whose files already exist are skipped with
 * a reason, so nothing is overwritten without asking.
 */
object BatchExtractor {
    private val repository = ArchiveRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var stopRequested = false
    private val _state = MutableStateFlow(BatchState())
    val state: StateFlow<BatchState> = _state.asStateFlow()

    fun start(paths: List<String>, keepArchive: Boolean, keepFree: Long, verifyFirst: Boolean) {
        if (_state.value.running || paths.isEmpty()) return
        stopRequested = false
        _state.value = BatchState(
            running = true,
            finished = false,
            items = paths.map { BatchItem(path = it, name = File(it).name) },
        )
        scope.launch {
            paths.forEachIndexed { index, path ->
                if (stopRequested) {
                    mark(index, BatchStatus.Skipped, "Stopped before this archive started.")
                } else {
                    mark(index, BatchStatus.Working, "Extracting…")
                    val (status, message) = runOne(path, keepArchive, keepFree, verifyFirst)
                    mark(index, status, message)
                }
            }
            _state.update { it.copy(running = false, finished = true) }
        }
    }

    /** Stops the current archive (its progress is saved) and skips the rest. */
    fun stop() {
        stopRequested = true
        repository.cancel()
    }

    /** Forgets a finished batch. Does nothing while one is running. */
    fun clear() {
        if (!_state.value.running) _state.value = BatchState()
    }

    private fun runOne(
        path: String,
        keepArchive: Boolean,
        keepFree: Long,
        verifyFirst: Boolean,
    ): Pair<BatchStatus, String> {
        val check = try {
            repository.check(
                CheckConfig(archive = path, output = null, keepArchive = keepArchive, keepFree = keepFree, password = null),
            )
        } catch (error: EngineException) {
            return if (error.code == "PASSWORD_NEEDED") {
                BatchStatus.Skipped to "Password protected. Extract it alone to enter the password."
            } else {
                BatchStatus.Failed to error.message
            }
        } catch (error: Exception) {
            return BatchStatus.Failed to (error.message ?: "The archive could not be read.")
        }
        if (!check.plan.fits) {
            return BatchStatus.Skipped to "Not enough free space for this archive."
        }
        val refusal = check.inPlaceRefusal
        if (refusal != null && !keepArchive) {
            return BatchStatus.Skipped to "$refusal. Extract it alone with Keep archive on."
        }
        return try {
            val summary = repository.extract(
                ExtractConfig(
                    archive = path,
                    output = null,
                    keepArchive = keepArchive,
                    replace = "none",
                    verifyFirst = verifyFirst,
                    keepFree = keepFree,
                    password = null,
                ),
            ) { }
            if (summary.paused) {
                BatchStatus.Paused to "Paused. Progress is saved; run it again to continue."
            } else if (summary.kept) {
                BatchStatus.Done to "Extracted to ${summary.output}. Archive kept."
            } else {
                BatchStatus.Done to "Extracted to ${summary.output}."
            }
        } catch (error: EngineException) {
            if (error.code == "EXISTS") {
                BatchStatus.Skipped to "Files already exist there. Extract it alone to replace or keep them."
            } else {
                BatchStatus.Failed to error.message
            }
        } catch (error: Exception) {
            BatchStatus.Failed to (error.message ?: "The extraction failed.")
        }
    }

    private fun mark(index: Int, status: BatchStatus, message: String) {
        _state.update { s ->
            s.copy(
                items = s.items.mapIndexed { i, item ->
                    if (i == index) item.copy(status = status, message = message) else item
                },
            )
        }
    }
}
