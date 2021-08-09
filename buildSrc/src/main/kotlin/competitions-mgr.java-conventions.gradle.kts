

plugins {
    java
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.spring.io/libs-milestone")
    }
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

group = "competitions-mgr"
version = "1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11


tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isFork = true
    options.isIncremental = true
}
