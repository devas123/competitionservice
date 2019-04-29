node {
    /* Requires the Docker Pipeline plugin to be installed */
    checkout scm
    try {
        stage('build') {
            docker.image('maven:3-alpine').inside('-v /root/.m2:/root/.m2') {
                sh 'mvn -s /root/.m2/settings.xml clean package'
            }
        }
        if (env.BRANCH_NAME == 'master' && (currentBuild.result == null || currentBuild.result == 'SUCCESS')) {
            stage('deploy') {
                docker.image('maven:3-alpine').inside('-v /root/.m2:/root/.m2') {
                    sh 'mvn -s /root/.m2/settings.xml deploy'
                }
            }
            stage('docker') {
                docker.withRegistry('http://95.169.186.20:8082/repository/compmanager-registry/', 'Nexus') {
                    env.IMAGE_NAME = "competitionservice"
                    def customImage = null
                    stage("build_docker") {
                        try {
                            customImage = docker.build("${env.IMAGE_NAME}/${env.BRANCH_NAME}:${env.BUILD_NUMBER}")
                        } catch (err) {
                            currentBuild.result = 'FAILURE'
                            print "Failed: ${err}"
                            throw err
                        }
                    }
                    if (customImage != null && currentBuild.result != 'FAILURE') {
                        stage("push_image") {
                            customImage.push()
                        }
                    }

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