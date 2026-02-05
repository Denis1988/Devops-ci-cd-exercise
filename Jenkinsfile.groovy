def FAILED_STAGE = "unknown"

def runStage(String name, Closure body) {
  stage(name) {
    FAILED_STAGE = name
    body()
  }
}

node {
  // Parameters / environment
  def DOCKER_IMAGE = "yourdockerhubuser/yourapp"
  def VERSION_TAG = "v${env.BUILD_NUMBER}"
  def ENVIRONMENT = (params.ENVIRONMENT ?: "staging")

  properties([
    parameters([
      choice(name: 'ENVIRONMENT', choices: ['staging', 'production'], description: 'Target environment')
    ])
  ])

  def startMillis = System.currentTimeMillis()

  try {
    runStage('Checkout') {
      checkout scm
    }

    runStage('Test: unit + integration + e2e') {
      // Replace these with your real commands.
      // Mandatory to pass -> let them fail the build (default behavior).
      sh '''
        set -eu
        echo "Run unit tests..."
        # e.g. pytest -q --junitxml=reports/unit.xml

        echo "Run integration tests..."
        # e.g. pytest -q -m integration --junitxml=reports/integration.xml

        echo "Run e2e tests..."
        # e.g. pytest -q -m e2e --junitxml=reports/e2e.xml

        echo "Generate coverage HTML to htmlcov/ ..."
        # e.g. pytest --cov --cov-report=html:htmlcov
      '''
    }

    runStage('Test: performance (prod only)') {
      if (ENVIRONMENT != 'production') {
        echo "Skipping performance tests for ENVIRONMENT=${ENVIRONMENT}"
      } else {
        sh '''
          set -eu
          echo "Run performance tests..."
          # e.g. k6 run perf/script.js
        '''
      }
    }

    runStage('Publish reports (Jenkins UI)') {
      // JUnit XML aggregation in Jenkins UI
      junit allowEmptyResults: false, testResults: '**/reports/**/*.xml'  // adjust glob
      // Archive HTML coverage directory so it’s downloadable
      archiveArtifacts artifacts: 'htmlcov/**', fingerprint: true
      // Publish HTML coverage as a browsable report link
      // publishHTML is provided by the HTML Publisher plugin, and is commonly used for coverage HTML. [page:3]
      publishHTML(target: [
        allowMissing: false,
        keepAll: true,
        alwaysLinkToLastBuild: true,
        reportDir: 'htmlcov',
        reportFiles: 'index.html',
        reportName: 'Coverage'
      ])
    }

    runStage('Tag + build Docker image') {
      sh """
        set -eu
        git config user.email "jenkins@local"
        git config user.name "jenkins"
        git tag ${VERSION_TAG}
      """
      // If you want to push the tag back to origin, you’ll need credentials + git push here.

      sh """
        set -eu
        docker build -f docker/Dockerfile -t ${DOCKER_IMAGE}:${VERSION_TAG} .
        docker tag ${DOCKER_IMAGE}:${VERSION_TAG} ${DOCKER_IMAGE}:latest
      """
    }

    runStage('Push image to Docker Hub') {
      withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
        sh """
          set -eu
          echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
          docker push ${DOCKER_IMAGE}:${VERSION_TAG}
          docker push ${DOCKER_IMAGE}:latest
        """
      }
    }

    runStage('CD: Deploy to AWS staging (Terraform + Ansible)') {
      if (ENVIRONMENT != 'staging') {
        echo "Skipping staging deploy for ENVIRONMENT=${ENVIRONMENT}"
      } else {
        withCredentials([
          [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-creds']
        ]) {
          sh '''
            set -eu
            cd infra/terraform/staging
            terraform init
            terraform apply -auto-approve
          '''
          sh '''
            set -eu
            cd infra/ansible
            ansible-playbook -i inventories/staging site.yml
          '''
        }
      }
    }

  } catch (err) {
    currentBuild.result = 'FAILURE'
    throw err
  } finally {
    def durationSec = ((System.currentTimeMillis() - startMillis) / 1000.0).round(1)

    // On failure: create JIRA issue (one option is a JIRA Pipeline Steps plugin step like jiraNewIssue). [web:30]
    if (currentBuild.result == 'FAILURE') {
      try {
        // Example only; requires Jira plugin config + IDs adjusted.
        def issue = [fields: [
          project: [id: '10000'],
          summary: "Jenkins failure: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
          description: "Branch: ${env.BRANCH_NAME}\nBuild URL: ${env.BUILD_URL}\nFailed stage: ${FAILED_STAGE}\nDuration: ${durationSec}s",
          issuetype: [id: '10001']
        ]]
        jiraNewIssue issue: issue, site: 'your-jira-site'
      } catch (jiraErr) {
        echo "JIRA creation failed: ${jiraErr}"
      }
    }

    // Email notifications (Email Extension plugin supports emailext step). [page:5]
    emailext(
      to: 'team@example.com',
      subject: "${env.JOB_NAME} #${env.BUILD_NUMBER} - ${currentBuild.currentResult}",
      body: """Build number: ${env.BUILD_NUMBER}
Branch name: ${env.BRANCH_NAME}
Duration: ${durationSec}s
Failed stage: ${FAILED_STAGE}
Build URL: ${env.BUILD_URL}
"""
    )

    // Optional Slack: if you install Slack plugin, you can call slackSend(message: "...") (example usage exists in common Jenkins pipeline patterns). [web:36]
  }
}
