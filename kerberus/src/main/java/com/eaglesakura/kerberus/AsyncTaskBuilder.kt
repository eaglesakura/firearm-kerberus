@file:Suppress("unused")

package com.eaglesakura.kerberus

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun <T> dispatcherEntry(task: AsyncTaskBuilder<T>, dispatcher: CoroutineDispatcher): Job {
    return GlobalScope.launch(dispatcher) {
        try {
            task.semaphore.run {
                val value = task.onBackground(this)

                withContext(Dispatchers.Main) {
                    task.onSuccess?.invoke(value)
                    task.onFinalize?.invoke()
                }
            }
        } catch (err: Exception) {
            if (err is CancellationException) {
                when (task.onCancel) {
                    null -> throw err
                    else -> withContext(Dispatchers.Main) { task.onCancel!!(err) }
                }
            } else {
                when (task.onError) {
                    null -> throw err
                    else -> withContext(Dispatchers.Main) {
                        task.onError!!(err)
                        task.onFinalize?.invoke()
                    }
                }
            }
        }
    }
}

/**
 * Task builder.
 *
 * e.g.)
 * asyncTask {
 *      onBackground {
 *          // do something.
 *      }
 *
 *      onSuccess {
 *          // do something.
 *      }
 *
 *      onFailed {
 *          // do something.
 *      }
 *
 *      onCancel {
 *          // do something.
 *      }
 * }
 */
class AsyncTaskBuilder<T>(
    var semaphore: Semaphore = Semaphore.NonBlocking,
    var entryPoint: AsyncTaskBuilder<T>.() -> Job
) {
    lateinit var onBackground: suspend (CoroutineScope.() -> T)

    /**
     * This property is finalize-handler after than "onBackground".
     * "T" is set from "onBackground" result value.
     */
    internal var onSuccess: ((value: T) -> Unit)? = null

    /**
     * This property is error-handler to exception throws from "onBackground" method.
     */
    internal var onError: ((err: Exception) -> Unit)? = null

    /**
     * This property is cancel-handler to "onBackground" method.
     */
    internal var onCancel: ((err: CancellationException) -> Unit)? = null

    /**
     * This function will  always called.
     */
    internal var onFinalize: (() -> Unit)? = null

    /**
     * Execute task main in selection dispatcher.
     */
    fun onBackground(block: suspend (CoroutineScope.() -> T)) {
        this.onBackground = block
    }

    /**
     * This function is finalize-handler after than "onBackground".
     * "T" is set from "onBackground" result value.
     */
    fun onSuccess(block: (value: T) -> Unit) {
        this.onSuccess = block
    }

    /**
     * This function is error-handler to exception throws from "onBackground" method.
     */
    fun onError(block: (err: Exception) -> Unit) {
        this.onError = block
    }

    /**
     * This function is cancel-handler to "onBackground" method.
     */
    fun onCancel(block: (err: CancellationException) -> Unit) {
        this.onCancel = block
    }

    /**
     * This function will  always called.
     */
    fun onFinalize(block: () -> Unit) {
        this.onFinalize = block
    }
}

/**
 * Start An async task with user-selection dispatcher.
 * "onBackground" function execute from CoroutineDispatcher thread in arguments.
 * "onSuccess", "onError", and "onCancel" functions are execute from Main-Thread.
 */
fun <T> asyncTask(
    semaphore: Semaphore = Semaphore.NonBlocking,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    builder: (AsyncTaskBuilder<T>.() -> Unit)
): Job {
    return asyncTask(semaphore, { dispatcherEntry(this, dispatcher) }, builder)
}

/**
 * Start An async task with user-selection "EntryPoint" object.
 */
private fun <T> asyncTask(
    semaphore: Semaphore = Semaphore.NonBlocking,
    entryPoint: AsyncTaskBuilder<T>.() -> Job,
    builder: (AsyncTaskBuilder<T>.() -> Unit)
): Job {
    val context = AsyncTaskBuilder(semaphore, entryPoint)
    builder(context)
    return context.entryPoint(context)
}
