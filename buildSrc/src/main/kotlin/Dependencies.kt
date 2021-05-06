import org.gradle.api.artifacts.Dependency
import org.gradle.kotlin.dsl.DependencyHandlerScope

object Dependencies {
    fun DependencyHandlerScope.arrowkt(version: String = "0.11.0") = sequenceOf(
        "io.arrow-kt:arrow-free-data",
        "io.arrow-kt:arrow-syntax",
        "io.arrow-kt:arrow-optics",
        "io.arrow-kt:arrow-mtl",
        "io.arrow-kt:arrow-fx",
        "io.arrow-kt:arrow-ui",
    )
        .addVersion(version, dependencies::create)
        .forEach { add("implementation", it) }


    fun DependencyHandlerScope.jackson() {
        sequenceOf(
            "com.fasterxml.jackson.core:jackson-databind",
            "com.fasterxml.jackson.module:jackson-module-parameter-names",
            "com.fasterxml.jackson.module:jackson-module-kotlin",
            "com.fasterxml.jackson.datatype:jackson-datatype-jdk8",
            "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"
        )
            .map(dependencies::create)
            .forEach { add("implementation", it) }
    }

    private fun Sequence<String>.addVersion(version: String, createDeps: (Any) -> Dependency) =
        map { "$it:$version" }.map(createDeps)
}