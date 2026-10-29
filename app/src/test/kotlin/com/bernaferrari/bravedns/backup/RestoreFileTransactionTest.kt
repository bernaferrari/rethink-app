package com.bernaferrari.bravedns.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreFileTransactionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `staging leaves live files unchanged and rollback restores both databases`() {
        val live = temporary.newFolder("live")
        val source = temporary.newFolder("source")
        val work = temporary.newFolder("work")
        val names = listOf("app.db", "logs.db", "app.db-wal")
        names.forEach { File(live, it).writeText("original $it") }
        names.take(2).forEach { File(source, it).writeText("replacement $it") }
        val transaction = RestoreFileTransaction(live, work, names)
        transaction.stage(source)
        assertEquals("original app.db", File(live, "app.db").readText())
        transaction.install()
        assertEquals("replacement logs.db", File(live, "logs.db").readText())
        assertFalse(File(live, "app.db-wal").exists())
        transaction.rollback()
        names.forEach { assertEquals("original $it", File(live, it).readText()) }
    }

    @Test fun `committing discards rollback files only after replacements are installed`() {
        val live = temporary.newFolder("live")
        val source = temporary.newFolder("source")
        val work = temporary.newFolder("work")
        File(live, "app.db").writeText("old")
        File(source, "app.db").writeText("new")
        val transaction = RestoreFileTransaction(live, work, listOf("app.db"))
        transaction.stage(source)
        transaction.install()
        transaction.commit()
        assertEquals("new", File(live, "app.db").readText())
        assertFalse(work.exists())
    }
}
