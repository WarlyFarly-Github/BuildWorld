plugins {
    java
}

group = "org.example"
version = "1.0.15-Beta_Release"

val paperApiVersion = "1.20.4-R0.1-SNAPSHOT"
val worldEditVersion = "7.2.13"
val worldGuardVersion = "7.0.10"

repositories {
    // Maven resolves Central implicitly; Gradle needs it declared
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc-repo" }
    maven("https://maven.enginehub.org/repo/") { name = "enginehub" }
    maven("https://jitpack.io") { name = "jitpack.io" }
}

dependencies {
    // Paper API (Bukkit). compileOnly == Maven "provided": the server supplies it at runtime
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // WorldEdit Bukkit adapter
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:$worldEditVersion")

    // WorldGuard Bukkit adapter
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:$worldGuardVersion")

    // WorldGuard core (exposes protection.regions, managers, etc.)
    compileOnly("com.sk89q.worldguard:worldguard-core:$worldGuardVersion")

    // SQLite JDBC. The Maven build never shaded this into the jar either, and the code
    // only touches java.sql, so Paper's bundled driver is what's used at runtime.
    compileOnly("org.xerial:sqlite-jdbc:3.41.2.1")

    // LuckPerms API
    compileOnly("net.luckperms:api:5.5")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
