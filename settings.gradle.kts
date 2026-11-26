pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// No foojay-resolver plugin here on purpose: F-Droid's source scanner rejects it (it
// fetches JDKs from a non-free API), and nothing in this project uses Gradle toolchains.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "notihop"
include(":notihop")
