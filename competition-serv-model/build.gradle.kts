plugins {
    id("competitions-mgr.java-conventions")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")
    testCompileOnly("org.projectlombok:lombok:1.18.20")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.20")
    annotationProcessor(project(":compservice-annotation-processor"))
    implementation(project(":compservice-annotations"))
    implementation("com.kjetland:mbknor-jackson-jsonschema_2.12:1.0.39")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.0")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.10.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.10.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.10.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.10.0")
}

description = "competition-serv-model"
