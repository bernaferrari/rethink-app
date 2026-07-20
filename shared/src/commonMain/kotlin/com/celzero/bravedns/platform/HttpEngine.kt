package com.celzero.bravedns.platform

import io.ktor.client.engine.HttpClientEngineFactory

/** Platform HTTP engine (CIO on Android/JVM; swap for Darwin/OkHttp on other targets later). */
expect fun platformHttpEngine(): HttpClientEngineFactory<*>
