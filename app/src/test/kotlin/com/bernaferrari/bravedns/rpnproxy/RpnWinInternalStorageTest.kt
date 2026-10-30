package com.bernaferrari.bravedns.rpnproxy

import android.app.Application
import android.content.Context
import com.bernaferrari.bravedns.database.RpnProxy
import com.bernaferrari.bravedns.database.RpnProxyRepository
import com.bernaferrari.bravedns.service.EncryptedFileManager
import com.bernaferrari.bravedns.service.EncryptionException
import io.mockk.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RpnWinInternalStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var repository: RpnProxyRepository
    private lateinit var store: RpnWinEntitlementStore
    private lateinit var row: RpnProxy

    @Before fun setUp() {
        context = mockk()
        every { context.filesDir } returns temporary.root
        every { context.getExternalFilesDir(any()) } throws AssertionError("External storage must never be consulted")
        repository = mockk()
        row = RpnProxy(7, "WIN", "/untrusted/state.json", "/untrusted/external.json", false, false,
            0L, 0L, "", "", 0, 0L)
        coEvery { repository.getProxyById(7) } returns row
        coEvery { repository.update(any()) } returns 1
        store = RpnWinEntitlementStore(context, repository, 7, "WIN", "state.json", "entitlement.json", "rpn", { it.toByteArray() })
        mockkObject(EncryptedFileManager)
        every { EncryptedFileManager.write(context, any<ByteArray>(), any()) } answers {
            thirdArg<File>().apply { parentFile!!.mkdirs(); writeBytes(secondArg()) }; true
        }
        every { EncryptedFileManager.readByteArray(context, any()) } answers { secondArg<File>().readBytes() }
    }

    @After fun tearDown() { unmockkAll() }

    @Test fun `fresh entitlement and state are saved internally and database paths match`() = runTest {
        store.storeWinEntitlement("entitlement")
        assertTrue(store.updateWinConfigState("state".toByteArray()))
        assertEquals(File(temporary.root, "rpn/entitlement.json").absolutePath, row.serverResPath)
        assertEquals(File(temporary.root, "rpn/state.json").absolutePath, row.configPath)
        assertEquals("entitlement", store.getWinEntitlement()!!.decodeToString())
        assertEquals("state", store.getWinExistingData()!!.decodeToString())
        verify(exactly = 0) { context.getExternalFilesDir(any()) }
    }

    @Test fun `readers select canonical internal files rather than absolute database paths`() = runTest {
        val directory = File(temporary.root, "rpn").apply { mkdirs() }
        File(directory, "entitlement.json").writeText("canonical-entitlement")
        File(directory, "state.json").writeText("canonical-state")
        assertNull(store.getWinEntitlement())
        assertNull(store.getWinExistingData())
        row.serverResPath = File(directory, "entitlement.json").absolutePath
        row.configPath = File(directory, "state.json").absolutePath
        assertEquals("canonical-entitlement", store.getWinEntitlement()!!.decodeToString())
        assertEquals("canonical-state", store.getWinExistingData()!!.decodeToString())
    }

    @Test fun `identity reset paths never revive leftover canonical entitlement or state`() = runTest {
        val directory = File(temporary.root, "rpn").apply { mkdirs() }
        File(directory, "entitlement.json").writeText("old-entitlement")
        File(directory, "state.json").writeText("old-state")
        row.serverResPath = ""
        row.configPath = ""
        assertNull(store.getWinEntitlement())
        assertNull(store.getWinExistingData())
        verify(exactly = 0) { EncryptedFileManager.readByteArray(any(), any()) }
    }

    @Test fun `failed state replacement never predeletes the previous file or updates its row`() = runTest {
        val file = File(temporary.root, "rpn/state.json").apply { parentFile!!.mkdirs(); writeText("old-state") }
        every { EncryptedFileManager.write(context, any<ByteArray>(), any()) } answers {
            assertEquals("old-state", file.readText())
            throw EncryptionException.WriteFailed(IOException("Injected write failure"))
        }
        assertFalse(store.updateWinConfigState("new-state".toByteArray()))
        assertEquals("old-state", file.readText())
        coVerify(exactly = 0) { repository.update(any()) }
    }
}
