plugins {
    id("competitions-mgr.java-conventions")
    scala
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.5")
    implementation("org.typelevel:cats-core_3.0.0-RC3:2.6.0")
    testImplementation("org.scalatest:scalatest_2.13:3.2.8")
    testImplementation("junit:junit:4.13")
}

description = "saga processor"
