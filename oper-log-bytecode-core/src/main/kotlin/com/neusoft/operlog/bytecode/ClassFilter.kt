package com.neusoft.operlog.bytecode

/**
 * Filter to determine whether a class should be processed by OperLog with exact package boundary checking.
 */
object ClassFilter {

    private val SYSTEM_PREFIXES = listOf(
        "android/",
        "androidx/",
        "java/",
        "javax/",
        "kotlin/",
        "kotlinx/",
        "dagger/",
        "hilt/",
        "com/google/",
        "okhttp3/",
        "okio/",
        "retrofit2/",
        "com/bumptech/glide/",
        "io/reactivex/",
        "org/apache/"
    )

    fun isTargetClass(className: String, config: OperLogConfigParams): Boolean {
        if (!config.enabled) return false

        // Normalize slashed class name (e.g. com/example/Foo -> com.example.Foo)
        val dotName = className.replace('/', '.')
        val slashName = className.replace('.', '/')

        // Exclude system / generated framework packages
        for (prefix in SYSTEM_PREFIXES) {
            if (slashName.startsWith(prefix)) {
                return false
            }
        }

        val simpleName = dotName.substringAfterLast('.')

        // Exclude generated classes
        if (simpleName == "R" ||
            simpleName.startsWith("R$") ||
            simpleName == "BuildConfig" ||
            simpleName.endsWith("Binding") ||
            simpleName.endsWith("_Factory") ||
            simpleName.endsWith("_Impl") ||
            simpleName.startsWith("Hilt_") ||
            simpleName.startsWith("Dagger")
        ) {
            return false
        }

        // Exclude packages specified in excludePackages (exact package boundary matching)
        for (exclude in config.excludePackages) {
            if (exclude.isNotEmpty() && (dotName == exclude || dotName.startsWith("$exclude."))) {
                return false
            }
        }

        // If includePackages specified, must match at least one (exact package boundary matching)
        if (config.includePackages.isNotEmpty()) {
            var matched = false
            for (include in config.includePackages) {
                if (include.isNotEmpty() && (dotName == include || dotName.startsWith("$include."))) {
                    matched = true
                    break
                }
            }
            if (!matched) return false
        }

        return true
    }
}
