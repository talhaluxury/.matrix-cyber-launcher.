package com.matrix.launcher.net

/** Pure input validation and address maths. No Android and no network access, so it is unit-testable. */
data class Ipv4Range(val network: Long, val prefix: Int) {
    /** Usable host addresses (network and broadcast excluded). */
    val hostCount: Int get() = ((1L shl (32 - prefix)) - 2L).toInt()

    fun hosts(): List<Long> {
        val first = network + 1
        val last = network + (1L shl (32 - prefix)) - 2
        val out = ArrayList<Long>(hostCount)
        var a = first
        while (a <= last) {
            out.add(a)
            a++
        }
        return out
    }

    override fun toString(): String = NetValidation.formatIpv4(network) + "/" + prefix
}

data class PortListResult(val ports: List<Int>, val error: String?)

object NetValidation {
    const val MAX_PORTS = 64
    const val MIN_DISCOVERY_PREFIX = 22
    const val MAX_DISCOVERY_PREFIX = 30

    val COMMON_PORTS: List<Int> = listOf(21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 3306, 3389, 8080)

    /** Strict dotted-quad. Returns the address as an unsigned 32-bit value, or null. */
    fun parseIpv4(s: String): Long? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        var v = 0L
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            if (p.length > 1 && p[0] == '0') return null // no ambiguous leading zeros (octal look-alikes)
            val n = p.toInt()
            if (n > 255) return null
            v = (v shl 8) or n.toLong()
        }
        return v
    }

    fun formatIpv4(v: Long): String =
        "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    /** RFC 1123 style host name: labels of 1..63 letters/digits/hyphens, total <= 253. */
    fun isValidHostname(s: String): Boolean {
        if (s.isEmpty() || s.length > 253) return false
        val labels = s.split('.')
        for (l in labels) {
            if (l.isEmpty() || l.length > 63) return false
            if (l.first() == '-' || l.last() == '-') return false
            if (!l.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }) return false
        }
        // An all-numeric dotted string that is not a valid IPv4 address (e.g. 999.1.1.1) is not a host name.
        if (labels.all { l -> l.all { it in '0'..'9' } }) return false
        return true
    }

    /** A valid target is either a strict IPv4 literal or a valid host name. */
    fun isValidTarget(s: String): Boolean = parseIpv4(s) != null || isValidHostname(s)

    fun parsePort(s: String): Int? {
        if (s.isEmpty() || s.length > 5 || !s.all { it in '0'..'9' }) return null
        val n = s.toInt()
        return if (n in 1..65535) n else null
    }

    /** Accepts "80", "22,80,443", "8000-8010" and mixes of those. Duplicates are removed, order is kept. */
    fun parsePortList(text: String): PortListResult {
        val tokens = text.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return PortListResult(emptyList(), "NO PORT GIVEN")
        val out = LinkedHashSet<Int>()
        for (t in tokens) {
            if (t.contains('-')) {
                val a = t.substringBefore('-')
                val b = t.substringAfter('-')
                val lo = parsePort(a)
                val hi = parsePort(b)
                if (lo == null || hi == null || lo > hi) return PortListResult(emptyList(), "INVALID PORT RANGE \"$t\"")
                if (hi - lo + 1 > MAX_PORTS) return PortListResult(emptyList(), "RANGE \"$t\" EXCEEDS $MAX_PORTS PORTS")
                for (p in lo..hi) out.add(p)
            } else {
                val p = parsePort(t) ?: return PortListResult(emptyList(), "INVALID PORT \"$t\" (1-65535)")
                out.add(p)
            }
            if (out.size > MAX_PORTS) return PortListResult(emptyList(), "TOO MANY PORTS (MAX $MAX_PORTS)")
        }
        return PortListResult(out.toList(), null)
    }

    /** "192.168.1.0/24" style. The host bits are masked off. Prefix must be /22../30 so a scan stays small. */
    fun parseCidr(text: String): Ipv4Range? {
        val t = text.trim()
        val slash = t.indexOf('/')
        if (slash <= 0) return null
        val ip = parseIpv4(t.substring(0, slash)) ?: return null
        val prefixText = t.substring(slash + 1)
        if (prefixText.isEmpty() || prefixText.length > 2 || !prefixText.all { it in '0'..'9' }) return null
        val prefix = prefixText.toInt()
        if (prefix < MIN_DISCOVERY_PREFIX || prefix > MAX_DISCOVERY_PREFIX) return null
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return Ipv4Range(ip and mask, prefix)
    }

    /** RFC 1918 private space plus 169.254.0.0/16 link-local. Discovery is restricted to these. */
    fun isPrivateOrLinkLocal(addr: Long): Boolean {
        val a = (addr shr 24) and 255
        val b = (addr shr 16) and 255
        return a == 10L || (a == 172L && b in 16..31) || (a == 192L && b == 168L) || (a == 169L && b == 254L)
    }

    /** Suggested scan range for the phone's own address: its subnet, but never larger than /22 (clamped to /24). */
    fun suggestRange(ip: Long, prefix: Int): Ipv4Range? {
        if (!isPrivateOrLinkLocal(ip)) return null
        val p = if (prefix < MIN_DISCOVERY_PREFIX) 24 else prefix
        if (p > MAX_DISCOVERY_PREFIX) return null
        val mask = (0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL
        return Ipv4Range(ip and mask, p)
    }
}
