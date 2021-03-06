/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    id("competitions-mgr.java-conventions")
}

dependencies {
    implementation(project(":compservice-annotations"))
    implementation("com.google.guava:guava:27.1-jre")
    implementation("com.squareup:javapoet:1.13.0")
    compileOnly("com.google.auto.service:auto-service:1.0-rc7")
    annotationProcessor("com.google.auto.service:auto-service:1.0-rc7")
}

description = "compservice-annotation-processor"
