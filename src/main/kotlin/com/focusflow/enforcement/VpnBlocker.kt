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
        // WireGuard (generic)
        "wireguard.exe",
        // Cisco AnyConnect
        "anyconnect.exe", "vpnui.exe", "vpnagent.exe",
        // Palo Alto GlobalProtect
        "globalprotect.exe", "pangpa.exe",
        // Fortinet FortiClient
        "fortisslvpn.exe", "forticlient.exe",
        // Pulse Secure
        "pulsesecure.exe", "pulsesvc.exe",
        // SoftEther
        "softethervpn.exe", "vpnclient.exe",
        // Tor
        "tor.exe"
    )

    var isEnabled: Boolean
        get() = Database.getSetting("vpn_block_enabled") == "true"
        set(value) = Database.setSetting("vpn_block_enabled", if (value) "true" else "false")

    fun getCustomProcesses(): List<String> {
        val raw = Database.getSetting("vpn_custom_processes") ?: return emptyList()
        return raw.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }

    fun addCustomProcess(processName: String) {
        val lower = processName.trim().lowercase().let {
            if (!it.endsWith(".exe")) "$it.exe" else it
        }
        val existing = getCustomProcesses().toMutableList()
        if (!existing.contains(lower)) {
            existing.add(lower)
            Database.setSetting("vpn_custom_processes", existing.joinToString(","))
        }
    }

    fun removeCustomProcess(processName: String) {
        val lower = processName.trim().lowercase()
        val updated = getCustomProcesses().filter { it != lower }
        Database.setSetting("vpn_custom_processes", updated.joinToString(","))
    }

    fun getAllBlockedProcesses(): Set<String> =
        KNOWN_VPN_PROCESSES + getCustomProcesses().toSet()

    fun isVpnProcess(processName: String): Boolean {
        if (!isEnabled) return false
        val lower = processName.lowercase()
        return KNOWN_VPN_PROCESSES.contains(lower) || getCustomProcesses().contains(lower)
    }
}
