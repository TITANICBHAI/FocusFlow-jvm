package com.focusflow.data

import com.focusflow.data.models.*
import java.time.LocalDate
import java.time.LocalDateTime

// ── Row mappers ───────────────────────────────────────────────────────────────

internal fun rowToBlockRule(rs: java.sql.ResultSet): BlockRule = BlockRule(
    id           = rs.getString("id"),
    processName  = rs.getString("process_name"),
    displayName  = rs.getString("display_name"),
    enabled      = rs.getInt("enabled") == 1,
    blockNetwork = rs.getInt("block_network") == 1
)

internal fun rowToSchedule(rs: java.sql.ResultSet): BlockSchedule = BlockSchedule(
    id           = rs.getString("id"),
    name         = rs.getString("name"),
    daysOfWeek   = rs.getString("days_of_week").split(",").mapNotNull { it.trim().toIntOrNull() },
    startHour    = rs.getInt("start_hour"),
    startMinute  = rs.getInt("start_minute"),
    endHour      = rs.getInt("end_hour"),
    endMinute    = rs.getInt("end_minute"),
    enabled      = rs.getInt("enabled") == 1,
    processNames = rs.getString("process_names")
                     ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
)

internal fun rowToNetworkCutoffRule(rs: java.sql.ResultSet): NetworkCutoffRule = NetworkCutoffRule(
    id                = rs.getString("id"),
    pattern           = rs.getString("pattern"),
    mode              = try { NetworkRuleMode.valueOf(rs.getString("mode")) }
                        catch (_: Exception) { NetworkRuleMode.DOMAIN },
    targetProcess     = rs.getString("target_process"),
    targetDisplayName = rs.getString("target_display_name"),
    enabled           = rs.getInt("enabled") == 1
)

internal fun rowToCustomBlockPreset(rs: java.sql.ResultSet): CustomBlockPreset = CustomBlockPreset(
    id           = rs.getString("id"),
    name         = rs.getString("name"),
    emoji        = rs.getString("emoji") ?: "🚫",
    processNames = rs.getString("process_names")
                     ?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    createdAt    = LocalDateTime.parse(rs.getString("created_at"), Database.dtFmt)
)

// ── Block Rules ───────────────────────────────────────────────────────────────

fun Database.getBlockRules(): List<BlockRule> = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT * FROM block_rules ORDER BY display_name"
    ).use { rs ->
        val list = mutableListOf<BlockRule>(); while (rs.next()) list.add(rowToBlockRule(rs)); list
    }
}

fun Database.getEnabledBlockProcesses(): Set<String> = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT process_name FROM block_rules WHERE enabled = 1"
    ).use { rs ->
        val set = mutableSetOf<String>()
        while (rs.next()) set.add(rs.getString("process_name").lowercase())
        set
    }
}

fun Database.upsertBlockRule(rule: BlockRule) = synchronized(this) {
    connection.prepareStatement("""
        INSERT OR REPLACE INTO block_rules (id, process_name, display_name, enabled, block_network)
        VALUES (?,?,?,?,?)
    """.trimIndent()).use { ps ->
        ps.setString(1, rule.id); ps.setString(2, rule.processName)
        ps.setString(3, rule.displayName); ps.setInt(4, if (rule.enabled) 1 else 0)
        ps.setInt(5, if (rule.blockNetwork) 1 else 0); ps.executeUpdate()
    }
}

fun Database.deleteBlockRule(id: String) = synchronized(this) {
    connection.prepareStatement("DELETE FROM block_rules WHERE id = ?").use { ps ->
        ps.setString(1, id); ps.executeUpdate()
    }
}

// ── Block Schedules ───────────────────────────────────────────────────────────

fun Database.getBlockSchedules(): List<BlockSchedule> = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT * FROM block_schedules ORDER BY name"
    ).use { rs ->
        val list = mutableListOf<BlockSchedule>()
        while (rs.next()) list.add(rowToSchedule(rs))
        list
    }
}

fun Database.upsertBlockSchedule(s: BlockSchedule) = synchronized(this) {
    connection.prepareStatement("""
        INSERT OR REPLACE INTO block_schedules
        (id, name, days_of_week, start_hour, start_minute, end_hour, end_minute, enabled, process_names)
        VALUES (?,?,?,?,?,?,?,?,?)
    """.trimIndent()).use { ps ->
        ps.setString(1, s.id); ps.setString(2, s.name)
        ps.setString(3, s.daysOfWeek.joinToString(","))
        ps.setInt(4, s.startHour); ps.setInt(5, s.startMinute)
        ps.setInt(6, s.endHour); ps.setInt(7, s.endMinute)
        ps.setInt(8, if (s.enabled) 1 else 0)
        ps.setString(9, s.processNames.joinToString(","))
        ps.executeUpdate()
    }
}

fun Database.deleteBlockSchedule(id: String) = synchronized(this) {
    connection.prepareStatement("DELETE FROM block_schedules WHERE id = ?").use { ps ->
        ps.setString(1, id); ps.executeUpdate()
    }
}

// ── Daily Allowances ──────────────────────────────────────────────────────────

fun Database.getDailyAllowances(): List<DailyAllowance> = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT * FROM daily_allowances ORDER BY display_name"
    ).use { rs ->
        val list = mutableListOf<DailyAllowance>()
        while (rs.next()) list.add(DailyAllowance(
            rs.getString("process_name"),
            rs.getString("display_name"),
            rs.getInt("allowance_minutes")
        ))
        list
    }
}

fun Database.upsertDailyAllowance(a: DailyAllowance) = synchronized(this) {
    connection.prepareStatement("""
        INSERT OR REPLACE INTO daily_allowances (process_name, display_name, allowance_minutes)
        VALUES (?,?,?)
    """.trimIndent()).use { ps ->
        ps.setString(1, a.processName); ps.setString(2, a.displayName)
        ps.setInt(3, a.allowanceMinutes); ps.executeUpdate()
    }
}

fun Database.deleteDailyAllowance(processName: String) = synchronized(this) {
    connection.prepareStatement("DELETE FROM daily_allowances WHERE process_name = ?").use { ps ->
        ps.setString(1, processName); ps.executeUpdate()
    }
}

// ── Daily Usage (persists allowance counters across reboots) ──────────────────

fun Database.getDailyUsage(date: LocalDate): Map<String, Long> = synchronized(this) {
    if (!isReady) return emptyMap()
    return connection.prepareStatement(
        "SELECT process_name, seconds_used FROM daily_usage WHERE date = ?"
    ).use { ps ->
        ps.setString(1, date.format(dateFmt))
        ps.executeQuery().use { rs ->
            val map = mutableMapOf<String, Long>()
            while (rs.next()) map[rs.getString("process_name")] = rs.getLong("seconds_used")
            map
        }
    }
}

fun Database.upsertDailyUsage(date: LocalDate, processName: String, seconds: Long) {
    synchronized(this) {
        if (!isReady) return@synchronized
        connection.prepareStatement("""
            INSERT OR REPLACE INTO daily_usage (date, process_name, seconds_used) VALUES (?, ?, ?)
        """.trimIndent()).use { ps ->
            ps.setString(1, date.format(dateFmt)); ps.setString(2, processName)
            ps.setLong(3, seconds); ps.executeUpdate()
        }
    }
}

fun Database.deleteDailyUsageBefore(date: LocalDate) {
    synchronized(this) {
        if (!isReady) return@synchronized
        connection.prepareStatement("DELETE FROM daily_usage WHERE date < ?").use { ps ->
            ps.setString(1, date.format(dateFmt)); ps.executeUpdate()
        }
    }
}

// ── Network Cutoff Rules ──────────────────────────────────────────────────────

fun Database.getNetworkCutoffRules(): List<NetworkCutoffRule> = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT * FROM network_cutoff_rules ORDER BY pattern"
    ).use { rs ->
        val list = mutableListOf<NetworkCutoffRule>()
        while (rs.next()) list.add(rowToNetworkCutoffRule(rs))
        list
    }
}

fun Database.getEnabledNetworkCutoffRules(): List<NetworkCutoffRule> = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT * FROM network_cutoff_rules WHERE enabled = 1"
    ).use { rs ->
        val list = mutableListOf<NetworkCutoffRule>()
        while (rs.next()) list.add(rowToNetworkCutoffRule(rs))
        list
    }
}

fun Database.upsertNetworkCutoffRule(rule: NetworkCutoffRule) = synchronized(this) {
    connection.prepareStatement("""
        INSERT OR REPLACE INTO network_cutoff_rules
        (id, pattern, mode, target_process, target_display_name, enabled)
        VALUES (?,?,?,?,?,?)
    """.trimIndent()).use { ps ->
        ps.setString(1, rule.id); ps.setString(2, rule.pattern)
        ps.setString(3, rule.mode.name); ps.setString(4, rule.targetProcess)
        ps.setString(5, rule.targetDisplayName)
        ps.setInt(6, if (rule.enabled) 1 else 0); ps.executeUpdate()
    }
}

fun Database.setNetworkCutoffRuleEnabled(id: String, enabled: Boolean) = synchronized(this) {
    connection.prepareStatement(
        "UPDATE network_cutoff_rules SET enabled = ? WHERE id = ?"
    ).use { ps -> ps.setInt(1, if (enabled) 1 else 0); ps.setString(2, id); ps.executeUpdate() }
}

fun Database.deleteNetworkCutoffRule(id: String) = synchronized(this) {
    connection.prepareStatement("DELETE FROM network_cutoff_rules WHERE id = ?").use { ps ->
        ps.setString(1, id); ps.executeUpdate()
    }
}

// ── Custom Block Presets ──────────────────────────────────────────────────────

fun Database.getCustomBlockPresets(): List<CustomBlockPreset> = synchronized(this) {
    return connection.createStatement().executeQuery(
        "SELECT * FROM custom_block_presets ORDER BY created_at DESC"
    ).use { rs ->
        val list = mutableListOf<CustomBlockPreset>()
        while (rs.next()) list.add(rowToCustomBlockPreset(rs))
        list
    }
}

fun Database.upsertCustomBlockPreset(preset: CustomBlockPreset) = synchronized(this) {
    connection.prepareStatement("""
        INSERT OR REPLACE INTO custom_block_presets
        (id, name, emoji, process_names, created_at)
        VALUES (?,?,?,?,?)
    """.trimIndent()).use { ps ->
        ps.setString(1, preset.id); ps.setString(2, preset.name)
        ps.setString(3, preset.emoji)
        ps.setString(4, preset.processNames.joinToString(","))
        ps.setString(5, preset.createdAt.format(Database.dtFmt))
        ps.executeUpdate()
    }
}

fun Database.deleteCustomBlockPreset(id: String) = synchronized(this) {
    connection.prepareStatement("DELETE FROM custom_block_presets WHERE id = ?").use { ps ->
        ps.setString(1, id); ps.executeUpdate()
    }
}
