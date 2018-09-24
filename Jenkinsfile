node {
    /* Requires the Docker Pipeline plugin to be installed */
    checkout scm
    stage('deploy') {
        docker.image('maven:3-alpine').inside('-v /root/.m2:/root/.m2') {
            sh 'mvn deploy'
        }
    }
}