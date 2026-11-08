pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.nexomc.com/releases")
        maven("https://repo.dmulloy2.net/repository/public/")
        maven("https://repo.opencollab.dev/main/")
    }
}

rootProject.name = "NexoBedrock"
include("geyser-extension")
