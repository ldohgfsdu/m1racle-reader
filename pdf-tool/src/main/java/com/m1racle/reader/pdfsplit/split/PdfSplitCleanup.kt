package com.m1racle.reader.pdfsplit.split

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class SplitCleanupFailure(
    val displayName: String,
    val reason: String,
)

fun formatSplitCleanupMessage(
    attempted: Int,
    deleted: Int,
    failures: List<SplitCleanupFailure>,
    maxFailuresShown: Int = 3,
): String {
    require(attempted >= 0)
    require(deleted in 0..attempted)
    require(failures.size == attempted - deleted)
    require(maxFailuresShown > 0)
    return when {
        attempted == 0 -> "未生成需要清理的文件"
        failures.isEmpty() -> "写入中的文件已清理"
        else -> {
            val shown = failures.take(maxFailuresShown).joinToString("、") {
                "${it.displayName}（${it.reason}）"
            }
            val omitted = failures.size - maxFailuresShown
            val more = if (omitted > 0) "，另有 $omitted 个" else ""
            "已删除 $deleted/$attempted 个未完成文件；仍残留：$shown$more"
        }
    }
}

data class SplitCleanupResult(
    val attempted: Int,
    val deleted: Int,
    val failures: List<SplitCleanupFailure>,
) {
    init {
        formatSplitCleanupMessage(attempted, deleted, failures)
    }

    val isComplete: Boolean get() = failures.isEmpty()

    val residualNames: List<String> get() = failures.map { it.displayName }

    fun userMessage(): String =
        formatSplitCleanupMessage(attempted, deleted, failures)
}

internal data class CreatedOutput<T>(
    val displayName: String,
    val reference: T,
)

internal fun <T> cleanCreatedOutputs(
    outputs: List<CreatedOutput<T>>,
    deleteDocument: (T) -> Boolean,
): SplitCleanupResult {
    var deleted = 0
    val failures = ArrayList<SplitCleanupFailure>()
    outputs.forEach { output ->
        val failure = try {
            if (deleteDocument(output.reference)) {
                deleted += 1
                null
            } else {
                SplitCleanupFailure(output.displayName, "系统未删除")
            }
        } catch (error: Exception) {
            val reason = error.message?.takeIf { it.isNotBlank() }
                ?: error.javaClass.simpleName
            SplitCleanupFailure(output.displayName, "删除异常：$reason")
        }
        failure?.let(failures::add)
    }
    return SplitCleanupResult(
        attempted = outputs.size,
        deleted = deleted,
        failures = failures,
    )
}

internal suspend fun <T> cleanCreatedOutputsNonCancellable(
    outputs: List<CreatedOutput<T>>,
    deleteDocument: (T) -> Boolean,
): SplitCleanupResult = withContext(NonCancellable) {
    cleanCreatedOutputs(outputs, deleteDocument)
}
