plugins {
    id("competitions-mgr.java-conventions")
    scala
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.5")
    implementation("dev.zio:zio-interop-cats_$scalaBinary:3.1.1.0")
    Libraries.cats.forEach { implementation(it) }
    Libraries.logging.forEach { implementation(it) }
    Libraries.zio.forEach { implementation(it) }
    Libraries.zioLogging.forEach { implementation(it) }
    Libraries.jackson.forEach { implementation(it) }
    Libraries.zioConfig.forEach { implementation(it) }
    Libraries.http4s.forEach { implementation(it) }
    Libraries.quill.forEach { implementation(it) }
    implementation(project(":competition-serv-model"))
    implementation(project(":compservice-annotations"))
    implementation(project(":saga-processor:commons"))

    Libraries.embeddedKafka.forEach { testImplementation(it) }
    Libraries.zioTest.apply {
        testImplementation(
            group = group,
            name = artifactId,
            version = version
        )
    }
    Libraries.embeddedCassandra.forEach { testImplementation(it) }
    testImplementation("com.datastax.oss:java-driver-core:4.13.0")
    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.8")
    testImplementation("junit:junit:4.13")
    scalaCompilerPlugins("org.typelevel:kind-projector_2.13.5:0.13.2")
}
description = "query processor"

val javaMainClass = "compman.compsrv.query.QueryServiceMain"

tasks.register("runMain", JavaExec::class) {
    val runFile: File = file("kafkarun")
    doFirst {
        runFile.createNewFile()
    }

    args(runFile.absolutePath)
    group = "queryService"
    this.isIgnoreExitValue = true
    classpath = sourceSets.main.get().runtimeClasspath + sourceSets.test.get().runtimeClasspath
    main = javaMainClass
    runFile.deleteOnExit()
    doLast {
        logger.lifecycle("Deleting file.")
        runFile.delete()
    }
}