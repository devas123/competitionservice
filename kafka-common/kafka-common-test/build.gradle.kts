plugins {
    id("competitions-mgr.java-conventions")
    scala
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.5")
    Libraries.cats.forEach { implementation(it) }
    Libraries.logging.forEach { implementation(it) }
    Libraries.zio.forEach { implementation(it) }
    Libraries.zioLogging.forEach { implementation(it) }
    Libraries.zioKafka.forEach { implementation(it) }
    Libraries.embeddedKafka.forEach { implementation(it) }

    Libraries.zioTest.apply {
        testImplementation(
            group = group,
            name = artifactId,
            version = version
        )
    }
    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.8")
}
description = "Common kafka test utils"