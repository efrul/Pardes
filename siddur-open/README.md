# siddur-open

The engine of Pardes's experimental smart siddur: a copy of
[SeforimOpenSiddur](https://github.com/arieldaniely/SeforimOpenSiddur) by Ariel Daniely, who offered it to Pardes
([efrul/Pardes#27](https://github.com/efrul/Pardes/issues/27)). It builds the day's tefilot from the siddurim the
library already holds, by the nusach, the calendar, the place and the user's choices, and needs neither a database of
its own nor Zayit's private siddur package. `UPSTREAM_README.md` is its own README, with its sources and rules.

Powered by the technologies that drive Zayit — https://zayitapp.com/

## What is here

| | |
|---|---|
| Source | https://github.com/arieldaniely/SeforimOpenSiddur |
| Commit | `cbf530ccb89f50433a980dc7f16ae219b2cf556c` (2026-11-08), version 0.1.0 |
| Copied as they are | `src/` (15 source files, 6 test files, 3 data files) but for the three files below, `tools/`, `LICENSE`, `COPYING.AMUD`, `COPYING.HEBCAL`, and `README.md` as `UPSTREAM_README.md` |
| Changed by Pardes | `IsraeliPrayerCustom.kt`, `SiddurCalendar.kt` and `IsraeliOccasionTest.kt` (2026-11-11, see below) |
| Left out | its Gradle wrapper, `build.gradle.kts`, `settings.gradle.kts`, `.gitattributes` and `.gitignore` |
| Pardes's own | this file, `build.gradle.kts` and `src/jvmTest/…/OrdinaryWeekdayCustomTest.kt` |

A copy in the repository, not a submodule or a published package: a release builds without reaching another
repository, and the engine's source is in the release's source archive.

`build.gradle.kts` compiles the copy as the `:siddur-open` module with Pardes's Kotlin, toolchain and versions of the
engine's two dependencies (kotlinx-datetime and KosherKotlin, the same versions as the engine's own build). The
sources stay as their author wrote them, so ktlint and detekt are turned off for this module. `tools/` is kept as the
source of the data files and is never run by the build.

## Changed in Pardes's copy

Modified on 2026-11-11, at the user's word: the engine left Tachanun out on Yom HaAtzmaut and Yom Yerushalayim under
each of its four customs, and Pardes wanted the choice of an ordinary weekday, as its default.

- `IsraeliPrayerCustom.kt`: a fifth custom, `ORDINARY_WEEKDAY`, first in the enum, and `keepsTheDays`, false for it
  alone. `saysHallel` is false under it.
- `SiddurCalendar.kt` (`flagsFor`): under it the days get none of their flags and Tachanun is said as on any weekday,
  on the day and at Mincha of the day before. The rest of the engine reads those flags and `festive`, so it adds
  nothing either.
- `IsraeliOccasionTest.kt`: the loop of `customControlsHallelWithoutChangingTheWeekdayService` runs over the customs
  that keep the days, which is what it asserts.
- `OrdinaryWeekdayCustomTest.kt` is new, and Pardes's.

The engine's own default (`SiddurPrefs.israeliPrayerCustom`, `NO_TACHANUN`) is unchanged. The app passes its own:
ordinary weekdays until the user picks a custom in the siddur's bar, which offers the choice on those days.

## Where it is used

`SeforimApp/addons/siddur/open` holds the siddur's screen and the reader of the library's siddurim
(`LibrarySiddurSource`), from the author's own fork of the app. `branding/brand.gradle` compiles it for Pardes in the
place of `addons/siddur/disabled`. The siddur is experimental and incomplete (see the coverage section of
`UPSTREAM_README.md`), so the app shows it only once the user turns it on in Settings › כללי (`OpenSiddurSwitch`).

## Licences

- The engine: GNU AGPL v3 with the attribution term of section 7b, `LICENSE`, the same text as Pardes's own.
- The conditions of paragraphs and alternatives (`src/jvmMain/resources/open-siddur/amud-annotations.tsv`,
  `amud-inline.tsv`) come from [Amud](https://github.com/Nt-f/amud) and are under GNU GPL v3, `COPYING.AMUD`.
- The table of readings (`readings.tsv.gz`) is made from [Hebcal Leyning](https://github.com/hebcal/hebcal-leyning),
  BSD 2-Clause, `COPYING.HEBCAL`.

The app's licences list (`branding/pardes/assetsExtra/common/licenses/README.md`) names the three, and the build
copies the two notices beside it.

## Updating

1. Copy `src/`, `tools/`, the three licence files and the README from the new commit over these, then make the
   changes of "Changed in Pardes's copy" again (or drop them, if the engine has the ordinary weekday by then).
2. Write the new commit, date and version in the table above.
3. Bring over what the author changed in his screen and `LibrarySiddurSource` since
   (`SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/siddur/` of
   https://github.com/arieldaniely/Zayit), which follow the engine's API.
4. Run `./gradlew :siddur-open:jvmTest`, and the app's siddur tests on a library:
   `SEFORIM_DB=/path/to/seforim.db ./gradlew :SeforimApp:jvmTest --tests "*features.siddur*"`.
