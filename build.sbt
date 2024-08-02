ThisBuild / scalaVersion := "2.13.14"

ThisBuild / githubWorkflowEnv ++= Map(
  "OTEL_EXPORTER_OTLP_PROTOCOL" -> "${{ secrets.OTEL_EXPORTER_OTLP_PROTOCOL }}",
  "OTEL_EXPORTER_OTLP_ENDPOINT" -> "${{ secrets.OTEL_EXPORTER_OTLP_ENDPOINT }}",
  "OTEL_EXPORTER_OTLP_HEADERS"  -> "${{ secrets.OTEL_EXPORTER_OTLP_HEADERS }}",
  "OTEL_SERVICE_NAME"           -> "${{ github.ref_name }}-${{ github.run_attempt }}",
  "OTEL_METRICS_EXPORTER"       -> "none", // we don't export metrics
  "OTEL_SDK_DISABLED" -> "${{ !startsWith(github.ref, 'refs/pull') || secrets.OTEL_SDK_DISABLED }}" // publish traces only for PRs
)

// OTEL_EXPORTER_OTLP_ENDPOINT: ${{ secrets.OTEL_EXPORTER_OTLP_ENDPOINT }}
// OTEL_EXPORTER_OTLP_HEADERS: ${{ secrets.OTEL_EXPORTER_OTLP_HEADERS }}
// OTEL_EXPORTER_OTLP_PROTOCOL: ${{ secrets.OTEL_EXPORTER_OTLP_PROTOCOL }}
// OTEL_SERVICE_NAME: '${{ github.ref_name }}-${{ github.run_attempt }}'
// OTEL_METRICS_EXPORTER: 'none' # we don't need metrics
// TELEMETRY_ENABLED: ${{ startsWith(github.ref, 'refs/pull') && secrets.TELEMETRY_ENABLED }} # publish traces only for PRs

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
