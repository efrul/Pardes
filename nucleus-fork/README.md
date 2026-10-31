# Nucleus's decorated-window-tao, from efrul/Nucleus

Pardes for desktop takes one Nucleus module, `nucleus.decorated-window-tao`, from the fork
[efrul/Nucleus](https://github.com/efrul/Nucleus), branch `commentator-tearoff`, and every other
Nucleus module from the release it branched from, `3.0.0-dev-202610272317`, on Maven Central.

What the fork adds: `Modifier.satelliteTearOffHandle` and `SatelliteDragOrigin.HostElement` (a
satellite torn off an element of a host window: a commentator's column header in the commentaries
pane, see `ReaderPane.Commentator` and `CommentatorTearOffs`), and pointer-icon parameters for it and
for `Modifier.satelliteDragHandle` (Windows has no grabbing hand). No native code changed: the jar
carries the release's native libraries.

`maven/` is a Maven repository with that one module, version `3.0.0-dev-202610272317-1-tearoff`
(`nucleusTao` in `gradle/libs.versions.toml`, read through `settings.gradle.kts`). Its POM and Gradle
module ask for the release's other modules, and it wins over the release's tao the other modules ask
for, being the higher version. So every build (CI, the release workflow, a developer's) works with no
step of its own.

To update it, in a clone of the fork:

```bash
scripts/publish-tearoff-local.sh                      # tao from source into Maven Local
python3 <Pardes>/nucleus-fork/vendor.py               # the jar, POM and module into maven/
```

Once upstream Nucleus has the API, delete this folder, its repository in `settings.gradle.kts` and
`nucleusTao`, and take the Nucleus release that has it.
