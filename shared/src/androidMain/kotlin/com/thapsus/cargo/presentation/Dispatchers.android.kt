package com.thapsus.cargo.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun dispatcherForUi(): CoroutineDispatcher = Dispatchers.Main
