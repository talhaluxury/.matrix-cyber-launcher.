package com.matrix.launcher.core

import com.matrix.launcher.net.NetValidation

sealed class TerminalAction {
    object None : TerminalAction()
    object Clear : TerminalAction()
    object OpenSettings : TerminalAction()
    object OpenRadar : TerminalAction()
    object Scan : TerminalAction()
    object OpenShow : TerminalAction()
    object OpenGlobe : TerminalAction()
    data class SetTheme(val id: String) : TerminalAction()
    data class Launch(val key: String) : TerminalAction()
    /** Network toolkit request. Active tests are only started after the user confirms authorization. */
    data class Net(val request: NetRequest) : TerminalAction()
}

sealed class NetRequest {
    object Status : NetRequest()
    /** [range] is a CIDR text, or null to use the phone's own subnet. */
    data class Discover(val range: String?) : NetRequest()
    object Diagnostics : NetRequest()
    data class Dns(val host: String) : NetRequest()
    data class PortCheck(val host: String, val ports: List<Int>) : NetRequest()
    object ScanStatus : NetRequest()
    object ScanCancel : NetRequest()
    object Report : NetRequest()
}

data class TerminalResult(val lines: List<String>, val action: TerminalAction = TerminalAction.None)

interface TerminalEnv {
    val apps: List<AppRef>
    fun time(): String
    fun date(): String
    fun battery(): String
    fun status(): List<String>
    fun storage(): String = "STORAGE: UNAVAILABLE"
    fun network(): String = "NETWORK: UNAVAILABLE"
    fun currentTheme(): String = "MATRIX_GREEN"
}

/**
 * Safe command parser. It only ever returns text and a small set of launcher actions.
 * There is no shell behind these commands. The only network activity is the opt-in network toolkit
 * (NETWORK/DNS/PORTCHECK), which the app asks the user to confirm before any active test starts.
 */
object CommandParser {
    const val MAX_INPUT = 200

    val COMMANDS = listOf("HELP", "APPS", "SEARCH", "OPEN", "TIME", "DATE", "BATTERY", "STORAGE", "NETWORK", "STATUS", "RADAR", "SCAN", "THEME", "GLOBE", "SHOW", "SETTINGS", "CLEAR", "DNS", "PORTCHECK", "REPORT")

    /** Persisted ids of the built-in themes (must match ui.theme.ThemeId names). */
    val THEMES = listOf("MATRIX_GREEN", "STEALTH_BLACK", "CYAN_CYBER", "RED_ALERT", "MINIMAL_HUD")

    private fun matchTheme(arg: String): String? {
        val n = arg.trim().uppercase().replace('_', ' ').replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return null
        val hits = THEMES.filter { it.replace('_', ' ').startsWith(n) || it.replace('_', ' ').split(' ').first() == n }
        return hits.singleOrNull()
    }

    fun autocomplete(partial: String): List<String> {
        val p = partial.trim().uppercase()
        if (p.isEmpty() || p.contains(' ')) return emptyList()
        return COMMANDS.filter { it.startsWith(p) && it != p }
    }

    fun execute(raw: String, env: TerminalEnv): TerminalResult {
        val input = raw.filter { !it.isISOControl() }.trim().take(MAX_INPUT)
        if (input.isEmpty()) return TerminalResult(emptyList())
        val parts = input.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].uppercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()
        return when (cmd) {
            "HELP" -> TerminalResult(HELP)
            "APPS" -> {
                val labels = env.apps.map { it.label }
                val shown = labels.take(40)
                val lines = ArrayList<String>()
                lines.add("${labels.size} LAUNCHABLE APPS:")
                shown.forEach { lines.add("  $it") }
                if (labels.size > shown.size) lines.add("  ... +${labels.size - shown.size} MORE (USE SEARCH <TEXT>)")
                TerminalResult(lines)
            }
            "SEARCH" -> {
                if (arg.isEmpty()) TerminalResult(listOf("USAGE: SEARCH <TEXT>"))
                else {
                    val hits = env.apps.filter { it.label.contains(arg, ignoreCase = true) }
                    if (hits.isEmpty()) TerminalResult(listOf("NO APPS MATCH \"$arg\""))
                    else TerminalResult(listOf("${hits.size} MATCH(ES):") + hits.take(30).map { "  ${it.label}" })
                }
            }
            "OPEN" -> {
                if (arg.isEmpty()) TerminalResult(listOf("USAGE: OPEN <APP NAME>"))
                else when (val m = AppMatcher.match(arg, env.apps)) {
                    is MatchResult.None -> TerminalResult(listOf("NO APP MATCHES \"$arg\""))
                    is MatchResult.Single -> TerminalResult(listOf("LAUNCHING ${m.app.label}"), TerminalAction.Launch(m.app.key))
                    is MatchResult.Multiple -> TerminalResult(
                        listOf("AMBIGUOUS \"$arg\" - BE MORE SPECIFIC:") + m.apps.take(8).map { "  ${it.label}" }
                    )
                }
            }
            "TIME" -> TerminalResult(listOf("TIME: ${env.time()}"))
            "DATE" -> TerminalResult(listOf("DATE: ${env.date()}"))
            "BATTERY" -> TerminalResult(listOf(env.battery()))
            "STORAGE" -> TerminalResult(listOf(env.storage()))
            "NETWORK" -> networkCommand(arg, env)
            "SCAN" -> scanCommand(arg)
            "DNS" -> dnsCommand(arg)
            "PORTCHECK" -> portCheckCommand(arg)
            "REPORT" -> TerminalResult(emptyList(), TerminalAction.Net(NetRequest.Report))
            "THEME" -> {
                if (arg.isEmpty()) {
                    TerminalResult(
                        listOf("CURRENT THEME: ${env.currentTheme().replace('_', ' ')}", "USAGE: THEME <NAME>  OPTIONS:") +
                            THEMES.map { "  ${it.replace('_', ' ')}" }
                    )
                } else {
                    val id = matchTheme(arg)
                    if (id == null) TerminalResult(listOf("UNKNOWN OR AMBIGUOUS THEME \"$arg\" (TYPE THEME)"))
                    else TerminalResult(listOf("THEME SET: ${id.replace('_', ' ')}"), TerminalAction.SetTheme(id))
                }
            }
            "GLOBE" -> TerminalResult(listOf("SWITCHING TO CYBER GLOBE (SIMULATED DATA)"), TerminalAction.OpenGlobe)
            "SHOW" -> TerminalResult(listOf("STARTING SHOW MODE - VISUAL DEMONSTRATION ONLY"), TerminalAction.OpenShow)
            "STATUS" -> TerminalResult(env.status())
            "RADAR" -> TerminalResult(listOf("OPENING RADAR (VISUAL SIMULATION - NO REAL SCAN)"), TerminalAction.OpenRadar)
            "SETTINGS" -> TerminalResult(listOf("OPENING LAUNCHER SETTINGS"), TerminalAction.OpenSettings)
            "CLEAR" -> TerminalResult(emptyList(), TerminalAction.Clear)
            else -> TerminalResult(listOf("UNKNOWN COMMAND: $cmd  (TYPE HELP)"))
        }
    }

    private fun networkCommand(arg: String, env: TerminalEnv): TerminalResult {
        if (arg.isEmpty()) {
            return TerminalResult(
                listOf(
                    env.network(),
                    "NOTE: CONNECTION TYPE ONLY. NO TRAFFIC IS INSPECTED.",
                    "MORE: NETWORK STATUS | NETWORK DISCOVER [CIDR] | NETWORK DIAGNOSTICS",
                ),
            )
        }
        val tokens = arg.split(Regex("\\s+"))
        return when (tokens[0].uppercase()) {
            "STATUS" -> TerminalResult(emptyList(), TerminalAction.Net(NetRequest.Status))
            "DISCOVER" -> {
                if (tokens.size > 2) return TerminalResult(listOf("USAGE: NETWORK DISCOVER [CIDR]  (EXAMPLE: NETWORK DISCOVER 192.168.1.0/24)"))
                if (tokens.size == 2) {
                    if (NetValidation.parseCidr(tokens[1]) == null) {
                        return TerminalResult(listOf("INVALID RANGE \"${tokens[1].take(40)}\". USE CIDR /22../30, EXAMPLE: 192.168.1.0/24"))
                    }
                    TerminalResult(emptyList(), TerminalAction.Net(NetRequest.Discover(tokens[1])))
                } else {
                    TerminalResult(emptyList(), TerminalAction.Net(NetRequest.Discover(null)))
                }
            }
            "DIAGNOSTICS", "DIAG" -> TerminalResult(emptyList(), TerminalAction.Net(NetRequest.Diagnostics))
            else -> TerminalResult(listOf("USAGE: NETWORK [STATUS | DISCOVER [CIDR] | DIAGNOSTICS]"))
        }
    }

    private fun scanCommand(arg: String): TerminalResult {
        return when (arg.uppercase()) {
            "" -> TerminalResult(
                listOf("SCAN: VISUAL ANIMATION (SIMULATED) + REAL LOCAL TELEMETRY REFRESH", "NO NETWORK OR DEVICE IS PROBED."),
                TerminalAction.Scan,
            )
            "STATUS" -> TerminalResult(emptyList(), TerminalAction.Net(NetRequest.ScanStatus))
            "CANCEL" -> TerminalResult(emptyList(), TerminalAction.Net(NetRequest.ScanCancel))
            else -> TerminalResult(listOf("USAGE: SCAN | SCAN STATUS | SCAN CANCEL"))
        }
    }

    private fun dnsCommand(arg: String): TerminalResult {
        if (arg.isEmpty()) return TerminalResult(listOf("TARGET REQUIRED.", "USAGE: DNS <HOSTNAME>  (EXAMPLE: DNS example.com)"))
        if (arg.contains(' ') || !NetValidation.isValidHostname(arg)) {
            return TerminalResult(listOf("INVALID HOSTNAME \"${arg.take(60)}\".", "USAGE: DNS <HOSTNAME>"))
        }
        return TerminalResult(emptyList(), TerminalAction.Net(NetRequest.Dns(arg)))
    }

    private fun portCheckCommand(arg: String): TerminalResult {
        val usage = "USAGE: PORTCHECK <HOST> <PORT[,PORT|A-B...]>  (EXAMPLE: PORTCHECK 192.168.1.1 80,443)"
        if (arg.isEmpty()) return TerminalResult(listOf("TARGET REQUIRED.", usage))
        val tokens = arg.split(Regex("\\s+"), limit = 2)
        val host = tokens[0]
        if (!NetValidation.isValidTarget(host)) return TerminalResult(listOf("INVALID TARGET \"${host.take(60)}\".", usage))
        if (tokens.size < 2) return TerminalResult(listOf("PORT REQUIRED.", usage))
        val parsed = NetValidation.parsePortList(tokens[1])
        if (parsed.error != null) return TerminalResult(listOf(parsed.error, usage))
        return TerminalResult(emptyList(), TerminalAction.Net(NetRequest.PortCheck(host, parsed.ports)))
    }

    private val HELP = listOf(
        "COMMANDS:",
        "  HELP            SHOW THIS LIST",
        "  APPS            LIST LAUNCHABLE APPS",
        "  SEARCH <TEXT>   FILTER INSTALLED APPS",
        "  OPEN <APP>      LAUNCH A MATCHING APP",
        "  TIME / DATE     DEVICE TIME AND DATE",
        "  BATTERY         BATTERY LEVEL",
        "  STORAGE         INTERNAL STORAGE FREE/TOTAL",
        "  NETWORK         CONNECTION TYPE (NO TRAFFIC DATA)",
        "  NETWORK STATUS  IP, GATEWAY, DNS SERVERS (LOCAL INFO)",
        "  NETWORK DISCOVER [CIDR]  FIND REACHABLE LAN DEVICES",
        "  NETWORK DIAGNOSTICS      DNS, LATENCY, INTERNET CHECKS",
        "  DNS <HOST>      RESOLVE A NAME AND TIME THE LOOKUP",
        "  PORTCHECK <HOST> <PORTS> TCP PORT REACHABILITY (YOUR HOSTS ONLY)",
        "  SCAN STATUS     PROGRESS OF A RUNNING NETWORK TASK",
        "  SCAN CANCEL     STOP A RUNNING NETWORK TASK",
        "  REPORT          SAVE LAST RESULTS TO A LOCAL FILE",
        "  STATUS          LOCAL LAUNCHER STATUS",
        "  SCAN            VISUAL SCAN + REAL TELEMETRY REFRESH",
        "  THEME [NAME]    LIST OR SWITCH THEMES",
        "  RADAR           SIMULATED RADAR (VISUAL ONLY)",
        "  GLOBE           SIMULATED CYBER GLOBE PANEL",
        "  SHOW            VISUAL DEMO (NO REAL OPERATIONS)",
        "  SETTINGS        LAUNCHER SETTINGS",
        "  CLEAR           CLEAR OUTPUT",
        "NOTE: THIS IS NOT A SHELL. NO SYSTEM COMMANDS RUN.",
        "NETWORK TESTS ASK FOR YOUR CONFIRMATION FIRST. TEST ONLY NETWORKS/HOSTS YOU OWN OR MAY TEST.",
    )
}
