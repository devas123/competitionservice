
plugins {
    id("competitions-mgr.java-conventions")
    scala
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.5")
    implementation("org.typelevel:cats-core_$scalaBinary:2.6.1")
    implementation("dev.zio:zio-interop-cats_$scalaBinary:3.1.1.0")
    Libraries.zio.forEach { implementation(it) }
    Libraries.zioLogging.forEach { implementation(it) }
    Libraries.logging.forEach { implementation(it) }
    Libraries.zioConfig.forEach { implementation(it) }
    Libraries.circle.forEach { implementation(it) }
    Libraries.jackson.forEach { implementation(it) }
    Libraries.embeddedKafka.forEach { testImplementation(it) }
    implementation(project(":competition-serv-model"))
    implementation(project(":compservice-annotations"))
    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.8")
    testImplementation("junit:junit:4.13")

}
description = "saga processor"
