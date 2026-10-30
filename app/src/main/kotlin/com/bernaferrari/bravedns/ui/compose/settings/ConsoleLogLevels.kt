package com.bernaferrari.bravedns.ui.compose.settings

import Logger

/** The UI omits USR (wire level 7); its last, None-labelled option is wire level 8. */
internal fun consoleLogSelectedLevel(index: Int): Long {
    require(index in 0..7)
    return if (index == 7) Logger.LoggerLevel.NONE.id else index.toLong()
}

internal fun consoleLogSelectionIndex(level: Long): Int = if (level >= Logger.LoggerLevel.USR.id) 7 else level.toInt().coerceIn(0, 6)
