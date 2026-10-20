plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.2.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.2")
    implementation("com.github.Lupino-1:LPLibrary:v1.0.6")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")
    implementation("net.megavex:scoreboard-library-api:2.7.4")
    implementation("net.megavex:scoreboard-library-implementation:2.7.4")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.isFork = false
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    named<Jar>("jar") {
        archiveClassifier.set("thin")
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        relocate("dev.lupino1", "${project.group}.libs.lplibrary")
        relocate("com.zaxxer.hikari", "${project.group}.libs.hikari")
        relocate("org.mariadb.jdbc", "${project.group}.libs.mariadb")
        relocate("net.megavex.scoreboardlibrary", "${project.group}.libs.scoreboardlibrary")
    }

    // Folia plugins/ has locked DBs while server runs — don't fingerprint destination
    val copyToFolia = register<Copy>("copyToFolia") {
        group = "distribution"
        description = "Copy plugin jar into Folia server plugins/"
        val jarTask = named<Jar>("shadowJar")
        dependsOn(jarTask)
        from(jarTask.flatMap { it.archiveFile })
        into("C:/Users/Lukáš/Desktop/folia server/plugins")
        doNotTrackState("Folia plugins/ contains locked runtime DBs")
    }

    named<Jar>("shadowJar") {
        finalizedBy(copyToFolia)
    }

    named("build") {
        dependsOn(named("shadowJar"))
        dependsOn(copyToFolia)
    }
}
