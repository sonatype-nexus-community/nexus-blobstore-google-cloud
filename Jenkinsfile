@Library(['private-pipeline-library', 'jenkins-shared']) _

mavenSnapshotPipeline(
    javaVersion: 'OpenJDK 8',
    mavenVersion: 'M3',
    useEventSpy: false,
    deployBranch: '_none',
    mavenOptions: '-PskipIT',
    testResults: ['**/target/*-reports/*.xml'],
    runFeatureBranchPolicyEvaluations: true,
    iqPolicyEvaluation: { stage ->
      nexusPolicyEvaluation iqStage: stage, iqApplication: 'nexus-blobstore-google-cloud',
          iqScanPatterns: [[scanPattern: 'scan_nothing']],
          iqModuleExcludes: [[moduleExclude: 'nexus-blobstore-google-cloud-it/**']],
          failBuildOnNetworkError: true
    }
)
