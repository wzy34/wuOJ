plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "dev.wuoj"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    implementation("tools.jackson.dataformat:jackson-dataformat-csv")
    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport)
    runtimeOnly("org.postgresql:postgresql")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    testImplementation(libs.openapi.validator.mockmvc)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
}

/** The docker CLI: on PATH, or where Docker Desktop installs it for the current user. */
val dockerCli: String = listOf(
    System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "docker") },
    listOf(File(System.getProperty("user.home"), ".docker/bin/docker"), File("/usr/local/bin/docker")),
).flatten().firstOrNull { it.canExecute() }?.path ?: "docker"

val judgeImage = providers.gradleProperty("judgeImage").orElse("wuoj/judge-cpp:dev")

val buildJudgeImage = tasks.register<Exec>("buildJudgeImage") {
    group = "judge"
    description = "Builds the C++ judge image used by the Docker sandbox."
    workingDir = rootDir.resolve("../judge/cpp")
    commandLine(dockerCli, "build", "--tag", judgeImage.get(), ".")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.test {
    useJUnitPlatform { excludeTags("sandbox") }
    // The contract coverage check is only meaningful when every API test runs, i.e. without --tests.
    val filtered = gradle.startParameter.taskRequests.any { "--tests" in it.args }
    if (!filtered) systemProperty("oj.contractCoverage", "true")
}

val sandboxTest = tasks.register<Test>("sandboxTest") {
    group = "verification"
    description = "Runs the tests that compile and run code in real judge containers."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("sandbox") }
    systemProperty("oj.judge.image", judgeImage.get())
    dependsOn(buildJudgeImage)
    shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(sandboxTest) }

/** Runs one importer command against the database started by Docker Compose support. */
fun registerImport(name: String, vararg commandArgs: String) = tasks.register<JavaExec>(name) {
    group = "dev data"
    classpath = sourceSets.main.get().runtimeClasspath + configurations.developmentOnly.get()
    mainClass = "dev.wuoj.WuojApplication"
    workingDir = rootDir
    args(*commandArgs)
}

val importDevUsers = registerImport("importDevUsers", "import-users", "../dev-data/users.csv")
val importDevProblems = registerImport("importDevProblems", "import-problems", "../dev-data/problems")
importDevProblems { mustRunAfter(importDevUsers) }

tasks.register("importDevData") {
    group = "dev data"
    description = "Imports the demo users and sample problems from dev-data/."
    dependsOn(importDevUsers, importDevProblems)
}
