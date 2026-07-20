package com.celzero.bravedns.platform

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun platformHttpEngine(): HttpClientEngineFactory<*> = CIO
