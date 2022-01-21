package com.hiarias.quartz

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import java.util.logging.LogRecord
import java.util.logging.Logger

class QuartzLogger(name: String) : Logger(name, null) {

    private val logger: org.apache.logging.log4j.Logger = LogManager.getLogger(name)


    fun toLog4JLogger(): org.apache.logging.log4j.Logger = logger

    override fun log(record: LogRecord) {
        if (record.thrown == null) {
            logger.log(convertLevel(record.level), record.message)
        } else {
            logger.log(convertLevel(record.level), record.message, record.thrown)
        }
    }

    private fun convertLevel(level: java.util.logging.Level): Level {
        return when(level) {
            java.util.logging.Level.ALL -> Level.ALL
            java.util.logging.Level.CONFIG -> Level.TRACE
            java.util.logging.Level.WARNING -> Level.WARN
            java.util.logging.Level.INFO -> Level.INFO
            java.util.logging.Level.OFF -> Level.OFF
            java.util.logging.Level.SEVERE -> Level.FATAL
            java.util.logging.Level.FINE -> Level.WARN
            java.util.logging.Level.FINER -> Level.WARN
            java.util.logging.Level.FINEST -> Level.WARN
            else -> Level.ALL
        }
    }
}
