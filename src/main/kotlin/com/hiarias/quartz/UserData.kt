package com.hiarias.quartz

import com.hiarias.quartz.util.BigDecimalSerializer
import com.hiarias.quartz.util.LazyLocation
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class UserData(
    @Serializable(with = BigDecimalSerializer::class)
    var money: BigDecimal,
    var nickName: String?,
    var homes: Map<String, LazyLocation>,
    var lastLocation: LazyLocation,
    var logoutLocation: LazyLocation,

) {
    var ipAddress: String = ""
    var afk: Boolean = false
    var timeStamps: TimeStamps = TimeStamps()

    @Serializable
    data class TimeStamps(
       var login: Long = 0L,
       var logout: Long = 0L,
       var lastTeleport: Long = 0L,
    )
}
