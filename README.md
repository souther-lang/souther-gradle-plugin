# souther-gradle-plugin

Compiles [Souther](https://github.com/souther-lang/souther) sources in a Gradle build.

Wiring this by hand took about forty lines, three of them load-bearing in a way nothing reported.
Without `inputs.dir`, editing a `.sou` left the task up to date and the build produced the
previously generated classes. Without `jar { from }`, the jar was empty. Both builds succeeded. This
plugin is what replaces that.

## Use it

```kotlin
plugins {
    java
    id("org.souther-lang.souther") version "0.2.0"
}

souther {
    southerVersion = "0.2.0"
}
```

That is the whole of it. `.sou` under `src/main/souther` is compiled, the generated classes go into
the jar and onto the test compile class path, and the runtime that generated code calls is added at
the version of the Souther that compiled the model — so the version above is the only one, and there
is no second to keep in step.

Java or Kotlin written beside the model can name it:

```java
import shared.money.Amount;
import souther.runtime.Result;
```

Editing a `.sou` re-runs the compile. Nothing edited is up to date. The build works with the
configuration cache.

## Importing another project's model

A dependency is all it takes. The module another project compiled is read from its classes, so
nothing has to be configured and no `.sou` is shared:

```kotlin
dependencies { implementation(project(":money")) }
```

```text
module app.orders exposing ( Order )

import shared.money ( Amount )

data Order = { total: Amount }
```

## Kotlin

Kotlin compiles against the model with no source set of its own. One thing is worth knowing: Kotlin
has no JVM target 25 yet and falls back to 24, while `javac` defaults to the JDK it is running on,
and Gradle refuses the two being different. Say what you target:

```kotlin
java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}
```

## Choosing a Souther

A project with a model names the Souther it compiles with:

```kotlin
souther {
    southerVersion = "0.2.0"
}
```

There is no default. One would be a Souther chosen by whoever released this plugin, and the next
Souther release would leave it behind while a build that named nothing went on saying nothing — so a
project with a model and no version is stopped rather than compiled with a version it never read.
A project with no `.sou` names nothing and resolves nothing.

For a build that would rather have whatever is newest than say which, `latest.release` is a version
like any other and Gradle resolves it that way. Written in the build script, so that a build whose
compiler changes with the calendar says so on the page.

The compiler is not on this plugin's class path. What the version names is
`org.souther-lang:souther-build-driver`, resolved from the repositories your project already uses
and run in a worker under `classLoaderIsolation` behind
[`souther-build-api`](https://github.com/souther-lang/souther-build-api). So the compile meets the
libraries that Souther was released against rather than the ones Gradle and this plugin happen to
carry, and the plugin and the Souther it runs are released on their own terms. This plugin names no
Souther anywhere, so a release of it is for this plugin or for the build protocol moving, and never
for a Souther release.

## Configuration

```kotlin
souther {
    southerVersion = "0.2.0"                            // no default: a project with a model names it
    sourceDirectory = layout.projectDirectory.dir("model")  // default: src/main/souther
    language = "ja"                                     // default: what a command line naming none gets
}
```

The task is `compileSouther`, and its output is part of the `main` source set.

## Design

[souther-lang/souther#137](https://github.com/souther-lang/souther/issues/137).

## License

Copyright © kawasima 2026

Released under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
