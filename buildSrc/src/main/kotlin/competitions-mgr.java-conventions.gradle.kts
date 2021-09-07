

plugins {
    java
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "competitions-mgr"
version = "1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11


tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
