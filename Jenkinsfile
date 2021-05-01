@Library('forge-shared-library')_

pipeline {
    agent {
        docker {
            image 'gradle:jdk8'
            args '-v forgegc:/home/gradle/.gradle'
        }
    }
    environment {
        GRADLE_ARGS = '--no-daemon --console=plain'
        DISCORD_WEBHOOK = credentials('forge-discord-jenkins-webhook')
        DISCORD_PREFIX = "Job: Installer Branch: ${BRANCH_NAME} Build: #${BUILD_NUMBER}"
        JENKINS_HEAD = 'https://wiki.jenkins-ci.org/download/attachments/2916393/headshot.png'
    }

    stages {
        stage('buildandtest') {
            steps {
                sh './gradlew ${GRADLE_ARGS} --refresh-dependencies --continue build test'
                script {
                    env.MYVERSION = sh(returnStdout: true, script: './gradlew properties -q | grep "version:" | awk \'{print $2}\'').trim()
                }
            }
            post {
                success {
                    writeChangelog(currentBuild, 'build/changelog.txt')
                    archiveArtifacts artifacts: 'build/changelog.txt', fingerprint: false
                }
            }
        }
        stage('publish') {
            when {
                not {
                    changeRequest()
                }
            }
            environment {
                FORGE_MAVEN = credentials('forge-maven-forge-user')
            }
            steps {
                sh './gradlew ${GRADLE_ARGS} publish -PforgeMavenUser=${FORGE_MAVEN_USR} -PforgeMavenPassword=${FORGE_MAVEN_PSW}'
                sh 'curl --user ${FORGE_MAVEN} http://files.minecraftforge.net/maven/manage/promote/latest/net.minecraftforge.installer/${MYVERSION}'
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'build/libs/**/*.jar', fingerprint: true, onlyIfSuccessful: true
            //junit 'build/test-results/*/*.xml'
            //jacoco sourcePattern: '**/src/*/java'

            if (env.CHANGE_ID == null) { // This is unset for non-PRs
                discordSend(
                    title: "${DISCORD_PREFIX} Finished ${currentBuild.currentResult}",
                    description: '```\n' + getChanges(currentBuild) + '\n```',
                    successful: currentBuild.resultIsBetterOrEqualTo("SUCCESS"),
                    result: currentBuild.currentResult,
                    thumbnail: JENKINS_HEAD,
                    webhookURL: DISCORD_WEBHOOK
                )
            }
        }
    }
}