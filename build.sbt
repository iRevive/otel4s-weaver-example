ThisBuild / scalaVersion := "2.13.14"

ThisBuild / githubWorkflowPublish := Nil
ThisBuild / githubWorkflowEnv ++= Map(
  "OTEL_EXPORTER_OTLP_PROTOCOL" -> "${{ secrets.OTEL_EXPORTER_OTLP_PROTOCOL }}",
  "OTEL_EXPORTER_OTLP_ENDPOINT" -> "${{ secrets.OTEL_EXPORTER_OTLP_ENDPOINT }}",
  "OTEL_EXPORTER_OTLP_HEADERS"  -> "${{ secrets.OTEL_EXPORTER_OTLP_HEADERS }}",
  "OTEL_SERVICE_NAME"           -> "${{ github.ref_name }}-${{ github.run_attempt }}",
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
    val serviceName = "service_name_placeholder"
    val revision    = "revision_placeholder"
    val rangeFrom   = "range_from_placeholder"
    val rangeTo     = "range_to_placeholder"

    val panesJson =
      s"""{"7uq": {
        |  "datasource":"grafanacloud-traces",
        |  "queries":[
        |    {
        |      "refId":"A",
        |      "datasource":{"type":"tempo","uid":"grafanacloud-traces"},
        |      "queryType":"traceql",
        |      "limit":100,
        |      "query":"{resource.service.name=\"$serviceName\" && resource.revision=\"$revision\"}"
        |    }
        |  ],
        |  "range":{"from":"$rangeFrom","to":"$rangeTo"}
        |}}""".stripMargin.replace(" ", "").replace("\n", "")

    val panesEncoded = panesJson // java.net.URLEncoder.encode(panesJson, "UTF-8")
    val panes = panesEncoded
      .replace(serviceName, "${{ github.ref_name }}-${{ github.run_attempt }}")
      .replace(revision, "${{ github.sha }}")
      .replace(rangeFrom, "${{ env.tests_start_time }}")
      .replace(rangeTo, "${{ env.tests_end_time }}")

    val link = s"https://d098b2fe4.grafana.net/explore?panes=$panes&schemaVersion=1&orgId=1"
    s"The traces can be reviewed [here]($link)."
  }

  WorkflowStep.Use(
    UseRef.Public("peter-evans", "create-or-update-comment", "v4"),
    name = Some("Publish 'Grafana traces' comment"),
    cond = Some("startsWith(github.ref, 'refs/pull')"),
    params = Map(
      "issue-number" -> "${{ github.event.pull_request.number }}",
      // "comment-author" -> "github-actions[bot]",
      "body" -> body
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

//The traces can be reviewed [here](https://synapseedu.grafana.net/explore?panes=%7B%227uq%22:%7B%22datasource%22:%22grafanacloud-traces%22,%22queries%22:%5B%7B%22refId%22:%22A%22,%22datasource%22:%7B%22type%22:%22tempo%22,%22uid%22:%22grafanacloud-traces%22%7D,%22queryType%22:%22traceql%22,%22limit%22:20,%22filters%22:%5B%7B%22id%22:%2212faf5c5%22,%22operator%22:%22%3D%22,%22scope%22:%22span%22%7D%5D,%22query%22:%22%7Bresource.service.name%3D%5C%22${{ github.ref_name }}-${{ github.run_attempt }}%5C%22%7D%22%7D%5D,%22range%22:%7B%22from%22:%22${{ env.tests_start_time }}%22,%22to%22:%22${{ env.tests_end_time }}%22%7D%7D%7D&schemaVersion=1&orgId=1).'
//
