// Jenkinsfile (Declarative Pipeline: stages-first, no helper functions)
// Fill in the TODO commands for your repo/tests and infra paths.

pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '30'))
  }

  parameters {
    choice(name: 'ENVIRONMENT', choices: ['staging', 'production'], description: 'Target environment')
  }

  environment {
    DOCKER_IMAGE = 'yourdockerhubuser/yourapp'
    VERSION_TAG  = "v${BUILD_NUMBER}"

    // Jenkins credentials IDs you must create in Jenkins:
    DOCKERHUB_CREDS = credentials('dockerhub-creds') // usernamePassword
    // AWS creds: use AWS Credentials plugin binding in "withCredentials" (below).
    // Jira + Slack + Email: require plugins + global config.
  }

  stages {

    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Test: unit + integration + e2e') {
      steps {
        // Make tests mandatory to pass: do NOT wrap with catchError.
        sh '''
          set -eu

          echo "TODO: run unit tests (must output JUnit XML)"
          # e.g. pytest -q --junitxml=reports/unit.xml

          echo "TODO: run integration tests (must output JUnit XML)"
          # e.g. pytest -q -m integration --junitxml=reports/integration.xml

          echo "TODO: run e2e tests (must output JUnit XML)"
          # e.g. pytest -q -m e2e --junitxml=reports/e2e.xml

          echo "TODO: generate coverage HTML to htmlcov/"
          # e.g. pytest --cov --cov-report=html:htmlcov
        '''
      }
    }

    stage('Test: performance (production only)') {
      when {
        expression { return params.ENVIRONMENT == 'production' }
      }
      steps {
        sh '''
          set -eu
          echo "TODO: run performance tests (k6/jmeter/locust/etc.)"
        '''
      }
    }

    stage('Reports: JUnit + Coverage') {
      steps {
        // Jenkins shows test results via junit and keeps artifacts via archiveArtifacts. [page:2]
        junit allowEmptyResults: false, testResults: '**/reports/**/*.xml'
        archiveArtifacts artifacts: 'htmlcov/**', fingerprint: true

        // Publish HTML coverage report in Jenkins UI (HTML Publisher plugin). [page:3]
        publishHTML(target: [
          allowMissing: false,
          keepAll: true,
          alwaysLinkToLastBuild: true,
          reportDir: 'htmlcov',
          reportFiles: 'index.html',
          reportName: 'Coverage'
        ])
      }
    }

    stage('Tag (local)') {
      steps {
        sh '''
          set -eu
          git config user.email "jenkins@local"
          git config user.name "jenkins"
          git tag "${VERSION_TAG}"
          echo "Created tag ${VERSION_TAG} (local only)."
          echo "TODO (optional): push tag to origin using SSH/https creds"
        '''
      }
    }

    stage('Build Docker image') {
      steps {
        sh '''
          set -eu
          docker build -f docker/Dockerfile -t "${DOCKER_IMAGE}:${VERSION_TAG}" .
          docker tag "${DOCKER_IMAGE}:${VERSION_TAG}" "${DOCKER_IMAGE}:latest"
        '''
      }
    }

    stage('Push image to Docker Hub') {
      steps {
        sh '''
          set -eu
          echo "${DOCKERHUB_CREDS_PSW}" | docker login -u "${DOCKERHUB_CREDS_USR}" --password-stdin
          docker push "${DOCKER_IMAGE}:${VERSION_TAG}"
          docker push "${DOCKER_IMAGE}:latest"
        '''
      }
    }

    stage('CD: Deploy to AWS staging (Terraform + Ansible)') {
      when {
        expression { return params.ENVIRONMENT == 'staging' }
      }
      steps {
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-creds']]) {
          sh '''
            set -eu

            echo "TODO: Terraform apply (staging)"
            # cd infra/terraform/staging
            # terraform init
            # terraform apply -auto-approve

            echo "TODO: Ansible deploy (staging)"
            # cd infra/ansible
            # ansible-playbook -i inventories/staging site.yml
          '''
        }
      }
    }
  }

  post {
    always {
      // Good place to keep logs/artifacts even on failures; junit/archive/publishHTML already done above.
      echo "Build URL: ${env.BUILD_URL}"
      echo "Branch: ${env.BRANCH_NAME}"
      echo "Last stage (best-effort): ${env.STAGE_NAME}"
      // Note: env.STAGE_NAME in post can show "Declarative: Post Actions", so if you need exact failed stage,
      // you usually store it yourself per-stage. [web:39]
    }

    failure {
      // Create JIRA issue (requires Jira plugin + configured site/steps). [web:30]
      // Example only; adjust IDs/fields to your Jira config.
      script {
        try {
          def issue = [fields: [
            project: [id: '10000'],
            summary: "Pipeline failure: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            description: "Branch: ${env.BRANCH_NAME}\nBuild URL: ${env.BUILD_URL}\n",
            issuetype: [id: '10001']
          ]]
          jiraNewIssue issue: issue, site: 'your-jira-site'
        } catch (e) {
          echo "JIRA creation failed: ${e}"
        }
      }

      // Email (Email Extension plugin provides emailext). [page:5]
      emailext(
        to: 'team@example.com',
        subject: "${env.JOB_NAME} #${env.BUILD_NUMBER} - FAILURE",
        body: """Build number: ${env.BUILD_NUMBER}
Branch name: ${env.BRANCH_NAME}
Duration: ${currentBuild.durationString}
Failed stage: (see Jenkins UI)
Build URL: ${env.BUILD_URL}
"""
      )

      // Optional Slack (requires Slack plugin + slackSend configured).
      // slackSend(channel: '#ci', message: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER} ${env.BUILD_URL}")
    }
  }
}
