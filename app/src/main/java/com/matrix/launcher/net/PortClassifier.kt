package com.matrix.launcher.net

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Maps what the OS reported for a TCP connect attempt to a [PortState]. null means "connect succeeded". */
object PortClassifier {
    fun classify(error: Throwable?): PortState {
        if (error == null) return PortState.REACHABLE
        return when (error) {
            is SocketTimeoutException -> PortState.TIMED_OUT
            is NoRouteToHostException -> PortState.UNREACHABLE
            is PortUnreachableException -> PortState.UNREACHABLE
            is UnknownHostException -> PortState.ERROR
            is ConnectException -> fromMessage(error.message)
            is SocketException -> fromMessage(error.message)
            else -> PortState.ERROR
        }
    }

    internal fun fromMessage(message: String?): PortState {
        val s = message?.lowercase().orEmpty()
        return when {
            "refused" in s || "econnrefused" in s -> PortState.REFUSED
            "timed out" in s || "timeout" in s || "etimedout" in s -> PortState.TIMED_OUT
            "unreachable" in s || "no route" in s || "enetunreach" in s || "ehostunreach" in s -> PortState.UNREACHABLE
            else -> PortState.ERROR
        }
    }
}
