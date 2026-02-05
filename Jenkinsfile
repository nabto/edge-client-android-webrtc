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
                sh './scripts/ci.sh build'
            }
        }

        stage('Deploy') {
            agent {
                label "linux"
            }
            steps {
                 withCredentials([string(credentialsId: "android_signing_key", variable: "ORG_GRADLE_PROJECT_GPG_SIGNING_KEY_BASE64"),
                                  string(credentialsId: "android_signing_public_key", variable: "ORG_GRADLE_PROJECT_GPG_SIGNING_PUBLIC_KEY_BASE64"),
                                  string(credentialsId: "android_signing_key_password", variable: "ORG_GRADLE_PROJECT_GPG_SIGNING_PASSWORD"),
                                  string(credentialsId: "maven_central_token_username", variable: "ORG_GRADLE_PROJECT_OSSRH_USERNAME"),
                                  string(credentialsId: "maven_central_token_password", variable: "ORG_GRADLE_PROJECT_OSSRH_PASSWORD")]) {
                     sh './scripts/ci.sh publish'
                 }
            }
        }
    }
}
