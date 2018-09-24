node {
    /* Requires the Docker Pipeline plugin to be installed */
    checkout scm
    stage('Back-end') {
        docker.image('maven:3-alpine').inside {
            sh 'mvn deploy'
        }
    }
}