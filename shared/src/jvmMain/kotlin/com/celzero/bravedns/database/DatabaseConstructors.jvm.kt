
package com.celzero.bravedns.database
import androidx.room3.RoomDatabaseConstructor
actual object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    actual override fun initialize(): AppDatabase = error("Room KSP not enabled for JVM; use android build")
}
actual object LogDatabaseConstructor : RoomDatabaseConstructor<LogDatabase> {
    actual override fun initialize(): LogDatabase = error("Room KSP not enabled for JVM; use android build")
}
actual object ConsoleLogDatabaseConstructor : RoomDatabaseConstructor<ConsoleLogDatabase> {
    actual override fun initialize(): ConsoleLogDatabase = error("Room KSP not enabled for JVM; use android build")
}
