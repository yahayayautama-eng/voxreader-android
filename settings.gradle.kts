pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // HMS In-App Purchases for the huawei flavor.
    maven("https://developer.huawei.com/repo/") {
      content { includeGroupByRegex("com\\.huawei.*") }
    }
  }
}

rootProject.name = "Vox Reader"

include(":app")
include(":offlinevoices")
