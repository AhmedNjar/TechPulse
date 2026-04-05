package com.example.spindle.presentation.viewmodels

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

interface MviState
interface MviIntent
interface MviEffect

abstract class BaseViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S
) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()
    protected val currentState: S get() = _state.value
    protected fun updateState(transform: S.() -> S) = _state.update { it.transform() }

    private val _effect = Channel<E>(Channel.BUFFERED)
    val effect: Flow<E> = _effect.receiveAsFlow()
    protected fun emitEffect(effect: E) { scope.launch { _effect.send(effect) } }

    abstract fun processIntent(intent: I)

    open fun onCleared() { scope.cancel() }
}
