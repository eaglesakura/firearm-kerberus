@file:Suppress("unused")

package com.eaglesakura.kerberus

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Semaphore for coroutines.
 *
 * It is use to the TaskQueue or such else.
 *
 * @author @eaglesakura
 * @link https://github.com/eaglesakura/army-knife
 */
interface Semaphore {
    @Throws(CancellationException::class)
    suspend fun <R> run(block: suspend () -> R): R

    companion object {
        val NonBlocking: Semaphore = object : Semaphore {
            override suspend fun <R> run(block: suspend () -> R): R {
                return block()
            }
        }

        /**
         * For network input/output semaphore.
         */
        val Network: Semaphore = SemaphoreImpl(Runtime.getRuntime().availableProcessors() * 2 + 1)

        /**
         * For storage input/output semaphore.
         */
        val IO: Semaphore = SemaphoreImpl(Runtime.getRuntime().availableProcessors() * 2 + 1)

        /**
         * Global queue.
         */
        val Queue: Semaphore = SemaphoreImpl(1)

        /**
         * new instance for Task Queue.
         */
        fun newQueue(): Semaphore {
            return newInstance(1)
        }

        /**
         * new instance with parallel.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        fun newInstance(maxParallel: Int): Semaphore {
            return SemaphoreImpl(maxParallel)
        }
    }

    private class SemaphoreImpl(maxParallel: Int) : Semaphore {
        private val channel: Channel<Unit> = Channel(maxParallel)

        override suspend fun <R> run(block: suspend () -> R): R {
            channel.send(Unit)
            try {
                return block()
            } finally {
                withContext(NonCancellable) {
                    channel.receive()
                }
            }
        }
    }
}

@Deprecated("Replace to com.eaglesakura.kerberus.CoroutineScope.launch", ReplaceWith(""))
fun Semaphore.launch(context: CoroutineContext, block: suspend CoroutineScope.() -> Unit): Job {
    val self = this
    return GlobalScope.launch(context) {
        self.run { block() }
    }
}

@Deprecated("Replace to com.eaglesakura.kerberus.withSemaphore", ReplaceWith("withSemaphore"))
suspend fun <T> Semaphore.runWith(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T {
    return withContext(context) {
        block()
    }
}