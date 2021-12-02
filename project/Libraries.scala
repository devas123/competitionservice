object Libraries {

  case class Dep(group: String, artifactId: String, version: String, classifier: String)

  val embeddedMongodb = List(
    "de.flapdoodle.embed:de.flapdoodle.embed.mongo:${Versions.embeddedMongodb}"
  )

  val monocle = List(
    "dev.optics:monocle-core_2.13:${Versions.monocle}",
    "dev.optics:monocle-macro_2.13:${Versions.monocle}"
  )


  val http4s = List(
    "org.http4s:http4s-dsl_${scalaBinary}:${Versions.http4s}",
    "org.http4s:http4s-blaze-server_${scalaBinary}:${Versions.http4s}",
    "org.http4s:http4s-blaze-client_${scalaBinary}:${Versions.http4s}"
  )
  val rocksdb = List(
    "org.rocksdb:rocksdbjni:${Versions.rocksdb}"
  )

  val mongoDb = List(
    "org.mongodb.scala:mongo-scala-driver_${scalaBinary}:${Versions.mongodb}"
  )

  val zio = List(
    "dev.zio:zio_$scalaBinary:${Versions.zio}",
    "dev.zio:zio-interop-cats_$scalaBinary:${Versions.zioCats}",
    "dev.zio:zio-streams_$scalaBinary:${Versions.zio}",
  )

  val zioKafka = List(
    "dev.zio:zio-kafka_$scalaBinary:${Versions.zioKafka}"
  )

  val zioTest = List(Dep(
    group = "dev.zio",
    artifactId = "zio-test_$scalaBinary",
    version = Versions.zio,
    classifier = "test"
  ),
    Dep(
      group = "dev.zio",
      artifactId = "zio-test-junit_$scalaBinary",
      version = Versions.zio,
      classifier = ""
    ))

  val zioConfig = List(
    "dev.zio:zio-config-magnolia_$scalaBinary:${Versions.zioConfig}",
    "dev.zio:zio-config-typesafe_$scalaBinary:${Versions.zioConfig}",
  )

  val zioLogging = List(
    "dev.zio:zio-logging_$scalaBinary:${Versions.zioLogging}",
    "dev.zio:zio-logging-slf4j_$scalaBinary:${Versions.zioLogging}",
  )

  val cats = List(
    "org.typelevel:cats-core_$scalaBinary:${Versions.cats}",
    "org.typelevel:cats-free_$scalaBinary:${Versions.cats}",
    "org.typelevel:cats-kernel_$scalaBinary:${Versions.cats}",
  )

  val guava = List(
    "com.google.guava:guava:${Versions.guava}"
  )

  val logging = List(
    "org.apache.logging.log4j:log4j-core:${Versions.log4j}",
    "org.apache.logging.log4j:log4j-slf4j-impl:${Versions.log4j}",
    "com.lmax:disruptor:${Versions.disruptor}"
  )

  val jackson = List(
    "com.fasterxml.jackson.core:jackson-databind:${Versions.jackson}",
    "com.fasterxml.jackson.module:jackson-module-parameter-names:${Versions.jackson}",
    "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:${Versions.jackson}",
    "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson}",
    "com.fasterxml.jackson.module:jackson-module-scala_2.13:${Versions.jackson}"

  )
  val embeddedKafka = List("io.github.embeddedkafka:embedded-kafka_$scalaBinary:${Versions.kafka}")
}
