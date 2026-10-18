package com.matrix.launcher

import com.matrix.launcher.net.PortClassifier
import com.matrix.launcher.net.PortState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PortClassifierTest {
    @Test fun successIsReachable() = assertEquals(PortState.REACHABLE, PortClassifier.classify(null))

    @Test fun timeout() {
        assertEquals(PortState.TIMED_OUT, PortClassifier.classify(SocketTimeoutException("connect timed out")))
        assertEquals(PortState.TIMED_OUT, PortClassifier.classify(ConnectException("failed to connect: ETIMEDOUT (Connection timed out)")))
    }

    @Test fun refused() {
        assertEquals(PortState.REFUSED, PortClassifier.classify(ConnectException("Connection refused")))
        assertEquals(PortState.REFUSED, PortClassifier.classify(ConnectException("failed to connect to /10.0.0.5 (port 81): isConnectException: ECONNREFUSED (Connection refused)")))
    }

    @Test fun unreachable() {
        assertEquals(PortState.UNREACHABLE, PortClassifier.classify(NoRouteToHostException("No route to host")))
        assertEquals(PortState.UNREACHABLE, PortClassifier.classify(ConnectException("connect failed: ENETUNREACH (Network is unreachable)")))
        assertEquals(PortState.UNREACHABLE, PortClassifier.classify(ConnectException("EHOSTUNREACH (No route to host)")))
    }

    @Test fun everythingElseIsErrorNotGuessed() {
        assertEquals(PortState.ERROR, PortClassifier.classify(UnknownHostException("nope")))
        assertEquals(PortState.ERROR, PortClassifier.classify(IOException("boom")))
        assertEquals(PortState.ERROR, PortClassifier.classify(SocketException("Permission denied")))
        assertEquals(PortState.ERROR, PortClassifier.classify(ConnectException(null as String?)))
    }
}
