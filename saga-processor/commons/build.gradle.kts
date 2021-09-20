plugins {
    id("competitions-mgr.java-conventions")
    scala
}

dependencies {
    Libraries.cats.forEach { implementation(it) {
        isTransitive = false
    } }
    Libraries.zio.forEach { implementation(it) {
        isTransitive = false
    } }
    Libraries.zioLogging.forEach { implementation(it) {
        isTransitive = false
    } }
    implementation("org.scala-lang:scala-library:2.13.5")
    implementation(project(":competition-serv-model"))
    implementation(project(":compservice-annotations"))
    scalaCompilerPlugins("org.typelevel:kind-projector_2.13.5:0.13.2")
}
description = "common model"