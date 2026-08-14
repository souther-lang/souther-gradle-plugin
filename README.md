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
    id("org.souther-lang.souther") version "0.1.0"
}
```

That is the whole of it. `.sou` under `src/main/souther` is compiled, the generated classes go into
the jar and onto the test compile class path, and the runtime that generated code calls is added at
the version of the Souther that compiled the model — so there is no second version to keep in step.

Java or Kotlin written beside the model can name it:

```java
import shared.money.Amount;
import souther.runtime.Result;
```

Editing a `.sou` re-runs the compile. Nothing edited is up to date. The build works with the
configuration cache.

## Choosing a Souther

A plugin release is verified against one Souther, and that is what a project naming no version gets.
To compile with another:

```kotlin
souther {
    southerVersion = "0.1.0-rc5"
}
```

The compiler is not on this plugin's class path. What the version names is
`org.souther-lang:souther-build-driver`, resolved from the repositories your project already uses
and run in a worker under `classLoaderIsolation` behind
[`souther-build-api`](https://github.com/souther-lang/souther-build-api). So the compile meets the
libraries that Souther was released against rather than the ones Gradle and this plugin happen to
carry, and the plugin and the Souther it runs are released on their own terms: a Souther release
needs no plugin release unless the build protocol moves with it.

## Configuration

```kotlin
souther {
    southerVersion = "0.1.0-rc5"                        // default: what this release was verified against
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
