node {
    /* Requires the Docker Pipeline plugin to be installed */
    checkout scm
    try {
        stage('build') {
            docker.image('maven:3-alpine').inside('-v /root/.m2:/root/.m2') {
                sh 'mvn -s /root/.m2/settings.xml clean package'
            }
        }
        stage('deploy') {
            if (env.BRANCH_NAME == 'master' && (currentBuild.result == null || currentBuild.result == 'SUCCESS')) {
                docker.image('maven:3-alpine').inside('-v /root/.m2:/root/.m2') {
                    sh 'mvn -s /root/.m2/settings.xml deploy'
                }
            }
        }
    } finally {
        try {
            junit '**/target/surefire-reports/**/*.xml'
        } catch (e) {
            echo "Error while aggregating test results: ${e}"
        }
    }
}