package com.bernaferrari.bravedns.backup

import java.io.File
import java.io.IOException

/** Stages copies before databases close and retains originals until the restore commits. */
internal class RestoreFileTransaction(
    private val databaseDir: File,
    private val workDir: File,
    private val names: List<String>,
) {
    private val staged = File(workDir, "staged")
    private val originals = File(workDir, "originals")
    private val moved = mutableListOf<String>()
    private val installed = mutableListOf<String>()

    fun stage(source: File) {
        check(staged.mkdirs() && originals.mkdirs()) { "Cannot create restore staging directories" }
        names.forEach { name ->
            val original = File(source, name)
            if (original.isDirectory) {
                if (!original.copyRecursively(File(staged, name))) throw IOException("Cannot stage $name")
            } else if (original.isFile) original.copyTo(File(staged, name))
        }
    }

    fun install() {
        try {
            names.forEach { name ->
                val current = File(databaseDir, name)
                if (current.exists()) {
                    move(current, File(originals, name))
                    moved += name
                }
            }
            names.forEach { name ->
                val replacement = File(staged, name)
                if (replacement.exists()) {
                    move(replacement, File(databaseDir, name))
                    installed += name
                }
            }
        } catch (error: IOException) {
            rollback()
            throw error
        }
    }

    fun rollback() {
        installed.forEach { name ->
            val file = File(databaseDir, name)
            if (file.exists() && !file.deleteRecursively()) throw IOException("Cannot remove restored $name")
        }
        installed.clear()
        moved.forEach { name -> move(File(originals, name), File(databaseDir, name)) }
        moved.clear()
    }

    fun commit() {
        workDir.deleteRecursively()
        moved.clear()
        installed.clear()
    }

    private fun move(from: File, to: File) {
        if (!from.renameTo(to)) throw IOException("Cannot replace database file ${to.name}")
    }
}
