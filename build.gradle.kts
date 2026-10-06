plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.2.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("com.github.Lupino-1:LPLibrary:v1.0.6")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("dev.lupino1", "${project.group}.libs.lplibrary")


}



tasks.processResources {
    val props = mapOf("version" to version)
    filesMatching("plugin.yml") {
        expand(props)
    }
}


tasks.named("build") {
    dependsOn(tasks.shadowJar)
}
