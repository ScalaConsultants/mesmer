import Dependencies._

lazy val scala213 = "2.13"

inThisBuild(
  List(
    scalaVersion := "2.13.6",
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
        "worekleszczy",
        "Piotr Jósiak",
        "piotr.josiak@gmail.com",
        url("https://github.com/worekleszczy")
      ),
      Developer(
        "lgajowy",
        "Łukasz Gajowy",
        "lukasz.gajowy@gmail.com",
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

addCommandAlias("fmt", "scalafmtAll; scalafixAll")
addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll")
addCommandAlias("testAll", "test; IntegrationTest/test")

val projectRootDir = all.base.absolutePath

lazy val all: Project = (project in file("."))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name           := "mesmer-all",
    publish / skip := true
  )
  .aggregate(extension, otelExtension, example, core)

lazy val core = (project in file("core"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name := "mesmer-akka-core",
    libraryDependencies ++= {
      akka ++
      openTelemetryInstrumentation ++
      scalatest.map(_ % "test") ++
      akkaTestkit.map(_ % "test")
    }
  )

lazy val extension = (project in file("extension"))
  .enablePlugins(MultiJvmPlugin)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .configs(MultiJvm)
  .settings(
    Test / parallelExecution := true,
    name                     := "mesmer-akka-extension",
    libraryDependencies ++= {
      akka ++
      openTelemetryApi ++
      akkaTestkit.map(_ % "test") ++
      scalatest.map(_ % "test") ++
      akkaMultiNodeTestKit.map(_ % "test") ++
      logback.map(_ % Test)
    }
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val otelExtension = (project in file("otel-extension"))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    name                                               := "mesmer-otel-extension",
    excludeDependencies += "io.opentelemetry.javaagent" % "opentelemetry-javaagent-bootstrap",
    libraryDependencies ++= {
      zio.map(_ % "provided") ++
      openTelemetryExtension.map(_ % "provided") ++
      openTelemetryMuzzle.map(_ % "provided") ++
      openTelemetryInstrumentation.map(_ % "provided") ++
      openTelemetryInstrumentationApiSemanticConventions ++
      byteBuddy.map(_ % "provided") ++
      akkaTestkit.map(_ % "it,test") ++
      scalatest.map(_ % "it,test") ++
      openTelemetryTesting.map(_ % "it,test")
    },
    assembly / test            := {},
    assembly / assemblyJarName := s"${name.value}_${scalaBinaryVersion.value}-${version.value}-assembly.jar",
    assemblyMergeStrategySettings,
    assembly / artifact := {
      val art = (assembly / artifact).value
      art.withClassifier(Some("assembly"))
    },
    addArtifact(assembly / artifact, assembly),
    Test / fork              := true,
    Test / parallelExecution := true,
    Test / testGrouping := ((Test / testGrouping).value) flatMap { group =>
      group.tests.map { test =>
        Tests.Group(name = test.name, tests = Seq(test), runPolicy = group.runPolicy)
      }
    },
    Test / testOnly / testGrouping      := (Test / testGrouping).value,
    IntegrationTest / parallelExecution := false,
    IntegrationTest / fork              := true,
    IntegrationTest / javaOptions ++= Seq(
      s"-javaagent:$projectRootDir/opentelemetry-agent-for-testing-$OpentelemetryAlphaVersion131.jar",
      s"-Dotel.javaagent.extensions=${assembly.value.absolutePath}",
      "-Dotel.javaagent.debug=false",
      "-Dotel.metric.export.interval=100", // 100 ms so that the "eventually" assertions could catch up
      "-Dotel.javaagent.testing.fail-on-context-leak=true",
      "-Dotel.javaagent.testing.transform-safe-logging.enabled=true",
      "-Dotel.metrics.exporter=otlp",
      "-Dmesmer.akka.persistence.templated=false",

      // suppress repeated logging of "No metric data to export - skipping export."
      // since PeriodicMetricReader is configured with a short interval
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.opentelemetry.sdk.metrics.export.PeriodicMetricReader=INFO",

      // suppress a couple of verbose ClassNotFoundException stack traces logged at debug level
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.internal.ServerImplBuilder=INFO",
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.internal.ManagedChannelImplBuilder=INFO",
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.perfmark.PerfMark=INFO",
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.Context=INFO"
    )
  )
  .dependsOn(core % "provided->compile;test->test;compile->compile;it->test")

lazy val example = (project in file("example"))
  .enablePlugins(JavaAppPackaging, UniversalPlugin)
  .settings(
    name           := "mesmer-akka-example",
    publish / skip := true,
    libraryDependencies ++= {
      akka ++
      scalatest.map(_ % "test") ++
      akkaTestkit.map(_ % "test") ++
      akkaPersistance ++
      logback ++
      Seq(
        "io.circe"                      %% "circe-core"                                % CirceVersion,
        "io.circe"                      %% "circe-generic"                             % CirceVersion,
        "io.circe"                      %% "circe-parser"                              % CirceVersion,
        "de.heikoseeberger"             %% "akka-http-circe"                           % "1.39.2",
        "dev.zio"                       %% "zio"                                       % "2.0.5",
        "org.postgresql"                 % "postgresql"                                % PostgresVersion,
        "com.typesafe.slick"            %% "slick"                                     % SlickVersion,
        "com.typesafe.slick"            %% "slick-hikaricp"                            % SlickVersion,
        "com.typesafe.akka"             %% "akka-discovery"                            % AkkaVersion,
        "com.lightbend.akka.management" %% "akka-management"                           % AkkaManagementVersion,
        "com.lightbend.akka.management" %% "akka-management-cluster-http"              % AkkaManagementVersion,
        "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap"         % AkkaManagementVersion,
        "io.opentelemetry"               % "opentelemetry-sdk-extension-autoconfigure" % OpentelemetryAlphaVersion130,
        "io.grpc"                        % "grpc-netty-shaded"                         % "1.48.1",
        "org.wvlet.airframe"            %% "airframe-log"                              % AirframeVersion
      )
    },
    assemblyMergeStrategySettings,
    mainClass                  := Some("example.Boot"),
    assembly / assemblyJarName := "mesmer-akka-example.jar",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    run / fork := true,
    run / javaOptions ++= {
      val properties = System.getProperties

      import scala.collection.JavaConverters._
      val keys = for {
        (key, value) <- properties.asScala.toList if value.nonEmpty
      } yield s"-D$key=$value"

      keys
    },
    commands += runExampleWithOtelAgent,
    commands += runStreamExampleWithOtelAgent,
    commands += runZioExampleWithOtelAgent
  )
  .dependsOn(core)

lazy val docs = project
  .in(file("mesmer-docs")) // important: it must not be docs/
  .settings(
    moduleName := "mesmer-docs"
  )
  .dependsOn(extension, otelExtension)
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

def runExampleWithOtelAgent = Command.command("runExampleWithOtelAgent") { state =>
  val extracted = Project extract state
  val newState = extracted.appendWithSession(
    Seq(
      run / javaOptions ++= Seq(
        s"-javaagent:$projectRootDir/opentelemetry-javaagent-$OpentelemetryLatestVersion.jar",
        s"-Dotel.service.name=mesmer-example",
        s"-Dotel.metric.export.interval=5000",
        s"-Dotel.javaagent.extensions=${(otelExtension / assembly).value.absolutePath}",
        "-Dotel.javaagent.debug=false"
      )
    ),
    state
  )
  val (s, _) =
    Project.extract(newState).runInputTask(Compile / runMain, " example.Boot", newState)
  s
}

def runStreamExampleWithOtelAgent = Command.command("runStreamExampleWithOtelAgent") { state =>
  val extracted = Project extract state
  val newState = extracted.appendWithSession(
    Seq(
      run / javaOptions ++= Seq(
        s"-javaagent:$projectRootDir/opentelemetry-javaagent-$OpentelemetryLatestVersion.jar",
        s"-Dotel.service.name=mesmer-stream-example",
        s"-Dotel.metric.export.interval=5000",
        s"-Dotel.javaagent.extensions=${(otelExtension / assembly).value.absolutePath}"
      )
    ),
    state
  )
  val (s, _) =
    Project.extract(newState).runInputTask(Compile / runMain, " example.SimpleStreamExample", newState)
  s
}

def runZioExampleWithOtelAgent = Command.command("runZioExampleWithOtelAgent") { state =>
  val extracted = Project extract state
  val newState = extracted.appendWithSession(
    Seq(
      run / javaOptions ++= Seq(
        s"-javaagent:$projectRootDir/opentelemetry-javaagent-$OpentelemetryLatestVersion.jar",
        s"-Dotel.service.name=mesmer-zio-example",
        s"-Dotel.metric.export.interval=1000",
        s"-Dotel.javaagent.extensions=${(otelExtension / assembly).value.absolutePath}"
      )
    ),
    state
  )
  val (s, _) =
    Project.extract(newState).runInputTask(Compile / runMain, " example.SimpleZioExample", newState)
  s
}
