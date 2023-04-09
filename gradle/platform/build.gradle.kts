plugins {
    id("java-platform")
}

group = "dev.fwcd.kotlin-language-server"

javaPlatform {
    allowDependencies()
}

val kotlinVersion = "1.8.10"
val exposedVersion = "0.37.3"
val lsp4jVersion = "0.15.0"

// constrain the dependencies that we use to these specific versions
dependencies {
    constraints {
        api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
        api("org.hamcrest:hamcrest-all:1.3")
        api("junit:junit:4.11")
        api("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion")
        api("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:$lsp4jVersion")
        api("org.jetbrains.kotlin:kotlin-compiler:$kotlinVersion")
        api("org.jetbrains.kotlin:kotlin-scripting-compiler:$kotlinVersion")
        api("org.jetbrains.kotlin:kotlin-scripting-jvm-host-unshaded:$kotlinVersion")
        api("org.jetbrains.kotlin:kotlin-sam-with-receiver-compiler-plugin:$kotlinVersion")
        api("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        api("org.jetbrains.kotlin:kotlin-jvm:$kotlinVersion")
        api("org.jetbrains:fernflower:1.0")
        api("org.jetbrains.exposed:exposed-core:$exposedVersion")
        api("org.jetbrains.exposed:exposed-dao:$exposedVersion")
        api("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
        api("com.h2database:h2:1.4.200")
        api("com.github.fwcd.ktfmt:ktfmt:b5d31d1")
        api("com.beust:jcommander:1.78")
        api("org.hamcrest:hamcrest-all:1.3")
        api("junit:junit:4.11")
        api("org.openjdk.jmh:jmh-core:1.20")
        api("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
        api("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
        api("org.openjdk.jmh:jmh-generator-annprocess:1.20")
        api("org.xerial:sqlite-jdbc:3.41.2.1")
    }
}
