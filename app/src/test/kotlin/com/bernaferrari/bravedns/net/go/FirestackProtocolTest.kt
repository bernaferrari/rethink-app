package com.bernaferrari.bravedns.net.go

import com.celzero.firestack.backend.DNSOpts
import com.celzero.firestack.backend.Rpn
import com.celzero.firestack.backend.RpnOps
import com.celzero.firestack.backend.Router
import com.celzero.firestack.intra.Bridge
import org.junit.Assert.*
import org.junit.Test

/** Checks the API paired with the pin, without initializing native classes. */
class FirestackProtocolTest {
    @Test fun `bridge carries ALG fact and synchronous routing verdict`() {
        val flow = Bridge::class.java.methods.single { it.name == "flow" }
        assertEquals(9, flow.parameterCount)
        assertEquals(Boolean::class.javaPrimitiveType, flow.parameterTypes.last())
        assertEquals("com.celzero.firestack.intra.Mark", flow.returnType.name)
        assertTrue(Bridge::class.java.methods.any { it.name == "onPrequery" })
    }
    @Test fun `registration separates entitlement from saved state`() {
        val register = Rpn::class.java.getMethod("registerWin", ByteArray::class.java,
            ByteArray::class.java, String::class.java, RpnOps::class.java)
        assertEquals(ByteArray::class.java, register.returnType)
    }
    @Test fun `DNS and router match upgraded protocol`() {
        assertFalse(DNSOpts::class.java.methods.any { it.name == "getPIDCSV" })
        assertNotNull(Router::class.java.getMethod("contains", String::class.java, String::class.java))
    }
}
