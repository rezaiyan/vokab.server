package com.alirezaiyan.vokab.server

import java.sql.Timestamp

/**
 * H2 compatibility function for PostgreSQL's TO_TIMESTAMP(epoch_seconds).
 * Referenced by CREATE ALIAS in schema.sql.
 */
object H2CompatFunctions {

    @JvmStatic
    fun toTimestamp(epochSeconds: Double): Timestamp {
        return Timestamp((epochSeconds * 1000).toLong())
    }
}
