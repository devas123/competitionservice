const val scalaBinary = "2.13"

object Versions {
    const val zio = "1.0.8"
    const val zioKafka = "0.13.0"
    const val zioConfig = "1.0.0"
    const val zioLogging = "0.4.0"
    const val circe = "0.13.0"
    const val cats = "2.6.1"
    const val guava = "30.1.1-jre"
    const val log4j = "2.13.3"
    const val disruptor = "3.4.2"
    const val jackson = "2.12.0"
    const val kafka = "2.4.1.1"
    const val rocksdb: String = "6.13.3"
    const val http4s: String = "0.23.3"
    const val quill: String = "3.8.0"
}

data class Dep(val group: String, val artifactId: String, val version: String, val classifier: String)

object Libraries {

    val http4s = listOf(
        "org.http4s:http4s-dsl_${scalaBinary}:${Versions.http4s}",
        "org.http4s:http4s-blaze-server_${scalaBinary}:${Versions.http4s}",
        "org.http4s:http4s-blaze-client_${scalaBinary}:${Versions.http4s}"
    )
    val rocksdb = listOf(
        "org.rocksdb:rocksdbjni:${Versions.rocksdb}"
    )

    val quill = listOf(
        "io.getquill:quill-cassandra_${scalaBinary}:${Versions.quill}"
    )

    val zio = listOf(
        "dev.zio:zio_$scalaBinary:${Versions.zio}",
        "dev.zio:zio-streams_$scalaBinary:${Versions.zio}",
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

    val circle = listOf(
        "io.circe:circe-core_$scalaBinary:${Versions.circe}",
        "io.circe:circe-generic_$scalaBinary:${Versions.circe}",
    )
    val cats = listOf(
        "org.typelevel:cats-core_$scalaBinary:${Versions.cats}",
        "org.typelevel:cats-free_$scalaBinary:${Versions.cats}",
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
        "com.fasterxml.jackson.core:jackson-databind:${Versions.jackson}",
        "com.fasterxml.jackson.module:jackson-module-parameter-names:${Versions.jackson}",
        "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:${Versions.jackson}",
        "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson}"

    )
    val embeddedKafka = listOf("io.github.embeddedkafka:embedded-kafka_$scalaBinary:${Versions.kafka}")
}
