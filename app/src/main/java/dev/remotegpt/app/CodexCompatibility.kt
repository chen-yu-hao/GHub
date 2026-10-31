package dev.remotegpt.app

/**
 * App Server versions are protocol inputs, not Android app release numbers.
 * Keep the minimum baseline here and let compatible newer 0.x releases pass
 * through the normal handshake and unknown-message handling.
 */
internal object CodexCompatibility {
    const val MINIMUM_VERSION = "0.164.0"
    val POLICY_LABEL get() = tr(R.string.ui_0_164_0_and_newer_0_x)

    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

        override fun toString(): String = "${major}.${minor}.${patch}"
    }

    private val versionPattern = Regex("(?<!\\d)(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+][0-9A-Za-z.-]+)?")
    private val minimum = Version(0, 164, 0)

    fun parse(value: String?): Version? {
        val match = value?.let(versionPattern::find) ?: return null
        return Version(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }

    fun isSupported(value: String?): Boolean {
        val version = parse(value) ?: return false
        return version.major == 0 && version >= minimum
    }
}
