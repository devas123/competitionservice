node {
    /* Requires the Docker Pipeline plugin to be installed */
    checkout scm
    stage('build') {
        docker.image('maven:3-alpine').inside('-v /root/.m2:/root/.m2') {
            sh 'mvn -s /root/.m2/settings.xml clean package'
        }
    }
    stage('deploy') {
        if (currentBuild.result == null || currentBuild.result == 'SUCCESS' && env.BRANCH_NAME == 'master') {
            docker.image('maven:3-alpine').inside('-v /root/.m2:/root/.m2') {
                sh 'mvn -s /root/.m2/settings.xml deploy'
            }
        }
    }
}