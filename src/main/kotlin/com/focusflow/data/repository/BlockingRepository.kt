package com.focusflow.data.repository

import com.focusflow.data.*
import com.focusflow.data.models.*

/**
 * BlockingRepository
 *
 * Single access point for all blocking-related data: block rules, schedules,
 * daily allowances, keywords, presets, network cutoff rules, and daily usage.
 * UI screens and services should use this instead of calling Database directly.
 */
object BlockingRepository {

    // ── Block Rules ───────────────────────────────────────────────────────────
    fun getBlockRules(): List<BlockRule>                 = Database.getBlockRules()
    fun getEnabledBlockProcesses(): Set<String>          = Database.getEnabledBlockProcesses()
    fun upsertBlockRule(rule: BlockRule)                 = Database.upsertBlockRule(rule)
    fun deleteBlockRule(id: String)                      = Database.deleteBlockRule(id)

    // ── Block Schedules ───────────────────────────────────────────────────────
    fun getBlockSchedules(): List<BlockSchedule>         = Database.getBlockSchedules()
    fun upsertBlockSchedule(s: BlockSchedule)            = Database.upsertBlockSchedule(s)
    fun deleteBlockSchedule(id: String)                  = Database.deleteBlockSchedule(id)

    // ── Daily Allowances ──────────────────────────────────────────────────────
    fun getDailyAllowances(): List<DailyAllowance>       = Database.getDailyAllowances()
    fun upsertDailyAllowance(a: DailyAllowance)          = Database.upsertDailyAllowance(a)
    fun deleteDailyAllowance(processName: String)        = Database.deleteDailyAllowance(processName)
    fun getDailyUsage(date: java.time.LocalDate)         = Database.getDailyUsage(date)
    fun upsertDailyUsage(date: java.time.LocalDate, processName: String, seconds: Long) =
        Database.upsertDailyUsage(date, processName, seconds)
    fun deleteDailyUsageBefore(date: java.time.LocalDate) = Database.deleteDailyUsageBefore(date)

    // ── Keywords ──────────────────────────────────────────────────────────────
    fun getBlockedKeywords(): List<String>               = Database.getBlockedKeywords()
    fun setBlockedKeywords(keywords: List<String>)       = Database.setBlockedKeywords(keywords)
    fun isKeywordBlockerEnabled(): Boolean               = Database.isKeywordBlockerEnabled()
    fun setKeywordBlockerEnabled(enabled: Boolean)       = Database.setKeywordBlockerEnabled(enabled)

    // ── Custom Presets ────────────────────────────────────────────────────────
    fun getCustomBlockPresets(): List<CustomBlockPreset>           = Database.getCustomBlockPresets()
    fun upsertCustomBlockPreset(preset: CustomBlockPreset)         = Database.upsertCustomBlockPreset(preset)
    fun deleteCustomBlockPreset(id: String)                        = Database.deleteCustomBlockPreset(id)

    // ── Network Cutoff Rules ──────────────────────────────────────────────────
    fun getNetworkCutoffRules(): List<NetworkCutoffRule>           = Database.getNetworkCutoffRules()
    fun getEnabledNetworkCutoffRules(): List<NetworkCutoffRule>    = Database.getEnabledNetworkCutoffRules()
    fun upsertNetworkCutoffRule(rule: NetworkCutoffRule)           = Database.upsertNetworkCutoffRule(rule)
    fun setNetworkCutoffRuleEnabled(id: String, enabled: Boolean)  = Database.setNetworkCutoffRuleEnabled(id, enabled)
    fun deleteNetworkCutoffRule(id: String)                        = Database.deleteNetworkCutoffRule(id)
}
