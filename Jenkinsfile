pipeline {
    agent none
    stages {
        stage('Build') {
            agent {
                label "linux"
            }
            environment {
                srcDir = pwd()
            }
            steps {
                withChecks(name: "Build android") {
                    sh "./scripts/ci.sh build"
                }
            }
        }
    }
}