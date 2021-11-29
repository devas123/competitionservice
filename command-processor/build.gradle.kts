plugins {
    id("competitions-mgr.java-conventions")
    id("com.palantir.docker") version "0.29.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    scala
    application
}

val javaMainClass = "compman.compsrv.CommandProcessorMain"


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
    Libraries.cats.forEach { implementation(it) }
    Libraries.guava.forEach { implementation(it) }
    Libraries.zio.forEach { implementation(it) }
    Libraries.zioLogging.forEach { implementation(it) }
    Libraries.zioKafka.forEach { implementation(it) }
    Libraries.logging.forEach { implementation(it) }
    Libraries.zioConfig.forEach { implementation(it) }
    Libraries.jackson.forEach { implementation(it) }
    Libraries.rocksdb.forEach { implementation(it) }
    Libraries.monocle.forEach { implementation(it) }
    implementation(project(":competition-serv-model"))
    implementation(project(":compservice-annotations"))
    implementation(project(":command-processor:commons"))
    implementation(project(":actor-system"))
    implementation(project(":kafka-common"))

    Libraries.embeddedKafka.forEach { testImplementation(it) }
    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.8")
    testImplementation("org.scalatestplus:junit-4-13_$scalaBinary:3.2.10.0")
    Libraries.zioTest.map {
        it.apply {
        testImplementation(
            group = group,
            name = artifactId,
            version = version
        )
    }
    }
}
description = "command processor"
