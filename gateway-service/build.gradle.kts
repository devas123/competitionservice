plugins {
    id("competitions-mgr.java-conventions")
    id("com.palantir.docker") version "0.29.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    scala
    application
}

val javaMainClass = "compman.compsrv.gateway.GatewayServiceMain"


application {
    mainClass.set(javaMainClass)
}


dependencies {
    implementation("org.scala-lang:scala-library:2.13.5")
    Libraries.cats.forEach { implementation(it) }
    Libraries.logging.forEach { implementation(it) }
    Libraries.zio.forEach { implementation(it) }
    Libraries.zioLogging.forEach { implementation(it) }
    Libraries.jackson.forEach { implementation(it) }
    Libraries.zioConfig.forEach { implementation(it) }
    Libraries.zioKafka.forEach { implementation(it) }
    Libraries.http4s.forEach { implementation(it) }
    implementation(project(":competition-serv-model"))
    implementation(project(":compservice-annotations"))
    implementation(project(":saga-processor:commons"))
    implementation(project(":actor-system"))

    Libraries.zioTest.apply {
        testImplementation(
            group = group,
            name = artifactId,
            version = version
        )
    }
    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.8")
    testImplementation("junit:junit:4.13")
    scalaCompilerPlugins("org.typelevel:kind-projector_2.13.5:0.13.2")
}
description = "gateway-service"


tasks.register("runMain", JavaExec::class) {
    val runFile: File = file("kafkarun")
    doFirst {
        runFile.createNewFile()
    }

    args(runFile.absolutePath)
    group = "gatewayService"
    this.isIgnoreExitValue = true
    classpath = sourceSets.main.get().runtimeClasspath + sourceSets.test.get().runtimeClasspath
    main = javaMainClass
    runFile.deleteOnExit()
    doLast {
        logger.lifecycle("Deleting file.")
        runFile.delete()
    }
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