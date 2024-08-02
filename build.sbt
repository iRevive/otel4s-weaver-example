ThisBuild / scalaVersion := "2.13.14"

ThisBuild / githubWorkflowPublish := Nil
ThisBuild / githubWorkflowEnv ++= Map(
  "OTEL_EXPORTER_OTLP_PROTOCOL" -> "${{ secrets.OTEL_EXPORTER_OTLP_PROTOCOL }}",
  "OTEL_EXPORTER_OTLP_ENDPOINT" -> "${{ secrets.OTEL_EXPORTER_OTLP_ENDPOINT }}",
  "OTEL_EXPORTER_OTLP_HEADERS"  -> "${{ secrets.OTEL_EXPORTER_OTLP_HEADERS }}",
  "OTEL_SERVICE_NAME"           -> "${{ github.ref }}",
  "OTEL_RESOURCE_ATTRIBUTES"    -> "revision=${{ github.sha }}",
  "OTEL_METRICS_EXPORTER"       -> "none", // we don't export metrics
  "OTEL_SDK_DISABLED" -> "${{ !startsWith(github.ref, 'refs/pull') || secrets.OTEL_SDK_DISABLED }}" // publish traces only for PRs
)

ThisBuild / githubWorkflowBuildPreamble +=
  WorkflowStep.ComputeVar("tests_start_time", "date -d \"$(date +'%Y-%m-%d') 00:00:00\" +%s%3N")

ThisBuild / githubWorkflowBuildPostamble +=
  WorkflowStep.ComputeVar("tests_end_time", "date -d \"$(date +'%Y-%m-%d') 23:59:59\" +%s%3N")

ThisBuild / githubWorkflowBuildPostamble += {
  def body = {
    val panes =
      """{"7uq": {
        |  "datasource":"grafanacloud-traces",
        |  "queries":[
        |    {
        |      "refId":"A",
        |      "datasource":{"type":"tempo","uid":"grafanacloud-traces"},
        |      "queryType":"traceql",
        |      "limit":100,
        |      "query":"{resource.revision=\"${{ github.sha }}\"}"
        |    }
        |  ],
        |  "range":{"from":"${{ env.tests_start_time }}","to":"${{ env.tests_end_time }}"}
        |}}""".stripMargin.replace(" ", "").replace("\n", "")

    val link = s"https://$${{ vars.GRAFANA_HOST }}/explore?panes=$panes&schemaVersion=1&orgId=1"
    s"The traces can be reviewed here - $link."
  }

  WorkflowStep.Use(
    UseRef.Public("peter-evans", "create-or-update-comment", "v4"),
    name = Some("Publish 'Grafana traces' comment"),
    cond = Some("startsWith(github.ref, 'refs/pull')"),
    params = Map(
      "issue-number" -> "${{ github.event.pull_request.number }}",
      "body"         -> body
    )
  )
}

lazy val root = project
  .in(file("."))
  .settings(
    name := "otel4s-weaver-example",
    libraryDependencies ++= Seq(
      "org.typelevel"       %% "otel4s-core-trace"         % "0.8.1",
      "org.typelevel"       %% "otel4s-sdk-trace"          % "0.8.1" % Test,
      "org.typelevel"       %% "otel4s-sdk-exporter-trace" % "0.8.1" % Test,
      "com.disneystreaming" %% "weaver-cats"               % "0.8.4" % Test,
      "com.disneystreaming" %% "weaver-scalacheck"         % "0.8.4" % Test
    ),
    Test / fork := true,
    Test / envVars ++= {
      if (!insideCI.value) {
        Map(
          "OTEL_SERVICE_NAME"           -> "ServiceTests",
          "OTEL_EXPORTER_OTLP_PROTOCOL" -> "http/protobuf",
          "OTEL_EXPORTER_OTLP_ENDPOINT" -> "http://localhost:4318"
        )
      } else
        Map.empty[String, String]
    }
  )
