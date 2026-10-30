package com.bernaferrari.bravedns.net.go

import android.content.Context
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.service.EventLogger
import com.bernaferrari.bravedns.service.PersistentState
import com.celzero.firestack.backend.Proxy
import com.celzero.firestack.intra.Tunnel
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.objenesis.ObjenesisStd
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
internal class GoVpnAdapterTestFixture {
    private val scopes = mutableListOf<CoroutineScope>()
    private val runners = mutableListOf<NativeProbeRunner>()
    private val dispatcher = StandardTestDispatcher()

    lateinit var context: Context
    lateinit var persistentState: PersistentState
    lateinit var appConfig: AppConfig
    lateinit var connTracker: ConnectionTrackerRepository
    lateinit var eventLogger: EventLogger

    fun setUp() {
        Dispatchers.setMain(dispatcher)

        context = RuntimeEnvironment.getApplication()
        persistentState = mockk(relaxed = true)
        appConfig = mockk(relaxed = true)
        connTracker = mockk(relaxed = true)
        eventLogger = mockk(relaxed = true)

        every { eventLogger.log(any(), any(), any(), any(), any(), any()) } just Runs
        every { appConfig.getPcapFilePath() } returns ""
        every { appConfig.getDnsType() } returns AppConfig.DnsType.DOH
        every { appConfig.isSmartDnsEnabled() } returns false
        every { persistentState.localBlocklistStamp } returns ""
        every { persistentState.rpnDnsTunTypes } returns ""

        every { persistentState.wireguardPlainFileMigrationDone } returns true
        startKoin { modules(module {
            single<Context> { context }; single { persistentState }; single { appConfig }
            single { mockk<com.bernaferrari.bravedns.database.WgConfigFilesRepository>(relaxed = true) }
            single { mockk<com.bernaferrari.bravedns.database.WgHopMapRepository>(relaxed = true) }
        }) }
        // Finish singleton initialization with its cached dependencies before object stubbing.
        runTest(dispatcher) {
            com.bernaferrari.bravedns.service.WireguardManager.load(false)
            com.bernaferrari.bravedns.wireguard.WgHopManager.load(false)
        }
    }

    fun tearDown() {
        scopes.forEach { it.cancel() }; runners.forEach { it.close() }
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
        clearAllMocks()
    }

    fun newAdapter(tunnel: Tunnel, dnsInstaller: (Tunnel, Proxy) -> Unit = { _, _ -> }): GoVpnAdapter {
        val adapter = ObjenesisStd().newInstance(GoVpnAdapter::class.java)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scopes.add(it) }

        GoVpnAdapter::class.java.getDeclaredField("installProxyDns").apply {
            isAccessible = true; set(adapter, dnsInstaller)
        }
        // Manually wire constructor deps since Intra.connect is bypassed via Objenesis.
        GoVpnAdapter::class.java.getDeclaredField("persistentState").apply {
            isAccessible = true
            set(adapter, persistentState)
        }
        GoVpnAdapter::class.java.getDeclaredField("appConfig").apply {
            isAccessible = true
            set(adapter, appConfig)
        }
        GoVpnAdapter::class.java.getDeclaredField("connTrackerDb").apply {
            isAccessible = true
            set(adapter, connTracker)
        }
        GoVpnAdapter::class.java.getDeclaredField("eventLogger").apply {
            isAccessible = true
            set(adapter, eventLogger)
        }

        GoVpnAdapter::class.java.getDeclaredField("context").apply {
            isAccessible = true
            set(adapter, context)
        }
        GoVpnAdapter::class.java.getDeclaredField("externalScope").apply {
            isAccessible = true
            set(adapter, scope)
        }
        GoVpnAdapter::class.java.getDeclaredField("tunnel").apply {
            isAccessible = true
            set(adapter, tunnel)
        }

        GoVpnAdapter::class.java.getDeclaredField("captureBackendIdentity").apply {
            isAccessible = true; set(adapter, Any())
        }
        GoVpnAdapter::class.java.getDeclaredField("pcapMutex").apply {
            isAccessible = true; set(adapter, kotlinx.coroutines.sync.Mutex())
        }
        GoVpnAdapter::class.java.getDeclaredField("proxyGeneration").apply {
            isAccessible = true; set(adapter, java.util.concurrent.atomic.AtomicLong())
        }
        GoVpnAdapter::class.java.getDeclaredField("nativeProbes").apply {
            isAccessible = true; set(adapter, NativeProbeRunner().also { runners.add(it) })
        }
        return adapter
    }

}
