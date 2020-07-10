@Library(['private-pipeline-library', 'jenkins-shared']) _

mavenSnapshotPipeline(
    javaVersion: 'OpenJDK 8',
    mavenVersion: 'M3',
    useEventSpy: false,
    testResults: ['**/target/*-reports/*.xml']
)
