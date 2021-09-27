plugins {
    id("competitions-mgr.java-conventions")
    id("com.palantir.docker") version "0.29.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    scala
    application
}

val javaMainClass = "compman.compsrv.Main"


application {
    mainClass.set(javaMainClass)
}

tasks.jar {
    manifest {
        attributes("MainClass" to javaMainClass)
    }
}

tasks.shadowJar {
    archiveBaseName.set("app")
    archiveClassifier.set("")
    archiveVersion.set("")
}

configure<com.palantir.gradle.docker.DockerExtension> {
    this.setDockerfile(file("Dockerfile"))
    name = project.name
    files(tasks.shadowJar.get().outputs)
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.5")
    implementation("dev.zio:zio-interop-cats_$scalaBinary:3.1.1.0")
    Libraries.cats.forEach { implementation(it) }
    Libraries.guava.forEach { implementation(it) }
    Libraries.zio.forEach { implementation(it) }
    Libraries.zioLogging.forEach { implementation(it) }
    Libraries.logging.forEach { implementation(it) }
    Libraries.zioConfig.forEach { implementation(it) }
    Libraries.jackson.forEach { implementation(it) }
    Libraries.rocksdb.forEach { implementation(it) }
    implementation(project(":competition-serv-model"))
    implementation(project(":compservice-annotations"))
    implementation(project(":saga-processor:commons"))

    Libraries.embeddedKafka.forEach { testImplementation(it) }
    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.8")
    testImplementation("junit:junit:4.13")
    Libraries.zioTest.apply {
        testImplementation(
            group = group,
            name = artifactId,
            version = version
        )
    }
}
description = "saga processor"
