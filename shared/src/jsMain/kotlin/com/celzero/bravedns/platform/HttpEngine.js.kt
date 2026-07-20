package com.celzero.bravedns.platform

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

actual fun platformHttpEngine(): HttpClientEngineFactory<*> = Js
