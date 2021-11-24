plugins {
    id("competitions-mgr.java-conventions")
    scala
}

dependencies {
    Libraries.cats.forEach { implementation(it)  }
    Libraries.zio.forEach { implementation(it)   }
    Libraries.zioLogging.forEach { implementation(it) }
    implementation("org.scala-lang:scala-library:2.13.5")
    scalaCompilerPlugins("org.typelevel:kind-projector_2.13.5:0.13.2")
}
description = "actor system"
