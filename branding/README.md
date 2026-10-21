# Branding: Pardes for desktop over Zayit's code

The desktop app in this repository is upstream [Zayit](https://github.com/kdroidFilter/Zayit) (merged as released,
`git merge v<x.y.z>`) built as **Pardes for desktop** (פרדס למחשב). Pardes is based on Zayit by Elie (Elyahou)
Gambache and is not on his behalf: he did not make it, is not responsible for it and gives it no support. At his
request a fork does not use Zayit's name or icon, so this folder holds everything that makes the build Pardes, and
upstream's files keep Zayit's values. A new Zayit release then merges against a few hooks instead of a renamed app.

## How it applies

- `brand.gradle`, applied by the last line of `SeforimApp/build.gradle.kts`, picks the brand
  (`-Pseforimapp.brand=pardes`, the default, or `zayit`) and, for Pardes:
  - sets the packaging values of `nucleus.application` from `<brand>/brand.properties` (name, package and image name,
    vendor, homepage, release repository, Linux maintainer and WM class, Windows upgrade id, icons);
  - removes the OS URL scheme: `zayit://` belongs to Zayit, and two apps registering it would clash;
  - fails if one of Zayit's private add-ons (smart siddur, semantic search, quote links) would be compiled: their
    licence is for Zayit only;
  - lays `<brand>/composeResources` over `SeforimApp/src/commonMain/composeResources` and `<brand>/assets` over
    `src/jvmMain/assets` (task `BrandResources` in buildSrc). A file replaces the file at the same path; the brand's
    `values/strings.xml` holds only the strings that change, each replacing upstream's string of that name. The build
    fails when upstream no longer has a file or a string the brand replaces.
- `<brand>/kotlin` is compiled with the app: `Brand` holds the values the app reads at run time (the data folder's
  id, the preferences node, ...). The app's code reads `Brand.*` where upstream has a literal; `zayit/kotlin` has
  upstream's values, so `-Pseforimapp.brand=zayit` builds Zayit as released.

## The hooks in upstream files

Keep this list short and exact; every line here is a possible merge conflict.

| File | Hook |
|---|---|
| `SeforimApp/build.gradle.kts` | last line: `apply(from = rootProject.file("branding/brand.gradle"))` |
| `SeforimApp/.../main.kt` | `val appId = Brand.APP_ID` (FileKit's data folder) |
| `SeforimApp/.../framework/di/modules/AppCoreBindings.kt` | `provideSettings()` returns `Brand.settings()` |
| `SeforimApp/.../framework/backup/PendingUserDataRestore.kt` | imports preferences into `Brand.settings()` |

Licence-restricted files are replaced in place, not overlaid, because they must not be in the repository at all:
the Rashi Amiti font (removed, with its `FontCatalog` entry; `FontCatalogTest` counts 27 fonts) and the temple
picture (`files/temple_jerusalem_in_fire.webp`, a public-domain painting by David Roberts instead of a Shutterstock
photo). When a Zayit merge conflicts there, keep Pardes's side.

## Merging a Zayit release

1. `git fetch --no-tags https://github.com/kdroidFilter/zayit tag v<x.y.z>` and `git merge v<x.y.z>` (never rebase,
   never push upstream's `v*` tags).
2. Keep removed what Pardes removed: Zayit's release, site and CLA workflows, FUNDING, dependabot, Rashi Amiti, the
   Shutterstock picture.
3. Build: `BrandResources` fails if a replaced file or string moved upstream; update `<brand>/` accordingly.
4. Check that `-Pseforimapp.brand=zayit` still compiles.
