plugins {
    id("competitions-mgr.java-conventions")
    scala
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.5")
    implementation("dev.zio:zio-interop-cats_$scalaBinary:3.1.1.0")
    Libraries.cats.forEach { implementation(it) }
    Libraries.zio.forEach { implementation(it) }
    Libraries.zioConfig.forEach { implementation(it) }
    Libraries.circle.forEach { implementation(it) }
    Libraries.http4s.forEach { implementation(it) }
    implementation(project(":competition-serv-model"))
    implementation(project(":compservice-annotations"))

    Libraries.embeddedKafka.forEach { testImplementation(it) }
    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.8")
    testImplementation("junit:junit:4.13")
}
description = "query processor"
