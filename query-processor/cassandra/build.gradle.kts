plugins {
    id("competitions-mgr.java-conventions")
    id("com.palantir.docker")
}



description = "cassandra docker image processor"




configure<com.palantir.gradle.docker.DockerExtension> {
    this.setDockerfile(file("Dockerfile"))
    name = "compsrv-cassandra"
    files("init_db.sh", "docker-entrypoint.sh", "schema.cql", "cassandra.yml")
}