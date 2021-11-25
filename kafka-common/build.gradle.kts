plugins {
    id("competitions-mgr.java-conventions")
    id("com.github.maiflai.scalatest") version "0.31"
    scala
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
    Libraries.embeddedKafka.forEach { testImplementation(it) }
    implementation(project(":actor-system"))
    implementation(project(":command-processor:commons"))

    Libraries.zioTest.apply {
        testImplementation(
            group = group,
            name = artifactId,
            version = version
        )
    }

    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.8")
    testRuntimeOnly("com.vladsch.flexmark:flexmark-all:0.35.10")
    scalaCompilerPlugins("org.typelevel:kind-projector_2.13.5:0.13.2")
}
description = "Common kafka actors"