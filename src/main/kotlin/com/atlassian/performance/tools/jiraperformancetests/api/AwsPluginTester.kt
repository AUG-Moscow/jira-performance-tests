package com.atlassian.performance.tools.jiraperformancetests.api

import com.atlassian.performance.tools.aws.Aws
import com.atlassian.performance.tools.aws.Investment
import com.atlassian.performance.tools.awsinfrastructure.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.JiraFormula
import com.atlassian.performance.tools.awsinfrastructure.storage.JiraSoftwareStorage
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.Ec2VirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.app.AppSource
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.virtualusers.LoadProfile
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import com.atlassian.performance.tools.jiraactions.scenario.Scenario
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors

class AwsPluginTester(
    private val aws: Aws,
    private val dataset: Dataset,
    outputDirectory: Path,
    private val deployment: AwsJiraDeployment
) {
    private val root: RootWorkspace = RootWorkspace(outputDirectory)
    private val label = "Plugin Test"

    fun run(
        shadowJar: File,
        scenarioClass: Class<out Scenario>,
        baselineApp: AppSource,
        experimentApp: AppSource,
        load: LoadProfile,
        jiraVersion: String
    ): RegressionResults {
        val standaloneStabilityTest = RegressionTest(
            dataset,
            shadowJar,
            scenarioClass,
            baselineApp,
            experimentApp,
            load,
            jiraVersion
        )
        return standaloneStabilityTest.run(
            workspace = root.currentTask.isolateTest(label)
        )
    }

    private inner class RegressionTest(
        private val dataset: Dataset,
        private val shadowJar: File,
        private val scenarioClass: Class<out Scenario>,
        private val baselineApp: AppSource,
        private val experimentApp: AppSource,
        private val load: LoadProfile,
        private val jiraVersion: String
    ) {
        fun run(workspace: TestWorkspace): RegressionResults {
            //provisioning
            val baselineLabel = baselineApp.getLabel()
            val baselineTest = provisioningTest(
                cohort = baselineLabel,
                jira = formula(
                    jiraVersion,
                    dataset,
                    Apps(listOf(baselineApp))
                ),
                feature = label,
                shadowJar = shadowJar
            )
            val experimentLabel = experimentApp.getLabel()
            val experimentCohort = if (baselineLabel == experimentLabel) {
                "$experimentLabel*"
            } else {
                experimentLabel
            }

            val experimentTest = provisioningTest(
                cohort = experimentCohort,
                jira = formula(
                    jiraVersion,
                    dataset,
                    Apps(listOf(experimentApp))
                ),
                feature = label,
                shadowJar = shadowJar
            )

            // run tests

            val executor = Executors.newFixedThreadPool(
                2,
                ThreadFactoryBuilder()
                    .setNameFormat("standalone-stability-test-thread-%d")
                    .build()
            )

            val futureBaselineResults = baselineTest.runAsync(workspace, executor, load, scenarioClass)
            val futureExperimentResults = experimentTest.runAsync(workspace, executor, load, scenarioClass)

            /// rest of the test

            val rawBaselineResults = futureBaselineResults.get()
            val rawExperimentResults = futureExperimentResults.get()
            executor.shutdownNow()

            return RegressionResults(
                baseline = rawBaselineResults,
                experiment = rawExperimentResults
            )
        }
    }

    private fun provisioningTest(
        feature: String,
        cohort: String,
        jira: JiraFormula,
        shadowJar: File
    ): ProvisioningPerformanceTest = ProvisioningPerformanceTest(
        cohort = cohort,
        infrastructureFormula = infrastructureFormula(
            feature = feature,
            jira = jira,
            shadowJar = shadowJar
        )
    )

    private fun infrastructureFormula(
        feature: String,
        jira: JiraFormula,
        shadowJar: File
    ): InfrastructureFormula<SshVirtualUsers> = InfrastructureFormula(
        investment = Investment(
            useCase = "Catch JPT regressions in $feature",
            lifespan = Duration.ofHours(1)
        ),
        jiraFormula = jira,
        virtualUsersFormula = Ec2VirtualUsersFormula(
            shadowJar = shadowJar
        ),
        aws = aws
    )

    private fun formula(
        jiraVersion: String,
        dataset: Dataset,
        apps: Apps
    ) = deployment.createJiraFormula(
        apps = apps,
        application = JiraSoftwareStorage(jiraVersion),
        jiraHomeSource = dataset.jiraHomeSource,
        database = dataset.database
    )
}
