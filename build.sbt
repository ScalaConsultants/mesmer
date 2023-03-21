import Dependencies._
import sbt.Keys.connectInput

lazy val scala213 = "2.13"

inThisBuild(
  List(
    scalaVersion := "2.13.10",
    organization := "io.scalac",
    homepage     := Some(url("https://github.com/ScalaConsultants/mesmer-akka-agent")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jczuchnowski",
        "Jakub Czuchnowski",
        "jakub.czuchnowski@gmail.com",
        url("https://github.com/jczuchnowski")
      ),
      Developer(
        "lgajowy",
        "Łukasz Gajowy",
        "lukasz.gajowy@scalac.io",
        url("https://github.com/lgajowy")
      )
    ),
    scalacOptions ++= Seq("-deprecation", "-feature"),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Wunused:imports",
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0",
    scalafixScalaBinaryVersion                     := "2.13"
  )
)

addCommandAlias("fmt", "scalafmtSbt; scalafmtAll; scalafixAll")
addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck")
addCommandAlias("testAll", "test; IntegrationTest/test")

val projectRootDir = all.base.absolutePath

lazy val all: Project = (project in file("."))
  .settings(
    name           := "mesmer-all",
    publish / skip := true
  )
  .aggregate(otelExtension, core, testkit, exampleAkka, exampleAkkaStream, exampleZio)

lazy val core = (project in file("core"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name := "mesmer-akka-core",
    libraryDependencies ++= {
      akka ++
      opentelemetryExtensionApi ++
      scalatest.map(_ % "test") ++
      akkaTestkit.map(_ % "test")
    }
  )
  .dependsOn(testkit % "test")

lazy val testkit = (project in file("testkit"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name           := "mesmer-testkit",
    publish / skip := true,
    libraryDependencies ++= {
      scalatest ++ akkaTestkit
    }
  )

lazy val otelExtension = (project in file("otel-extension"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    name                                               := "mesmer-otel-extension",
    excludeDependencies += "io.opentelemetry.javaagent" % "opentelemetry-javaagent-bootstrap",
    libraryDependencies ++= {
      zio.map(_ % "provided") ++
      openTelemetryExtension.map(_ % "provided") ++
      opentelemetryExtensionApi ++
      openTelemetryMuzzle.map(_ % "provided") ++
      openTelemetryInstrumentationApiSemanticConventions ++
      byteBuddy.map(_ % "provided") ++
      akkaTestkit.map(_ % "it,test") ++
      scalatest.map(_ % "it,test") ++
      openTelemetryTesting.map(_ % "it,test")
    },
    assembly / test            := {},
    assembly / assemblyJarName := s"${name.value}-assembly.jar",
    assemblyMergeStrategySettings,
    assembly / artifact := {
      val art = (assembly / artifact).value
      art.withClassifier(Some("assembly"))
    },
    addArtifact(assembly / artifact, assembly),
    Test / fork              := true,
    Test / parallelExecution := true,
    Test / testGrouping := (Test / testGrouping).value flatMap { group =>
      group.tests.map { test =>
        Tests.Group(name = test.name, tests = Seq(test), runPolicy = group.runPolicy)
      }
    },
    Test / testOnly / testGrouping      := (Test / testGrouping).value,
    IntegrationTest / parallelExecution := false,
    IntegrationTest / fork              := true,
    IntegrationTest / javaOptions ++= Seq(
      s"-javaagent:$projectRootDir/opentelemetry-agent-for-testing-$OpentelemetryAlphaMinor0Version.jar",
      s"-Dotel.javaagent.extensions=${assembly.value.absolutePath}",
      "-Dotel.javaagent.debug=false",
      "-Dotel.metric.export.interval=50", // 100 ms so that the "eventually" assertions could catch up
      "-Dotel.javaagent.testing.fail-on-context-leak=true",
      "-Dotel.javaagent.testing.transform-safe-logging.enabled=true",
      "-Dotel.javaagent.testing.exporter.temporality=CUMULATIVE",
      "-Dmesmer.akka.persistence.templated=false",

      // suppress repeated logging of "No metric data to export - skipping export."
      // since PeriodicMetricReader is configured with a short interval
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.opentelemetry.sdk.metrics.export.PeriodicMetricReader=INFO",

      // suppress a couple of verbose ClassNotFoundException stack traces logged at debug level
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.internal.ServerImplBuilder=INFO",
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.internal.ManagedChannelImplBuilder=INFO",
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.perfmark.PerfMark=INFO",
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.Context=INFO",
      // disable automatic self PushMetrics invocation
//      "-Dio.scalac.mesmer.akka.streams.collect-interval=5m"
    )
  )
  .dependsOn(core % "provided->compile;compile->compile", testkit % "it,test")

def exampleCommonSettings = Seq(
  publish / skip := true,
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  run / javaOptions ++= Seq(
    s"-javaagent:$projectRootDir/opentelemetry-javaagent-$OpentelemetryMinor0Version.jar",
    s"-Dotel.javaagent.extensions=${(otelExtension / assembly).value.absolutePath}",
    "-Dotel.javaagent.debug=true"
  ),
  libraryDependencies ++= {
    logback ++ Seq(
      "io.grpc"             % "grpc-netty-shaded" % "1.54.0",
      "org.wvlet.airframe" %% "airframe-log"      % AirframeVersion
    )
  },
  run / fork         := true,
  run / connectInput := true
)

lazy val exampleAkka = (project in file("examples/akka"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(exampleCommonSettings)
  .settings(
    name := "mesmer-akka-example",
    libraryDependencies ++= {
      akka ++
      scalatest.map(_ % "test") ++
      akkaTestkit.map(_ % "test") ++
      akkaPersistance ++ Seq(
        "io.circe"                      %% "circe-core"                        % CirceVersion,
        "io.circe"                      %% "circe-generic"                     % CirceVersion,
        "io.circe"                      %% "circe-parser"                      % CirceVersion,
        "de.heikoseeberger"             %% "akka-http-circe"                   % "1.39.2",
        "org.postgresql"                 % "postgresql"                        % PostgresVersion,
        "com.typesafe.slick"            %% "slick"                             % SlickVersion,
        "com.typesafe.slick"            %% "slick-hikaricp"                    % SlickVersion,
        "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
        "com.lightbend.akka.management" %% "akka-management"                   % AkkaManagementVersion,
        "com.lightbend.akka.management" %% "akka-management-cluster-http"      % AkkaManagementVersion,
        "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion
      )
    },
    run / javaOptions ++= Seq(
      s"-Dotel.service.name=mesmer-example",
      s"-Dotel.metric.export.interval=5000"
    )
  )
  .dependsOn(core)

lazy val exampleAkkaStream = (project in file("examples/akka-stream"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(exampleCommonSettings)
  .settings(
    name := "mesmer-akka-stream-example",
    libraryDependencies ++= akka,
    run / javaOptions ++= Seq(
      s"-Dotel.service.name=mesmer-stream-example",
      s"-Dotel.metric.export.interval=5000"
    )
  )
  .dependsOn(core)

lazy val exampleZio = (project in file("examples/zio"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(exampleCommonSettings)
  .settings(
    name := "mesmer-zio-example",
    libraryDependencies ++= zio,
    run / javaOptions ++= Seq(
      s"-Dotel.service.name=mesmer-zio-example",
      s"-Dotel.metric.export.interval=1000"
    )
  )
  .dependsOn(core)

lazy val docs = project
  .in(file("mesmer-docs")) // important: it must not be docs/
  .settings(
    moduleName := "mesmer-docs"
  )
  .dependsOn(otelExtension)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)

lazy val assemblyMergeStrategySettings = assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", _ @_*)           => MergeStrategy.concat
  case PathList("META-INF", _ @_*)                       => MergeStrategy.discard
  case PathList("reference.conf")                        => MergeStrategy.concat
  case PathList("jackson-annotations-2.10.3.jar", _ @_*) => MergeStrategy.last
  case PathList("jackson-core-2.10.3.jar", _ @_*)        => MergeStrategy.last
  case PathList("jackson-databind-2.10.3.jar", _ @_*)    => MergeStrategy.last
  case PathList("jackson-dataformat-cbor-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-datatype-jdk8-2.10.3.jar", _ @_*) => MergeStrategy.last
  case PathList("jackson-datatype-jsr310-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-module-parameter-names-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-module-paranamer-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case _ => MergeStrategy.first
}
