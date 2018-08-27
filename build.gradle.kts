import org.apache.tools.ant.taskdefs.condition.Os.*
import java.nio.file.Paths

val kotlinVersion = "1.2.30"

plugins {
    kotlin("jvm").version("1.2.30")
    id("com.atlassian.performance.tools.gradle-release").version("0.0.2")
}

dependencies {
    listOf(
        "org.jetbrains.kotlin:kotlin-stdlib-jre8:$kotlinVersion",
        "com.atlassian.performance.tools:workspace:[1.1.0,2.0.0)",
        "com.atlassian.performance.tools:report:[1.0.0,2.0.0)",
        "com.atlassian.performance.tools:aws-resources:0.0.1",
        "com.atlassian.performance.tools:infrastructure:[1.0.0,2.0.0)",
        "com.atlassian.performance.tools:aws-infrastructure:0.0.2",
        "com.atlassian.performance.tools:jira-software-actions:0.1.1",
        "com.atlassian.performance.tools:virtual-users:0.0.3"
    ).plus(
        log4jCore()
    ).forEach { compile(it) }
}

fun log4jCore(): List<String> = log4j(
    "api",
    "core",
    "slf4j-impl"
)

fun log4j(
    vararg modules: String
): List<String> = modules.map { module ->
    "org.apache.logging.log4j:log4j-$module:2.10.0"
}

task<Exec>("testRefApp") {
    dependsOn("publishToMavenLocal")
    workingDir(Paths.get("examples", "ref-app"))
    executable(Paths.get(workingDir.toString(), if (isFamily(FAMILY_WINDOWS)) "mvnw.cmd" else "mvnw"))
    environment("MAVEN_OPTS", "-Djansi.force=true")
    args("install", "-Djpt.version=$version", "-U")
}

task<Wrapper>("wrapper") {
    gradleVersion = "4.9"
    distributionType = Wrapper.DistributionType.ALL
}