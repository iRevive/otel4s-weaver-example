# otel4s 🤝 weaver 🤝 GitHub Actions 🤝 Grafana Cloud

This examples shows how to instrument test with traces using [otel4s](https://github.com/typelevel/otel4s)
and [weaver](https://github.com/disneystreaming/weaver-test) and publish test traces to Grafana Cloud from GitHub
Actions.

Check out the [blog post](https://ochenashko.com/otel4s-test-traces-grafana) for the details.

----

Push your changes, create a PR, check the result. The generated link automatically shows all spans associated with the current pipeline run.

### Successful traces

![Grafana Tempo Traces](./images/grafana-tempo-traces.png)

### An errored trace

![Jaeger Errored Trace Example](./images/jaeger-errored-trace-example.png)
