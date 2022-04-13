package com.github.brezinajn

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias Reducer<ACTION, STATE> = (ACTION, STATE) -> STATE
typealias SideEffect<ACTION, STATE, ERROR> = suspend (ACTION, STATE, Dispatch<ACTION>) -> Either<ERROR, Unit>
typealias Dispatch<ACTION> = (ACTION) -> Unit

fun <ACTION, STATE> MVI(
    setState: (STATE) -> Unit,
    getState: () -> STATE,
    reducer: Reducer<ACTION, STATE>,
    sideEffect: ((ACTION, STATE, Dispatch<ACTION>) -> Unit)? = null,
): Dispatch<ACTION> {
    fun dispatch(action: ACTION) {
        val newState = reducer(action, getState())
        setState(newState)
        sideEffect?.invoke(action, newState, ::dispatch)
    }

    return ::dispatch
}

inline fun <ACTION, STATE, ERROR> MVI(
    noinline getState: () -> STATE,
    noinline setState: (STATE) -> Unit,
    noinline reducer: Reducer<ACTION, STATE>,
    coroutineScope: CoroutineScope,
    crossinline sideEffect: SideEffect<ACTION, STATE, ERROR>,
    crossinline onError: suspend (ACTION, STATE, ERROR) -> Unit,
    crossinline onThrowable: suspend (ACTION, STATE, Throwable) -> Unit,
    noinline logAction: (suspend (ACTION, STATE) -> Unit)? = null,
): Dispatch<ACTION> = MVI(
    getState = getState,
    setState = setState,
    reducer = reducer,
    sideEffect = { action, newState, dispatch ->
        if (logAction != null) {
            coroutineScope.launch {
                logAction(action, newState)
            }
        }

        coroutineScope.launch {
            Either.catch {
                sideEffect(action, newState, dispatch)
                    .tapLeft { onError(action, newState, it) }
            }.tapLeft { onThrowable(action, newState, it) }
        }
    }
)
