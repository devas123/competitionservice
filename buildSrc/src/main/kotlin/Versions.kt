const val scalaBinary = "2.13"

object Versions {
    const val zio = "1.0.9"
    const val zioKafka = "0.13.0"
    const val zioConfig = "1.0.0"
    const val zioLogging = "0.4.0"
    const val zioCats = "3.1.1.0"
    const val circe = "0.14.1"
    const val cats = "2.6.1"
    const val guava = "30.1.1-jre"
    const val log4j = "2.13.3"
    const val disruptor = "3.4.2"
    const val jackson = "2.12.0"
    const val kafka = "2.8.1"
    const val rocksdb: String = "6.13.3"
    const val http4s: String = "1.0.0-M27"
    const val mongodb: String = "4.3.3"
    const val embeddedMongodb: String = "3.1.4"
}

data class Dep(val group: String, val artifactId: String, val version: String, val classifier: String)

object Libraries {

    val embeddedMongodb = listOf(
        "de.flapdoodle.embed:de.flapdoodle.embed.mongo:${Versions.embeddedMongodb}"
    )
    val http4s = listOf(
        "org.http4s:http4s-dsl_${scalaBinary}:${Versions.http4s}",
        "org.http4s:http4s-blaze-server_${scalaBinary}:${Versions.http4s}",
        "org.http4s:http4s-blaze-client_${scalaBinary}:${Versions.http4s}"
    )
    val rocksdb = listOf(
        "org.rocksdb:rocksdbjni:${Versions.rocksdb}"
    )

    val mongoDb = listOf(
        "org.mongodb.scala:mongo-scala-driver_${scalaBinary}:${Versions.mongodb}"
    )

    val zio = listOf(
        "dev.zio:zio_$scalaBinary:${Versions.zio}",
        "dev.zio:zio-interop-cats_$scalaBinary:${Versions.zioCats}",
        "dev.zio:zio-streams_$scalaBinary:${Versions.zio}",
    )

    val zioKafka = listOf(
        "dev.zio:zio-kafka_$scalaBinary:${Versions.zioKafka}"
    )

    val zioTest = Dep(
        group = "dev.zio",
        artifactId = "zio-test_$scalaBinary",
        version = Versions.zio,
        classifier = "test"
    )

    val zioConfig = listOf(
        "dev.zio:zio-config-magnolia_$scalaBinary:${Versions.zioConfig}",
        "dev.zio:zio-config-typesafe_$scalaBinary:${Versions.zioConfig}",
    )

    val zioLogging = listOf(
        "dev.zio:zio-logging_$scalaBinary:${Versions.zioLogging}",
        "dev.zio:zio-logging-slf4j_$scalaBinary:${Versions.zioLogging}",
    )

    val cats = listOf(
        "org.typelevel:cats-core_$scalaBinary:${Versions.cats}",
        "org.typelevel:cats-free_$scalaBinary:${Versions.cats}",
        "org.typelevel:cats-kernel_$scalaBinary:${Versions.cats}",
    )

    val guava = listOf(
        "com.google.guava:guava:${Versions.guava}"
    )

    val logging = listOf(
        "org.apache.logging.log4j:log4j-core:${Versions.log4j}",
        "org.apache.logging.log4j:log4j-slf4j-impl:${Versions.log4j}",
        "com.lmax:disruptor:${Versions.disruptor}"
    )

    val jackson = listOf(
        "com.fasterxml.jackson.core:jackson-databind:${Versions.jackson}",
        "com.fasterxml.jackson.module:jackson-module-parameter-names:${Versions.jackson}",
        "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:${Versions.jackson}",
        "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson}",
        "com.fasterxml.jackson.module:jackson-module-scala_2.13:${Versions.jackson}"

    )
    val embeddedKafka = listOf("io.github.embeddedkafka:embedded-kafka_$scalaBinary:${Versions.kafka}")
}
