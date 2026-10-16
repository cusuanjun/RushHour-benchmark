package com.junj.metrics.parser

enum class CommandType {
    AM_START,
    CAT_PROC_MEMINFO,
    CAT_PROC_PRESSURE_MEMORY,
    DUMPSYS_THERMALSERVICE,
    DUMPSYS_GFXINFO,
    DUMPSYS_MEMINFO,
    SWAP_IN_STATS,
}

enum class ResultField {
    // AM_START
    LAUNCH_STATE,
    TOTAL_TIME,
    WAIT_TIME,
    STATUS,

    // CAT_PROC_MEMINFO
    MEM_TOTAL,
    MEM_FREE,
    MEM_AVAILABLE,
    SWAP_TOTAL,
    SWAP_FREE,
    SWAP_CACHED,

    // CAT_PROC_PRESSURE_MEMORY
    PSI_SOME_TOTAL,
    PSI_FULL_TOTAL,

    // DUMPSYS_THERMALSERVICE
    BIG_TEMP,
    MID_TEMP,
    LITTLE_TEMP,
    SOC_THERM_TEMP,
    VIRTUAL_SKIN_TEMP,

    // DUMPSYS_GFX_INFO
    JANKY_FRAMES_PCT,
    P50_RENDER_LAT,
    P90_RENDER_LAT,
    P95_RENDER_LAT,
    P99_RENDER_LAT,

    // DUMPSYS_MEMINFO
    TOTAL_PSS,

    // SWAP_IN_STATS
    TOTAL_SWAP_INS,
    SAMPLED_SWAP_INS,
    AVG_SWAP_IN_LAT,
    P90_SWAP_IN_LAT,
    P99_SWAP_IN_LAT,
    MAX_SWAP_IN_LAT,
}

data class RegexKey(
    val commandType: CommandType,
    val resultField: ResultField,
)

val commandRegexes = mapOf(
    // AM_START
    RegexKey(
        CommandType.AM_START,
        ResultField.LAUNCH_STATE
    ) to Regex("""LaunchState:\s(.+)"""),

    RegexKey(
        CommandType.AM_START,
        ResultField.TOTAL_TIME
    ) to Regex("""TotalTime:\s(.+)"""),

    RegexKey(
        CommandType.AM_START,
        ResultField.WAIT_TIME
    ) to Regex("""WaitTime:\s(.+)"""),

    RegexKey(
        CommandType.AM_START,
        ResultField.STATUS
    ) to Regex("""Status:\s(.+)"""),

    // CAT_PROC_MEMINFO
    RegexKey(
        CommandType.CAT_PROC_MEMINFO,
        ResultField.MEM_TOTAL
    ) to Regex("""MemTotal:\s*(\d+)\skB"""),

    RegexKey(
        CommandType.CAT_PROC_MEMINFO,
        ResultField.MEM_FREE
    ) to Regex("""MemFree:\s*(\d+)\skB"""),

    RegexKey(
        CommandType.CAT_PROC_MEMINFO,
        ResultField.MEM_AVAILABLE
    ) to Regex("""MemAvailable:\s*(\d+)\skB"""),

    RegexKey(
        CommandType.CAT_PROC_MEMINFO,
        ResultField.SWAP_TOTAL
    ) to Regex("""SwapTotal:\s*(\d+)\skB"""),

    RegexKey(
        CommandType.CAT_PROC_MEMINFO,
        ResultField.SWAP_FREE
    ) to Regex("""SwapFree:\s*(\d+)\skB"""),

    RegexKey(
        CommandType.CAT_PROC_MEMINFO,
        ResultField.SWAP_CACHED
    ) to Regex("""SwapCached:\s*(\d+)\skB"""),

    // CAT_PROC_PRESSURE_MEMORY
    RegexKey(
        CommandType.CAT_PROC_PRESSURE_MEMORY,
        ResultField.PSI_SOME_TOTAL
    ) to Regex("""some\s.*?total=(\d+)"""),

    RegexKey(
        CommandType.CAT_PROC_PRESSURE_MEMORY,
        ResultField.PSI_FULL_TOTAL
    ) to Regex("""full\s.*?total=(\d+)"""),

    // DUMPSYS_THERMALSERVICE
    RegexKey(
        CommandType.DUMPSYS_THERMALSERVICE,
        ResultField.BIG_TEMP
    ) to Regex("""Temperature\{mValue=(\d+(?:\.\d+)?),\smType=-?\d+,\smName=BIG,\smStatus=-?\d+\}"""),

    RegexKey(
        CommandType.DUMPSYS_THERMALSERVICE,
        ResultField.MID_TEMP
    ) to Regex("""Temperature\{mValue=(\d+(?:\.\d+)?),\smType=-?\d+,\smName=MID,\smStatus=-?\d+\}"""),

    RegexKey(
        CommandType.DUMPSYS_THERMALSERVICE,
        ResultField.LITTLE_TEMP
    ) to Regex("""Temperature\{mValue=(\d+(?:\.\d+)?),\smType=-?\d+,\smName=LITTLE,\smStatus=-?\d+\}"""),

    RegexKey(
        CommandType.DUMPSYS_THERMALSERVICE,
        ResultField.SOC_THERM_TEMP
    ) to Regex("""Temperature\{mValue=(\d+(?:\.\d+)?),\smType=-?\d+,\smName=soc_therm,\smStatus=-?\d+\}"""),

    RegexKey(
        CommandType.DUMPSYS_THERMALSERVICE,
        ResultField.VIRTUAL_SKIN_TEMP
    ) to Regex("""Temperature\{mValue=(\d+(?:\.\d+)?),\smType=-?\d+,\smName=VIRTUAL-SKIN,\smStatus=-?\d+\}"""),

    RegexKey(
        CommandType.DUMPSYS_GFXINFO,
        ResultField.JANKY_FRAMES_PCT
    ) to Regex("""Janky frames:\s+\d+\s+\((\d+(?:\.\d+)?)%\)"""),

    RegexKey(
        CommandType.DUMPSYS_GFXINFO,
        ResultField.P50_RENDER_LAT
    ) to Regex("""50th\spercentile:\s(\d+)ms"""),

    RegexKey(
        CommandType.DUMPSYS_GFXINFO,
        ResultField.P90_RENDER_LAT
    ) to Regex("""90th\spercentile:\s(\d+)ms"""),

    RegexKey(
        CommandType.DUMPSYS_GFXINFO,
        ResultField.P95_RENDER_LAT
    ) to Regex("""95th\spercentile:\s(\d+)ms"""),

    RegexKey(
        CommandType.DUMPSYS_GFXINFO,
        ResultField.P99_RENDER_LAT
    ) to Regex("""99th\spercentile:\s(\d+)ms"""),

    RegexKey(
        CommandType.DUMPSYS_MEMINFO,
        ResultField.TOTAL_PSS
    ) to Regex("""TOTAL\sPSS:\s*(\d+)"""),

    RegexKey(
        CommandType.SWAP_IN_STATS,
        ResultField.TOTAL_SWAP_INS
    ) to Regex("""total_swapins:\s*(\d+)"""),

    RegexKey(
        CommandType.SWAP_IN_STATS,
        ResultField.SAMPLED_SWAP_INS
    ) to Regex("""sampled_swapins:\s*(\d+)"""),

    RegexKey(
        CommandType.SWAP_IN_STATS,
        ResultField.AVG_SWAP_IN_LAT
    ) to Regex("""avg_ns:\s*(\d+)"""),

    RegexKey(
        CommandType.SWAP_IN_STATS,
        ResultField.P90_SWAP_IN_LAT
    ) to Regex("""p90_ns:\s*(\d+)"""),

    RegexKey(
        CommandType.SWAP_IN_STATS,
        ResultField.P99_SWAP_IN_LAT
    ) to Regex("""p99_ns:\s*(\d+)"""),

    RegexKey(
        CommandType.SWAP_IN_STATS,
        ResultField.MAX_SWAP_IN_LAT
    ) to Regex("""max_ns:\s*(\d+)"""),
)

fun regexFindField(commandType: CommandType, resultField: ResultField, output: String) : String? {
    var regexInput = output
    if (commandType == CommandType.DUMPSYS_THERMALSERVICE) {
        regexInput = Regex(
            """(?ms)^Current temperatures from HAL:\s*\r?\n(.*?)(?=^\S.*:\s*$|\z)"""
        ).find(output)?.groupValues?.get(1).toString()
    }
    val regexKey = RegexKey(commandType, resultField)
    val regex = commandRegexes[regexKey] ?: return null
    return regex
        .find(regexInput)
        ?.groupValues
        ?.getOrNull(1)
}
