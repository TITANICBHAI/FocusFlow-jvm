package com.focusflow.enforcement

import com.focusflow.data.Database

/**
 * VpnBlocker
 *
 * Detects and kills known VPN processes when enforcement is active.
 * Prevents users from using a VPN to bypass Windows Firewall rules.
 *
 * Built-in list covers the most popular VPN clients. Users can also add
 * custom process names via addCustomProcess().
 */
object VpnBlocker {

    val KNOWN_VPN_PROCESSES: Set<String> = setOf(
        // NordVPN
        "nordvpn.exe", "nordvpn-service.exe",
        // ExpressVPN
        "expressvpn.exe", "expressvpnservice.exe", "expressvpnlauncher.exe",
        // ProtonVPN
        "protonvpn.exe", "protonvpn-service.exe",
        // Windscribe
        "windscribe.exe", "windscribeservice.exe",
        // CyberGhost
        "cyberghost.exe", "cyberghost64.exe", "cyberghostservice.exe",
        // Surfshark
        "surfshark.exe", "surfshark-service.exe",
        // Private Internet Access
        "pia.exe", "pia-service.exe",
        // Mullvad
        "mullvad.exe", "mullvad-daemon.exe",
        // IPVanish
        "ipvanish.exe",
        // HMA (HideMyAss)
        "hidemyass.exe", "hmavpn.exe",
        // TunnelBear
        "tunnelbear.exe",
        // Hotspot Shield
        "hotspotshield.exe", "hotspotshield-service.exe",
        // Avast SecureLine
        "avastsvpn.exe",
        // AVG Secure VPN
        "avgsecu.exe",
        // VyprVPN
        "vyprvpn.exe",
        // PrivateVPN
        "privatevpn.exe",
        // PureVPN
        "purevpn.exe",
        // ZenMate
        "zenmate.exe",
        // TorGuard
        "torguard.exe",
        // Lantern
        "lantern.exe",
        // Psiphon
        "psiphon3.exe",
        // UltraSurf
        "ultrasurf.exe",
        // OpenVPN (generic)
        "openvpn.exe", "openvpn-gui.exe",
        // WireGuard (generic + Windows service — most common install method)
        "wireguard.exe", "wireguard-service.exe", "wgservice.exe",
        // Cisco AnyConnect
        "anyconnect.exe", "vpnui.exe", "vpnagent.exe", "csclient.exe",
        // Cisco Umbrella (DNS-layer VPN/proxy)
        "umbrella.exe", "umbrellad.exe", "ciscoumbrella.exe",
        // Palo Alto GlobalProtect
        "globalprotect.exe", "pangpa.exe", "pangps.exe",
        // Fortinet FortiClient
        "fortisslvpn.exe", "forticlient.exe", "forticlientvpn.exe",
        // Pulse Secure / Ivanti
        "pulsesecure.exe", "pulsesvc.exe", "ivanti.exe",
        // Zscaler
        "zscalertunnel.exe", "zscaler.exe", "zscalerclient.exe", "zscalerservice.exe",
        // F5 VPN (BIG-IP Edge)
        "f5vpn.exe", "f5fpc.exe",
        // SoftEther
        "softethervpn.exe", "vpnclient.exe",
        // IVPN
        "ivpn.exe", "ivpnservice.exe",
        // SaferVPN / CactusVPN / other smaller clients
        "safervpn.exe", "cactusvpn.exe",
        // Windows built-in VPN dialer (rasphone / rasdial can create/connect VPN tunnels)
        "rasphone.exe", "rasdial.exe",
        // Tor
        "tor.exe"
    )

    // ── Hot-path caches — never hit the DB on the WinEventHook foreground callback ──

    /** Null = not loaded yet; set lazily on first read, cleared on write. */
    @Volatile private var _cachedEnabled: Boolean? = null

    /** Null = not loaded yet; updated atomically on add/remove. */
    @Volatile private var _cachedCustomProcesses: List<String>? = null

    var isEnabled: Boolean
        get() = _cachedEnabled ?: (Database.getSetting("vpn_block_enabled") == "true").also { _cachedEnabled = it }
        set(value) {
            _cachedEnabled = value
            Database.setSetting("vpn_block_enabled", if (value) "true" else "false")
        }

    fun getCustomProcesses(): List<String> {
        return _cachedCustomProcesses ?: run {
            val raw = Database.getSetting("vpn_custom_processes")
            // Always cache the result — even an empty list — so the DB is not re-read
            // on every isVpnProcess() call when no custom VPN processes are configured.
            val result = if (raw.isNullOrBlank()) emptyList()
                         else raw.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            _cachedCustomProcesses = result
            result
        }
    }

    fun addCustomProcess(processName: String) {
        val lower = processName.trim().lowercase().let {
            if (!it.endsWith(".exe")) "$it.exe" else it
        }
        val existing = getCustomProcesses().toMutableList()
        if (!existing.contains(lower)) {
            existing.add(lower)
            Database.setSetting("vpn_custom_processes", existing.joinToString(","))
            _cachedCustomProcesses = existing          // keep cache in sync
        }
    }

    fun removeCustomProcess(processName: String) {
        val lower = processName.trim().lowercase()
        val updated = getCustomProcesses().filter { it != lower }
        Database.setSetting("vpn_custom_processes", updated.joinToString(","))
        _cachedCustomProcesses = updated              // keep cache in sync
    }

    fun getAllBlockedProcesses(): Set<String> =
        KNOWN_VPN_PROCESSES + getCustomProcesses().toSet()

    fun isVpnProcess(processName: String): Boolean {
        if (!isEnabled) return false
        val lower = processName.lowercase()
        return KNOWN_VPN_PROCESSES.contains(lower) || getCustomProcesses().contains(lower)
    }
}
