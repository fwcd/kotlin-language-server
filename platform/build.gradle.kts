plugins { id("java-platform") }

javaPlatform { allowDependencies() }

dependencies {
    constraints {
        api(libs.org.jetbrains.kotlin.stdlib)
        api(libs.hamcrest.all)
        api(libs.junit.junit)
        api(libs.org.eclipse.lsp4j.lsp4j)
        api(libs.org.eclipse.lsp4j.jsonrpc)
        api(libs.org.jetbrains.kotlin.compiler)
        api(libs.org.jetbrains.kotlin.ktscompiler)
        api(libs.org.jetbrains.kotlin.kts.jvm.host.unshaded)
        api(libs.org.jetbrains.kotlin.sam.with.receiver.compiler.plugin)
        api(libs.org.jetbrains.kotlin.reflect)
        api(libs.org.jetbrains.kotlin.jvm)
        api(libs.org.jetbrains.fernflower)
        api(libs.org.jetbrains.exposed.core)
        api(libs.org.jetbrains.exposed.dao)
        api(libs.org.jetbrains.exposed.jdbc)
        api(libs.com.h2database.h2)
        api(libs.com.github.fwcd.ktfmt)
        api(libs.com.beust.jcommander)
        api(libs.org.openjdk.jmh.core)
        api(libs.org.jetbrains.kotlin.kotlin.scripting.jvm.host)
        api(libs.org.openjdk.jmh.generator.annprocess)
        api(libs.org.xerial.sqlite.jdbc)
    }
}
