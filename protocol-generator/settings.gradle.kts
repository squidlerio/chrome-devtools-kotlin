rootProject.name = "protocol-generator"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":cdp-kotlin-generator")
include(":cdp-json-parser")
