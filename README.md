| Project Stage | CI | Release | Snapshot |
| --- | --- | --- | --- |
| [![Project stage][Stage]][Stage-Page] | ![Scala CI][Badge-CI] | [![Release Artifacts][badge-releases]][link-releases] | [![Snapshot Artifacts][badge-snapshots]][link-snapshots] |

# Mesmer

Mesmer is an [OpenTelemetry](https://opentelemetry.io/) instrumentation library for Scala
applications.

Compatibility:
- Scala: 2.13.x
- JVM: 1.11+
  
See the [docs](https://scalaconsultants.github.io/mesmer/) for more information.

## Contributors
### Local testing

`example` subproject contains a test application that uses Akka Cluster sharding with Mesmer Akka extension.
Go [here](example/README.md) for more information.

### Contributor setup

1. You're encouraged to use
   the [sbt native client](https://www.scala-sbt.org/1.x/docs/sbt-1.4-Release-Notes.html#Native+thin+client). It will
   speed up your builds and your pre-commit checks (below). Just set ` export SBT_NATIVE_CLIENT=true` and sbt will use
   the native client.
2. Install [pre-commit](https://pre-commit.com/)
3. Run `pre-commit install`
4. If you're using Intelij Idea:
    - Download "google-java-format" plugin and use it
    - Go to "Editor" -> "Code Style" -> "YAML". Uncheck "Indent sequence value" and "Brackets" (in the "Spaces" menu)

### Documentation

Mesmer project uses [Docusaurus v2](https://docusaurus.io/) with [mdoc](https://scalameta.org/mdoc/) to produce
type-checked documentation. All is configured with the [sbt-mdoc](https://scalameta.org/mdoc/docs/installation.html#sbt)
plugin according to this [document](https://scalameta.org/mdoc/docs/docusaurus.html).

There are 3 directories relevant to the process:

- `website/` - Docusaurus application
- `docs/` - markdown pages with the documentation
- `mesmer-docs/` - markdown pages compiled by mdoc

To run Docusaurus locally:

- install node (version >= 14) and yarn
- go to the "website" directory:

```sh
cd website
```

- run the following:

```sh
yarn
yarn run start
```

To see the documentation changes in your running Docusaurus instance you need to recompile with the following command:

```sh
sbt docs/mdoc
```

This will put them into `mesmer-docs/target/mdoc` where the Docusaurus can pick them up (the location where Docusaurus
looks for these pages is configured in `website/docusaurus.config.js`)

The homepage (in case you need to make changes to it) resides in `website/src/pages/index.js`.

[Badge-CI]: https://github.com/ScalaConsultants/mesmer/workflows/Scala%20CI/badge.svg

[badge-releases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/io.scalac/mesmer-akka-extension_2.13 "Sonatype Releases"

[badge-snapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/io.scalac/mesmer-akka-extension_2.13 "Sonatype Snapshots"

[link-releases]: https://oss.sonatype.org/content/repositories/releases/io/scalac/mesmer-akka-extension_2.13/ "Sonatype Releases"

[link-snapshots]: https://oss.sonatype.org/content/repositories/snapshots/io/scalac/mesmer-akka-extension_2.13/ "Sonatype Snapshots"

[Stage]: https://img.shields.io/badge/Project%20Stage-Development-yellowgreen.svg

[Stage-Page]: https://github.com/zio/zio/wiki/Project-Stages
