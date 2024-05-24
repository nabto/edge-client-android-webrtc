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

        stage('Deploy') {
            agent {
                label "linux"
            }
            steps {
                 withCredentials([string(credentialsId: "android_signing_key", variable: "SIGNING_KEY_BASE64"),
                                  string(credentialsId: "android_signing_key_password", variable: "SIGNING_PASSWORD"),
                                  string(credentialsId: "sonatype_ossrh_username", variable: "OSSRH_USERNAME"),
                                  string(credentialsId: "sonatype_ossrh_password", variable: "OSSRH_PASSWORD")]) {
                     sh "./scripts/ci.sh publish"
                 }
            }
        }
    }
}