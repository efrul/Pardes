# Spec: a rolling log file, freeze capture and startup timing

- **For:** items 1 to 3 of the proposals the user chose from `docs/INTELLIJ_LESSONS.md` (a rolling log file, freeze capture, startup timing); the choice and its state are in `docs/DESKTOP_STAGE3_PLAN.md`.
- **When:** 2026-11-21. Read-only: nothing was changed, built or run, and no library was at hand; what was not
  checked is marked "(לא נבדק)" in the report.
- **Code read:** efrul/Pardes at `21999b0` (main of that day), with Zayit's submodules at their pins (SeforimLibrary
  `4f07145`, SeforimAcronymizer `dea83b3`); efrul/Nucleus at `65ccd12` and the Nucleus jars Pardes builds against;
  JetBrains/intellij-community at `b2ba8bcb`. The line numbers are those commits'.
- **By:** a general-purpose agent of Claude Code, one of four run side by side. The report is as it came back, in
  Hebrew, but for its headings set one level down and the session's local paths made relative; the brief it got is
  summed up at the end.
- **Checked by hand afterwards:** nothing yet. The user asked for these details before anything is built, and has
  not gone over the open decisions (`docs/DESKTOP_STAGE3_PLAN.md` › ההצעות שנבחרו מדוח IntelliJ).

## The report

<div dir="rtl">

## מפרט מימוש: שלושה כלי אבחון לפרדס (יומן, קפיאות, זמני עלייה)

**מה זה:** מפרט לתכנון בלבד, לפני מימוש. שום קובץ במאגר לא שונה. כל טענה נבדקה בקוד; מה שלא אפשר היה לבדוק כאן מסומן **(לא נבדק)**.

**מול מה נבדק:**
- פרדס בקומיט `21999b0`, מול בסיס זית שבמפת השינויים (v0.6.12, `d7777e2e`).
- Nucleus המקומי בקומיט `65ccd12` (2026-10-27 22:44 UTC; העותק רדוד, מ-2026-09-23), וה-jar-ים שפרדס בונה מולם בפועל, דרך `javap`: `nucleus-fork/maven/dev/nucleusframework/nucleus.decorated-window-tao/3.0.0-dev-202610272317-2-tearoff/nucleus.decorated-window-tao-3.0.0-dev-202610272317-2-tearoff.jar`, ו-`nucleus.nucleus-application-3.0.0-dev-202610272317.jar` שהורד מ-Maven Central.
- intellij-community בקומיט `b2ba8bcb`, בקריאה בלבד (`git show`).
- קובצי המקור שפורסמו של FileKit `0.16.0` ושל slf4j-simple `2.0.20`, הגרסאות שב-`gradle/libs.versions.toml:7` ו-`:39`.

**קיצורים:**
- `S/` הוא `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/`, ו-`T/` הוא `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/`. בטבלאות הנתיבים מלאים.
- `N:` הוא נתיב במאגר Nucleus, ו-`IJ:` נתיב ב-intellij-community.
- טביעת רגל, כמו בדוח: (א) תצורה; (ב) קובץ של פרדס או וו של שורה אחת; (ג) עריכה בתוך קוד של זית; (ד) Nucleus.

**חמישה דברים שהקוד מתקן בדוח** (`docs/INTELLIJ_LESSONS.md:56-60` ושורות 1-2 בטבלה שבשורות 99-100):
1. **ה-watchdog כבר רץ בפרדס ב-Windows.** `TaoApplication.run` מפעיל אותו (`N:decorated-window-tao/src/main/kotlin/dev/nucleusframework/window/tao/TaoApplication.kt:120-128`), הוא דלוק כברירת מחדל (`N:.../TaoEventLoopWatchdog.kt:171-172`), ופרדס לא מכבה אותו. ה-SEVERE שלו, עם המחסניות, הולך היום ל-ConsoleHandler של JUL, כלומר ל-stderr, שלא נראה כשפותחים את התוכנה מהאייקון. פריט 1 לבדו כבר שומר אותו בקובץ.
2. **לא צריך וו בתוך `nucleusApplication`.** `TaoApplication.onUnresponsive`, `onResponsive` ו-`expectUnresponsive` ציבוריים ב-jar שפרדס בונה מולו, ונרשמים בכל רגע (שדות `@Volatile` פשוטים, `TaoApplication.kt:592-633`). הגרסאות שב-`NucleusApplicationScope` רק מעבירות אליהם (`N:nucleus-application/src/main/kotlin/dev/nucleusframework/application/NucleusApplicationScope.kt:82, 89, 108`).
3. **אין היום פעולה שצריך לעטוף ב-`expectUnresponsive {}`.** העברת הספרייה רצה ב-`Dispatchers.IO` (`S/features/database/move/LibraryMoveWindow.kt:71`). השחזור רץ בהרכבה הראשונה, לפני שיש חלון (`S/main.kt:200`), וה-watchdog ישן כל זמן שאין חלון רשום (`TaoEventLoopWatchdog.kt:509-512`).
4. **"האינדקס נפתח" אינו רגע נפרד בקוד.** Lucene פותח את התיקייה ואת ה-searcher בעצלות (`SeforimLibrary/search/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/search/LuceneSearchEngine.kt:186-194, 228-242`), בתוך `warmUp()` (`:292-301`) או בחיפוש הראשון. אסור לגעת ב-SeforimLibrary (`.claude/skills/zayit-fidelity/SKILL.md:78-79`), ולכן נמדד "האינדקס נפתח והתחמם".
5. **השורות ב-`main.kt` זזו** מאז הערות המחקר: `isDevEnv` נקבע היום ב-`S/main.kt:166-167`, הגרף נוצר ב-`:221-228`, ו-`warmCatalog` נמצא ב-`:526-531`.

---

#### 1. קובץ יומן מתגלגל

**בקצרה:** התוכנה כותבת תמיד יומן מתגלגל, `pardes.log` ועד ארבעה קבצים ישנים של 5MB, בתיקיית הנתונים של המותג. נכנסים אליו כל מה ש-Nucleus, Lucene ו-ONNX Runtime כותבים דרך `java.util.logging`, והודעות האפליקציה עצמה דרך וו קטן במודול `logger` של זית. בעמוד המשוב שבהגדרות נוספים מתג "יומן מפורט" וכפתור "פתיחת תיקיית היומנים".

**מה רואים:**

*בדיסק.* תיקייה `logs` בתוך `FileKit.filesDir`, ליד התיקיות `databases` (`FileKit.databasesDir` הוא `filesDir/databases`) ו-`fonts` (`S/core/presentation/typography/UserFonts.kt:133`). לפי FileKit 0.16.0 (`FileKit.jvm.kt:85-107`) ו-`Brand.APP_ID` (`branding/pardes/kotlin/io/github/kdroidfilter/seforimapp/branding/Brand.kt:13`), התיקייה היא:
- Windows: `%APPDATA%\io.github.efrul.pardes\logs\`
- macOS: `~/Library/Application Support/io.github.efrul.pardes/logs/`
- Linux: `$XDG_DATA_HOME/io.github.efrul.pardes/logs/`, ובלעדיו `~/.local/share/io.github.efrul.pardes/logs/`
- בהרצת E2E ‏(`ZAYIT_E2E_OUT`): `$ZAYIT_E2E_OUT/logs`, כמו `TabThumbnailStore` (`S/framework/di/modules/AppCoreBindings.kt:284`).

*הקבצים.* `pardes.log` הוא הנוכחי, ו-`pardes.1.log` עד `pardes.4.log` הם הישנים (1 החדש שבהם). אין קובץ נעילה. כל הפעלה ממשיכה את אותו קובץ, וכשהוא עובר 5,000,000 בתים הוא מתגלגל, כך שעל הדיסק יש לכל היותר כ-25MB.

*השורות.* באנגלית, כמו ההודעות שכבר בקוד. כל שורה: תאריך ושעה, אלפיות שנייה מתחילת ההפעלה, רמה (`ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`), החוט, המקור (`app` להודעות האפליקציה, או שם הלוגר של JUL בקיצור), וההודעה. מחסנית של חריגה באה בשורות שאחריה. התיקייה הביתית מוחלפת ב-`~`. דוגמה, בתחילת הפעלה:
```
2026-11-21 21:03:04.120 [      0] INFO  main app - ------------------------------ started ------------------------------
2026-11-21 21:03:04.121 [      1] INFO  main app - Version 1.0.10, native image; Windows 11 10.0 amd64; 8 cores; heap up to 4096 MB
2026-11-21 21:03:04.121 [      1] INFO  main app - Locale he_IL, time zone Asia/Jerusalem (+02:00), PID 4242
2026-11-21 21:03:04.122 [      2] INFO  main app - Log ~\AppData\Roaming\io.github.efrul.pardes\logs, detailed: no
2026-11-21 21:03:05.901 [   1781] INFO  catalog-warmup app - [CatalogCache] Precomputed catalog loaded: 1234 categories, 7000 books
2026-11-21 21:09:40.002 [ 395882] SEVERE nucleus-tao-watchdog d.n.w.t.TaoEventLoopWatchdog - Tao event loop has not pumped messages for at least 5000 ms — the UI is frozen. Thread dump follows.
```
בסגירה רגילה נכתבת שורה `------ ended ------`.

*בהגדרות › משוב* (`S/features/settings/feedback/FeedbackSettingsScreen.kt`), אחרי הכרטיסים של העמוד (`FeedbackSettingsContent`, שורה 83) ולפני הקישור לתיקיית הסימונים של צורת הדף (שורה 84):
- כרטיס בצורת `SettingCard` (`S/features/settings/ui/SettingsComponents.kt:31-84`): תיבת סימון "יומן מפורט", וחץ שפותח את ההסבר שמתחתיה.
- כפתור `OutlinedButton` "פתיחת תיקיית היומנים", כמו הכפתור "פתיחת התיקייה" של המשוב (`S/features/settings/feedback/FeedbackSendCard.kt:362`). לידו, בכתב קטן, "תיקיית היומנים: …" עם הנתיב המלא, מבודד משמאל לימין ב-`isolatedPath` (`S/features/settings/library/LibraryLocationCard.kt:216-217`).
- הסימון חל מיד, בלי הפעלה מחדש. הוא נשמר עד שמורידים אותו, וכברירת מחדל הוא כבוי.
- הקונסולה לא משתנה: `SEFORIMAPP_LOGGING` ממשיך לקבוע מה מודפס בה (`S/main.kt:166-167`).

*המחרוזות המוצעות*, בבלוק חדש ב-`SeforimApp/src/commonMain/composeResources/values/strings.xml` אחרי שורה 301, בסוף הבלוק של שליחת המשוב ולפני ההערה של שאלת ההסכמה (שורה 302), עם הערה `<!-- Pardes: the app's log, in Settings › משוב (DiagnosticsLog) -->`. הניסוח הולך אחרי הסגנון של העמוד ("פתיחת תיקיית הסימונים של צורת הדף", שורה 47):

| מפתח | טקסט |
|---|---|
| `diagnostics_verbose_log` | יומן מפורט |
| `diagnostics_verbose_log_hint` | התוכנה שומרת במחשב הזה יומן של הודעות, שגיאות וקפיאות, כדי שאפשר יהיה להבין מה קרה כשמשהו משתבש. כשהאפשרות מסומנת נכתבים בו גם פרטים נוספים: אילו ספרים, מקומות וחיפושים נפתחו, וכמה זמן לקח כל חיפוש. היומן עשוי להכיל נתיבים של קבצים ושמות של ספרים, של מילונים ושל שולחנות עבודה, ובמצב המפורט גם חיפושים. הוא לא יוצא מהמחשב: שולחים אותו רק כשמצרפים אותו לפנייה. נשמרים לכל היותר חמישה קבצים של 5MB. |
| `diagnostics_open_logs_folder` | פתיחת תיקיית היומנים |
| `diagnostics_logs_folder` | תיקיית היומנים: %1$s |

**איך זה עובד:**

1. **וו אחד ב-`main.kt`.** אחרי שורה 167 (`isDevEnv = …`) ולפני `PlatformInfo.warmUp()` (שורה 175):
   ```kotlin
   // Pardes: the log file (DiagnosticsLog), before anything worth logging runs
   DiagnosticsLog.install()
   ```
   - המקום אחרי ענף ה-CLI (`S/main.kt:160-164`), ולכן `zayit cli` לא כותב ליומן.
   - והוא לפני `nucleusApplication` (שורה 183), ולכן נכנסות ליומן גם ההודעות של האתחול של Nucleus (GraalVM, מופע יחיד, ספריות native). ואלה בדיוק הכשלונות שמאחורי "התוכנה לא עולה".
   - הוא רץ לפני ש-`GraalVmInitializer.initialize()` מכין את ה-native image, ולכן יש לזה סעיף בסיכונים ובהחלטה 1.יג.
   - אותו וו מפעיל גם את פריט 2 ואת השעון של פריט 3.
2. **`DiagnosticsLog.install()`**, בקובץ חדש של פרדס:
   1. התיקייה: `FileKit.init(Brand.APP_ID)`, ואז `File(FileKit.filesDir.path, "logs")`.
      - `init` רק קובע שדות (`FileKit.jvm.kt:46-50`), ו-`main.kt:191` קורא לו שוב באותו ערך.
      - גם Nucleus מאתחל את FileKit בעצמו, רק אם הוא עוד לא אותחל (`N:nucleus-application/src/main/kotlin/dev/nucleusframework/application/internal/FileKitIntegration.kt:25-46`, שנקרא מ-`NucleusApplication.kt:111-112`; קיים גם ב-jar שפרדס בונה מולו). קריאה מוקדמת עם `Brand.APP_ID` רק מקדימה את מה ש-`main.kt:191` קובע ממילא.
      - `filesDir` יוצר את התיקייה אם חסרה (`assertExists`, `FileKit.jvm.kt:147-151`).
      - בהרצת E2E התיקייה היא `E2e.outDir/logs`.
   2. ה-handler: `RollingLogHandler(logs/pardes.log, limit = 5_000_000, count = 5)`, ‏`StreamHandler` של JUL. הקידוד UTF-8 נקבע במפורש, כמו ב-IntelliJ (`IJ:platform/util/src/com/intellij/openapi/diagnostic/RollingFileHandler.kt:57`). הרמה `Level.ALL`: הסינון נעשה ברמות של הלוגרים.
   3. ה-root logger: `Logger.getLogger("").addHandler(handler)`, כמו ש-IntelliJ עושה (`IJ:platform/util/src/com/intellij/openapi/diagnostic/JulLogger.java:184-191`).
      - כך נכנסות כל הרשומות של Nucleus, שכל מודול שלו כותב דרך JUL (`efrul/Nucleus: CLAUDE.md` › Logging; למשל הלוגר של ה-watchdog, `TaoEventLoopWatchdog.kt:141`), וגם של Lucene (ה-CLI של SeforimLibrary מכוון את הלוגר `org.apache.lucene` של JUL, `SeforimLibrary/cli/src/jvmMain/kotlin/io/github/kdroidfilter/seforimlibrary/cli/Main.kt:11-13`) ושל ONNX Runtime 1.17.1 (`Logger.getLogger` ב-`ai/onnxruntime/OnnxRuntime.java:27` וב-`OrtEnvironment.java:26` שלו).
      - ה-ConsoleHandler שכבר קיים נשאר.
      - `allowNucleusRuntimeLogging` נשאר `false`: אפליקציה שמגדירה את JUL בעצמה משאירה אותו כבוי (`N:core-runtime/src/main/kotlin/dev/nucleusframework/core/runtime/tools/Logger.kt:16-34`).
   4. **הודעות האפליקציה.** `LogSinkConfig.sink` (הוו של סעיף 5) הופך כל הודעה ל-`LogRecord` ומעביר אותה ישר ל-handler, לא דרך לוגר של JUL. כך היא לא מגיעה לקונסולה.
      - המיפוי: `VERBOSE`→`FINER`, `DEBUG`→`FINE`, `INFO`→`INFO`, `WARN`→`WARNING`, `ERROR`→`SEVERE`. זה המיפוי של Nucleus עצמו (`N:core-runtime/.../tools/Logger.kt:61-79`).
   5. **הרמה.** נקרא המפתח `diagnostics_verbose_log` מ-`Brand.settings()`. זה אותו מאגר ש-`AppSettings` קורא (`S/framework/di/modules/AppCoreBindings.kt:100`, ‏`Brand.kt:16`), אבל אפשר לקרוא אותו לפני שהגרף קיים.
      - רגיל: `LogSinkConfig.sinkLevel = INFO`.
      - מפורט: `DEBUG`, ובנוסף `Logger.getLogger("dev.nucleusframework").level = Level.FINE`, כמו ש-Nucleus עצמו ממליץ (`tools/Logger.kt:19-22`). הלוגר נשמר בשדה עם הפניה חזקה, כי JUL מחזיק לוגרים בהפניה חלשה ושוכח את הרמה שלהם (`tools/Logger.kt:89-93`; IntelliJ שומר אותם במטמון מאותה סיבה, `IJ:platform/platform-impl/src/com/intellij/diagnostic/logs/LogLevelConfigurationManager.kt:54, 85`).
      - ה-ConsoleHandler נשאר ב-`INFO`, כך שרשומות `FINE` מגיעות לקובץ בלבד.
   6. **חריגות שלא נתפסו.** `Thread.setDefaultUncaughtExceptionHandler` כותב `SEVERE` עם המחסנית, ואחר כך מעביר ל-handler הקודם, או מדפיס ל-stderr כמו ה-JVM.
      - היום חריגה בחוט רקע מודפסת רק ל-stderr: Nucleus לא קובע handler כזה (חיפוש ב-`N:`), ובפרדס קובע אותו רק `S/core/e2e/E2eTortureScenario.kt:174-209`, שמשרשר ומחזיר את הקודם.
      - חריגה בחוט הממשק כבר נכתבת על ידי Nucleus (`reportFatal`, ‏`TaoApplication.kt:171-183`), ונכנסת דרך שלב 3.
   7. **כותרת וסיום.** שורות INFO בתחילת ההפעלה:
      - הגרסה (`NucleusApp.version`, כמו ב-`S/framework/crashreporting/CrashReporting.kt:60`);
      - מערכת ההפעלה, גרסתה והארכיטקטורה;
      - native image או JVM, לפי `org.graalvm.nativeimage.imagecode`, כמו ב-`LuceneSearchEngine.kt:189`;
      - מספר הליבות, תקרת הזיכרון, השפה, אזור הזמן, ה-PID והתיקייה.
      
      ‏shutdown hook כותב `ended`. זה הרעיון של `IDE_STARTED`/`IDE_SHUTDOWN` ושל `logEssentialInfoAboutIde` (`IJ:platform/platform-impl/bootstrap/src/com/intellij/platform/ide/bootstrap/startup.kt:93-94, 643-644, 653-697`).
   8. בסוף: `FreezeLog.install(folder)` (פריט 2) ו-`StartupTrace.start()` (פריט 3).
   9. **אם משהו נכשל** (דיסק מלא, תיקייה בלי הרשאת כתיבה), `install` בולע את הכשל, והתוכנה ממשיכה כמו היום, בלי קובץ.
3. **`RollingLogHandler`**, בקובץ חדש של פרדס. הקוד כתוב מחדש לפי האלגוריתם של `IJ:platform/util/src/com/intellij/openapi/diagnostic/RollingFileHandler.kt`, בלי להעתיק:
   - הקובץ נפתח בהמשך (APPEND): גם אצל IntelliJ זו ברירת המחדל, `idea.log.append` ‏(`IJ:platform/platform-impl/src/com/intellij/idea/LoggerFactory.java:48`).
   - אחרי כל רשומה ה-buffer מרוקן לדיסק (`flush`, ‏`:70-81`).
   - כשהקובץ עבר את הגבול: נמחק `pardes.4.log`, ו-3→4 … 1→2 מוזזים (`:126-134`; אצל IntelliJ `count` סופר רק את הקבצים הישנים, וכאן את כולם, כך ש-5 הם `pardes.log` ועוד ארבעה); הקובץ נסגר, `pardes.log` מוזז ל-`pardes.1.log` ב-`ATOMIC_MOVE`, ונפתח קובץ חדש (`:83-113`).
   - אם ההזזה נכשלת (ב-Windows, למשל, אנטי-וירוס שמחזיק את הקובץ), הכתיבה ממשיכה לאותו קובץ, והכשל נרשם פעם אחת (`:136-142`).
   - השמות בצורה `name.N.ext` (`:144-148`).
   - **הבדל אחד מ-IntelliJ: `close()` רק מרוקן את ה-buffer.**
     - ה-`LogManager` של JUL סוגר ומסיר כל handler ב-shutdown hook משלו. IntelliJ עוקפת את זה ברפלקציה (`JulLogger.java:233-254`), ובפרדס אין רפלקציה, בגלל ה-native image.
     - הגיבוי ל-Drive ביציאה כותב מתוך shutdown hook (`backup-drive/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/backup/drive/GoogleDriveSync.kt:208-217`), וגם שורת הסיום של שלב 2.7.
     - כל רשומה כבר נכתבה לדיסק, והקובץ נסגר עם התהליך, כך ששום דבר לא הולך לאיבוד.
4. **`LogLineFormatter`**, בקובץ חדש של פרדס. הרעיון לקוח מ-`IJ:platform/util/src/com/intellij/openapi/diagnostic/IdeaLogRecordFormatter.java:41-80`: תאריך, אלפיות שנייה מאז פתיחת היומן (`:64`), רמה, ושם לוגר מקוצר (`:70`). הקוד כתוב מחדש.
   - שם הרמה נלקח מ-`Level.getName()` ולא מהשם המתורגם: ב-metadata של ה-native image של פרדס רשום משאב עברי של שמות הרמות (`SeforimApp/src/graalvm/reachability-metadata.json:2985-2986, 2992`), כך ששמות מתורגמים עלולים לצאת בעברית **(לא נבדק)**.
   - כל טקסט, כולל המחסנית, עובר דרך `CrashReporting.scrub(text, home)` (`S/framework/crashreporting/CrashReporting.kt:94-103`). הפונקציה ב-`internal companion object` (שורה 73), ולכן קובץ של פרדס באותו מודול מגיע אליה בלי לשנות את הקובץ.
5. **הוו ב-`Logger.kt` של זית.** המודול `logger` זהה לבסיס (`git diff d7777e2e -- logger/` ריק). הודעות האפליקציה יוצאות היום רק לשני מקומות: לקונסולה, כש-`isDevEnv` דלוק, ול-Sentry, כש-Sentry פעיל (`logger/src/commonMain/kotlin/io/github/kdroidfilter/seforimapp/logger/Logger.kt:47-52, 62-93`). בפרדס אין DSN: `BuildConfig.SENTRY_DSN` ריק (`SeforimApp/build.gradle.kts:387-389`; `.github/workflows/desktop-release.yml:22-23`), `CrashReporting.start()` רק כותב "Sentry DSN not configured" (`CrashReporting.kt:38`), ו-`Sentry.isEnabled()` נשאר `false`. השינוי, כשלוש-עשרה שורות, בסגנון של הקובץ:
   - **אחרי `SentryConfig` (שורות 11-15),** אובייקט באותה צורה:
     ```kotlin
     /** Pardes: a third output beside the console and Sentry (the log file, DiagnosticsLog), from [sinkLevel] up. */
     object LogSinkConfig {
         @Volatile var sink: ((LoggingLevel, String, Throwable?) -> Unit)? = null

         @Volatile var sinkLevel: LoggingLevel = LoggingLevel.INFO
     }
     ```
   - **אחרי `shouldLogToSentry` (שורות 49-52),** `@PublishedApi internal fun shouldLogToSink(minLevel: LoggingLevel): Boolean`, שמחזירה `LogSinkConfig.sink != null && minLevel.priority >= LogSinkConfig.sinkLevel.priority`.
   - **בתוך `logAt`:** שורה `val sendToSink = shouldLogToSink(minLevel)` אחרי שורה 69; התנאי של שורה 70 נהיה `if (!sendToConsole && !sendToSentry && !sendToSink) return`; ואחרי בלוק הקונסולה (שורה 77), `if (sendToSink) runCatching { LogSinkConfig.sink?.invoke(minLevel, renderedMessage, throwable) }`. ה-`runCatching` כמו בענף של Sentry (שורות 79-92).
   - `logAt` והפונקציות הציבוריות הן `inline` (שורות 62, 95-127), ולכן השינוי מקומפל לתוך כל מקום שקורא להן. כל המודולים נבנים יחד, כך שזו לא בעיה. החברים החדשים חייבים להיות ציבוריים או `@PublishedApi internal`.
   - **מי קורא ללוגר:** בערך 150 קריאות. `debugln` בכ-44 מקומות, `infoln` בכ-31, `warnln` בכ-69, `errorln` ב-8, ו-`verboseln` לא נקרא בשום מקום. הן ב-`SeforimApp` ‏(47 קבצים), `backup-drive` (2), `pagination` (1) ו-`branding` (1).
   - אנדרואיד לא מושפע: הוא לא בונה את `logger/` ולא את אף קובץ כאן (חיפוש ב-`androidApp/app/build.gradle.kts`).
6. **העמוד בהגדרות.** קובץ חדש, `DiagnosticsSettings.kt`, עם גרסה בלי מצב (`verbose`, `onVerboseChange`, `folder`, `onOpenFolder`, ו-`modifier` כמו שכללי ה-Compose דורשים), וגרסה עם מצב שקוראת את `DiagnosticsLog.verbose` (`StateFlow`).
   - **המתג** קורא ל-`DiagnosticsLog.setVerbose(on)`, שכותב את ההעדפה ומחיל את הרמות מיד.
   - **הכפתור** קורא ל-`DiagnosticsLog.openFolder()` ב-`Dispatchers.IO`, כמו `DafReviewsFolderLink` (`S/features/bookcontent/daf/DafReviewMenu.kt:75-90`):
     - ‏`Desktop.getDesktop().open(folder)` כש-`Desktop.isDesktopSupported()` ופעולת OPEN נתמכת;
     - אחרת `ProcessBuilder` עם `explorer` ‏/ `open` ‏/ `xdg-open`. זה הגיבוי של `UrlOpener` (`S/core/presentation/utils/UrlOpener.kt:38-47`), שכותב ש-AWT Desktop לא זמין לעתים קרובות בלינוקס (שורות 10-13).
     - IntelliJ מסמנת את הקובץ במנהל הקבצים כשהיא יכולה, ואחרת פותחת את התיקייה (`IJ:platform/platform-impl/src/com/intellij/ide/actions/ShowLogAction.java:29-36`). כאן פותחים את התיקייה.
   - ב-`FeedbackSettingsScreen` נוספת קריאה אחת, `DiagnosticsSettings()`, בין שורה 83 ל-84. `FeedbackSettingsContent` ובדיקת הממשק שלו נשארים כמו שהם.
7. **מה לא נכנס ליומן.**
   - ‏slf4j-simple כותב ל-`System.err`, שנקרא מחדש בכל כתיבה (`slf4j-simple 2.0.20: OutputChoice.java:43-44`, ‏`SimpleLoggerConfiguration.java:65`). הוא בא מ-`SeforimApp/build.gradle.kts:191`, ומשתמש בו `S/framework/update/DbDeltaUpdateService.kt:12, 39, 61`.
   - קריאות `printStackTrace()` גולמיות.
   - ‏Kermit של SeforimLibrary שותק: הוא מכוון ל-`Assert` אלא אם `SEFORIMAPP_REPOSITORY_LOGGING` מוגדר (`SeforimLibrary/dao/src/commonMain/kotlin/io/github/kdroidfilter/seforimlibrary/dao/repository/SeforimRepository.kt:60-66`).
   - ‏IntelliJ מפנה את stdout ואת stderr לתוך היומן, עם הגנה מפני רקורסיה (`startup.kt:645-648`; `IJ:platform/platform-impl/bootstrap/src/com/intellij/platform/ide/bootstrap/PrintStreamLogger.java:20-26`). כאן זו החלטה פתוחה (1.ח).

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/DiagnosticsLog.kt` | חדש | פרדס | `object DiagnosticsLog`: ‏`install()`, ו-`install(folder, settings)` לבדיקות, שמחזירה ידית להסרה; `folder`, ‏`verbose`, ‏`setVerbose`, ‏`openFolder`; ה-sink, ה-handler של החריגות, הכותרת וה-shutdown hook. כ-150 שורות | (ב) |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/RollingLogHandler.kt` | חדש | פרדס | ה-`StreamHandler` המתגלגל, עם `closeFile()` לבדיקות. כ-80 שורות | (ב) |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/LogLineFormatter.kt` | חדש | פרדס | ה-`Formatter` של שורה, עם `scrub`. כ-50 שורות | (ב) |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/DiagnosticsSettings.kt` | חדש | פרדס | המתג, הכפתור והנתיב. כ-70 שורות | (ב) |
| `logger/src/commonMain/kotlin/io/github/kdroidfilter/seforimapp/logger/Logger.kt` | קיים | זית כפי שיצא; אין לו רשומה במפה | `LogSinkConfig` אחרי שורה 15; `shouldLogToSink` אחרי שורה 52; ב-`logAt` שורה אחרי 69, תנאי בשורה 70, וקריאה אחרי 77. בערך +13 −1 | (ג) קטן |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/main.kt` | קיים | זית ששונה (+76 −8) | אחרי שורה 167: הערה ו-`DiagnosticsLog.install()`, ו-import. ‏+3 | (ג), בקובץ שסומן "Merge risk: high" |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/FeedbackSettingsScreen.kt` | קיים | פרדס (125 שורות) | `DiagnosticsSettings()` בין שורה 83 ל-84, ו-import. ‏+2 | (ב) |
| `SeforimApp/src/commonMain/composeResources/values/strings.xml` | קיים | זית ששונה (+359 −1) | בלוק חדש אחרי שורה 301: הערה וארבע מחרוזות. ‏+5 | (ב), בתוך הבלוקים של פרדס |
| `branding/pardes/kotlin/io/github/kdroidfilter/seforimapp/branding/BrandInfo.kt`, ‏`branding/zayit/kotlin/io/github/kdroidfilter/seforimapp/branding/BrandInfo.kt` | קיים | פרדס | `LOG_FILE_NAME` (`pardes.log` / `zayit.log`) עם KDoc, ‏+2 בכל אחד; רק אם החלטה 1.ב בוחרת בזה | (א) |
| `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/RollingLogHandlerTest.kt`, ‏`LogLineFormatterTest.kt`, ‏`DiagnosticsLogTest.kt`; `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/features/settings/feedback/DiagnosticsSettingsUiTest.kt` | חדש | פרדס | הבדיקות (סעיף בדיקות) | (ב) |

**רשומות שצריך במפת השינויים** (`docs/ZAYIT_CHANGE_MAP.md`, לפי `.claude/skills/zayit-fidelity/SKILL.md:40-61`):
- **סעיף 2, קבוצה חדשה** "### The app's log: a file, its freezes and its start times". בראשה פסקה שאומרת מה המשתמשים רואים, ומונה את הווים: `main.kt`, ‏`Logger.kt`, ‏`FeedbackSettingsScreen.kt`, ‏`strings.xml`, ובפריטים 2-3 גם `MainAppWindow.kt` ו-`HybridSearchEngine.kt`. לכל קובץ חדש, כולל הבדיקות, רשומה `####` עם Feature, ‏Size, ‏What, ‏Reached from, ‏Follows ו-Tests.
  - Follows: ‏`DiagnosticsLog` הולך אחרי `CrashReporting`; ה-handler והפורמט אחרי האלגוריתם של IntelliJ (הרעיון בלבד, בלי קוד מועתק); הכרטיס אחרי `SettingCard` ואחרי `DafReviewsFolderLink`.
  - לבדיקות, Reached from: ‏"the JVM tests".
- **סעיף 1, רשומה חדשה** `` #### `logger/src/commonMain/kotlin/io/github/kdroidfilter/seforimapp/logger/Logger.kt` `` תחת "### The application shell, DI, windows, update and platform":
  - Change: ‏modified, +13 −1;
  - What: ‏`LogSinkConfig`, ‏`shouldLogToSink`, והפלט השלישי של `logAt`;
  - Why: הודעות האפליקציה מגיעות לקובץ רק דרך הלוגר;
  - Kind: ‏Pardes feature hook;
  - Merge risk: קטן. גרסה 0.6.13 לא נוגעת בקובץ (סעיף 5 במפה, שורות 7568-7582). ההיסטוריה של זית לא נמצאת בעותק הרדוד, כך שתדירות השינויים בקובץ **(לא נבדקה)**;
  - Smaller: ‏already minimal;
  - Tests: ‏`DiagnosticsLogTest`.
  - ובנוסף: טבלת הסוגים בראש סעיף 1 (Pardes feature hook: מ-53 ל-54, שורות 58-69), וספירת הסעיף (מ-121 ל-122). את הספירות מעדכנים עם `scripts/mapedit.py`.
- **רשומות קיימות שנכתבות מחדש:**
  - `main.kt` (שורות 338-373): Change מ-+76 −8 ל-+79 −8 (ועוד שורות מפריט 3); תבליט חדש; ו-"the hooks sit in five places" נהיה שש. ה-Smaller של הרשומה כבר מציע לאחד ווים, וכאן וו אחד משרת את שלושת הפריטים.
  - `strings.xml` (משורה 1733): Change מ-+359 −1 ל-+364 −1, ותבליט `diagnostics_*` (4).
  - `FeedbackSettingsScreen.kt` (שורות 5652-5668): Size 127, ו-What שמזכיר את `DiagnosticsSettings`.
  - אם 1.ב בוחרת ב-`BrandInfo`: השורות של שני הקבצים בסעיף 3 (שורות 6578, 6583), והתיאור של `BrandInfo` ב-`branding/README.md`.
- בסוף: `python3 .claude/skills/zayit-fidelity/scripts/check_change_map.py` צריך לעבור.

**החלטות פתוחות:**
- **1.א איך מגלגלים את הקובץ.**
  - *מומלץ:* handler של פרדס שכתוב לפי האלגוריתם של IntelliJ, בכ-80 שורות.
  - אפשרות אחרת: `java.util.logging.FileHandler` של ה-JDK, בלי קוד גלגול בכלל. אבל אז יש קובץ נעילה `pardes.0.log.lck` בתיקייה, השמות הם `pardes.0.log`…, ה-hook של JUL סוגר אותו ביציאה, ופתיחה שנייה של התוכנה (שעוברת ב-`main` לפני בדיקת המופע היחיד) יוצרת `pardes.0.log.1`.
  - אפשרות שלישית: להעתיק את `RollingFileHandler.kt` ‏(149 שורות, Apache 2.0) עם ההודעות, לפי המתכון (`.claude/skills/zayit-fidelity/references/lessons.md:153-211`). זה עומס רישוי מיותר לקוד כל כך קטן.
- **1.ב שם הקובץ.**
  - *מומלץ:* `BrandInfo.LOG_FILE_NAME`, כלומר `pardes.log`, ו-`zayit.log` בבנייה של זית. יש לזה תקדים: `LIBRARY_FOLDER_NAME` (במפה, שורה 6583).
  - אפשר גם `pardes.log` קבוע בקוד, או שם ניטרלי כמו `app.log`. התיקייה ממילא שונה לכל מותג.
- **1.ג גודל ומספר קבצים.** *מומלץ:* 5MB × 5 קבצים בסך הכול, כמו בדוח. ב-IntelliJ: 10,000,000 בתים, ו-`idea.log` עם עוד עד 12 קבצים ישנים (`JulLogger.java:25-26`, ‏`RollingFileHandler.kt:126-134`).
- **1.ד קובץ אחד שממשיך מהפעלה להפעלה, או קובץ חדש בכל הפעלה.** *מומלץ:* להמשיך, כמו IntelliJ. קובץ חדש בכל הפעלה שומר רק את חמש ההפעלות האחרונות, וכמה הפעלות מחדש אחרי תקלה מוחקות אותה.
- **1.ה איפה התיקייה.**
  - *מומלץ:* `filesDir/logs`, ליד `databases`, כמו שהדוח מציע.
  - אפשרויות אחרות: `FileKit.cacheDir/logs` (ב-Windows זה `%LOCALAPPDATA%\io.github.efrul.pardes\Cache`, שלא עובר בין מחשבים, כמו שעושה IntelliJ), או `~/Library/Logs` ב-macOS.
- **1.ו איפה נשמר המתג.**
  - *מומלץ:* ב-`DiagnosticsLog`, דרך `Brand.settings()`, במפתח `diagnostics_verbose_log` שלא עובר בגיבוי. כך אפשר לקרוא אותו לפני שהגרף קיים, ולא צריך לגעת ב-`AppSettings`.
  - אפשרות אחרת: ב-`AppSettings`, כמו בסגנון של אלי ("Settings go through AppSettings", ‏`references/eli-style.md:51-53`), וכמו `OpenSiddurSwitch`. אבל `AppSettings` נוצר רק עם הגרף, אחרי האתחול של Nucleus, והוא קובץ של זית, כלומר (ג).
- **1.ז מה כולל "מפורט".** *מומלץ:* ה-`DEBUG` של האפליקציה, וגם `dev.nucleusframework` ב-`FINE`. לא Kermit: הוא מכוון מחדש בכל פעם שהמאגר נבנה (`SeforimRepository.kt:60-66`).
- **1.ח להפנות את stdout ואת stderr ליומן, כמו IntelliJ.** *מומלץ:* לא בצעד הראשון. יש סכנת רקורסיה, ומעט מאוד בפרדס כותב ל-stderr (slf4j-simple ב-`DbDeltaUpdateService`, וקריאות `printStackTrace`). אפשר לחזור לזה אחרי שהיומן הוכיח את עצמו.
- **1.ט כפתור או קישור.**
  - *מומלץ:* `OutlinedButton`, כי הבקשה הייתה לכפתור, ויש לזה תקדים בעמוד (`FeedbackSendCard.kt:362`).
  - אפשרות אחרת: `Link` ליד `DafReviewsFolderLink`.
- **1.י handler לחריגות שלא נתפסו.** *מומלץ:* כן, משורשר ל-handler הקודם.
- **1.יא האם המתג נשמר.** *מומלץ:* כן, עד שמורידים אותו, כמו הקטגוריות של IntelliJ שנשמרות ב-`log-categories.xml` (`LogLevelConfigurationManager.kt`). כבוי כברירת מחדל.
- **1.יב אפשרות שנדחית: בלי לגעת ב-`Logger.kt`.** אפשר להדליק `isDevEnv` ו-`loggingLevel = INFO`, ולהפנות את `System.out` לקובץ בלי צבעי ANSI. *מומלץ לדחות:* זה משנה את מה שמודפס כשהתוכנה נפתחת מטרמינל, מדליק את מצב הדיבאג של Sentry בבנייה רשמית של זית (`CrashReporting.kt:61`), ותלוי בהפניה של זרמים.
- **1.יג איפה בדיוק הוו.**
  - *מומלץ:* לפני `nucleusApplication`, כמו שמתואר. כך נכנסות גם ההודעות של האתחול של Nucleus, כולל כשל בטעינה של הספריות native, שהוא בדיוק המקרה של "התוכנה לא עולה". בתנאי שגרסת הניסיון ל-macOS ולינוקס עוברת.
  - החלופה: שורה ראשונה בתוך הלמבדה של `nucleusApplication`, ב-`remember { }`, כמו הצעדים הממתינים (`S/main.kt:196`). היא רצה אחרי `GraalVmInitializer` ואחרי בדיקת המופע היחיד, כך שאין גם כותרות של פתיחה שנייה. אבל מה שקורה לפני ההרכבה הראשונה לא נכנס ליומן.

**סיכונים ולא-ידועים:**
- **native image** (המהדורות ל-Windows x64, macOS ולינוקס הן GraalVM של Oracle; רק Windows arm64 רצה על JVM, `.github/workflows/desktop-release.yml:164-180, 251-284`):
  - **ה-handler, הפורמט וה-`LogRecord` לא צריכים metadata.** הם נבנים בקוד, ואין בהגדרות שם של מחלקה שנטענת ברפלקציה. JUL עצמו כבר עובד ב-native image של פרדס, כי כל מודול של Nucleus כותב דרכו.
  - **`java.logging` נמצא גם בבנייה ל-JVM:** `modules("java.sql", …)` (`SeforimApp/build.gradle.kts:265-266`), ו-`java.sql` דורש `java.logging` באופן טרנזיטיבי (נבדק ב-`java --describe-module java.sql`).
  - **`Desktop.open`** כבר נמצא בקוד שיצא (`DafReviewMenu.kt:85`, ‏`FeedbackSettingsScreen.kt:75`), ו-Nucleus רושם את `WDesktopPeer` ואת `XDesktopPeer` ב-metadata שלו (`N:plugin-build/plugin/src/main/resources/nucleus/graalvm/platform-metadata/windows-reachability-metadata.json:2192`, ‏`linux-…json:313`). האם הוא עובד בכל מערכת ב-native image **(לא נבדק)**, ולכן יש גיבוי ב-`ProcessBuilder`.
  - **הוו רץ לפני `GraalVmInitializer.initialize()`.** `nucleusApplication` קורא לו ראשון (`N:nucleus-application/src/main/kotlin/dev/nucleusframework/application/NucleusApplication.kt:75`), והוא קובע ב-native image את `java.home`, את `java.library.path`, את הקידוד ואת השפה של macOS (`N:graalvm-runtime/src/main/kotlin/dev/nucleusframework/graalvm/GraalVmInitializer.kt:17-84`).
    - ב-Windows זה כבר קורה היום: `S/main.kt:181` ניגש ל-`WindowsJumpListManager` לפני `nucleusApplication`, ולאובייקט הזה יש לוגר של JUL (`N:launcher-windows/src/main/kotlin/dev/nucleusframework/launcher/windows/WindowsJumpListManager.kt:27`). כלומר, JUL מאותחל לפני `GraalVmInitializer` במהדורה ל-Windows שכבר יצאה, והיא עובדת.
    - ב-macOS ובלינוקס השורה הזו לא רצה, ולכן `install()` יהיה הראשון שנוגע ב-JUL לפני `GraalVmInitializer` **(לא נבדק)**. גם שורת השפה בכותרת עלולה לצאת `en` ב-macOS, כי השפה משוחזרת רק אחר כך.
    - לכן צריך לבדוק את זה בגרסת ניסיון ל-macOS ולינוקס, ויש חלופה (החלטה 1.יג).
- **פרטיות: מה יכול להיות ביומן.**
  - **ברמה הרגילה:**
    - נתיבים, כשהתיקייה הביתית מוחלפת ב-`~`. ספרייה בכונן אחר נשארת עם הנתיב המלא שלה (`S/framework/database/DatabasePathProvider.kt:64`, ‏`S/framework/database/LibraryLocation.kt:298`).
    - שם של מילון (`S/features/home/widgets/library/DictionaryWidget.kt:341`).
    - הבעלים של ספרייה שמשתמשים בה במקומה (`S/framework/database/ForeignLibraries.kt:97`).
    - הפקודה של ה-CLI (`S/core/presentation/utils/CliTerminalLauncher.kt:54`).
    - גרסאות של עדכונים, והודעות של חריגות.
  - **במצב המפורט, גם:**
    - מספרי ספרים ושורות (`S/features/bookcontent/BookContentViewModel.kt:293-307`, ‏`S/features/bookcontent/usecases/ContentUseCase.kt:57`).
    - טקסט של חיפוש: `[History] open search:g:<השאילתה>` (`S/features/history/HistoryScreen.kt:91`, עם `searchKey` ב-`S/core/history/HistoryStore.kt:163-183`).
    - שמות של שולחנות עבודה (`S/framework/session/SessionManager.kt:79-89`).
    - זמני חיפוש, בלי טקסט השאילתה (`S/features/search/semantic/HybridSearchEngine.kt:329-333`).
  - **שום דבר לא שולח את היומן.** אין DSN (`CrashReporting.kt:26-27`), וחבילת המשוב לא כוללת אותו (`S/features/settings/feedback/FeedbackPackage.kt:13-22`). ה-`scrub` מחליף רק את התיקייה הביתית.
- **פתיחה שנייה.** לחיצה כפולה כשהתוכנה כבר פתוחה, או קישור עמוק, מריצות את `main` ואת `install()` לפני ש-Nucleus מעביר אותן למופע שרץ. כל אחת כזו מוסיפה ליומן כותרת קצרה ושורת סיום. זה גם מידע שימושי.
- **שני תהליכים שמגלגלים יחד** (בהעברה אחרי עדכון, או בפתיחה שנייה בדיוק בגבול של 5MB): הזזה שנכשלה ממשיכה לכתוב לאותו קובץ. התנהגות השיתוף של קבצים פתוחים ב-Windows **(לא נבדקה)**.
- **כתיבה סינכרונית.** הכתיבה קורית בחוט שכותב את ההודעה, ולפעמים זה חוט הממשק. ברמה הרגילה יש מעט שורות. IntelliJ כותבת דרך תור (`IJ:platform/util/src/com/intellij/openapi/diagnostic/asyncLog.kt:67-68, 94`); זה צעד המשך, אם במצב המפורט ייראו גמגומים.
- **איפוס הגדרות.** "איפוס" ב-`AppSettings.clearAll()` מוחק את כל הצומת (`S/core/settings/AppSettings.kt:821-822`), כולל המתג. המתג יחזור להיות כבוי בהפעלה הבאה.
- **מיזוג עם זית.** `main.kt` מסומן "Merge risk: high" (מפת השינויים, שורות 370-372). `Logger.kt` לא משתנה ב-0.6.13.

**בדיקות:**
- **`T/framework/diagnostics/RollingLogHandlerTest.kt`** (שמות הבדיקות בגרשיים הפוכים, כמו אצל אלי):
  - מעבר של הגבול יוצר `pardes.log` ו-`pardes.1.log`;
  - אף פעם אין יותר מ-`count` קבצים, והישן ביותר נמחק;
  - בפתיחה מחדש הכתיבה ממשיכה את הקובץ;
  - כשההזזה נכשלת (`pardes.1.log` הוא תיקייה לא ריקה), הכתיבה ממשיכה;
  - אחרי `close()`, רשומה עדיין נכתבת;
  - טקסט עברי נשמר ב-UTF-8.
- **`T/framework/diagnostics/LogLineFormatterTest.kt`:**
  - הצורה של השורה, והרמות באנגלית;
  - התיקייה הביתית מוחלפת בשני סוגי המפרידים, כמו ב-`T/framework/crashreporting/CrashReportingTest.kt:20-29`;
  - המחסנית מצורפת, וגם בה הנתיבים מוחלפים;
  - שם הלוגר מקוצר.
- **`T/framework/diagnostics/DiagnosticsLogTest.kt`:** התקנה לתיקייה זמנית, עם `PropertiesSettings(Properties())` כמו ב-`T/TestAppSettings.kt:8`.
  - `infoln {}` מגיע לקובץ;
  - `debugln {}` לא מגיע, עד `setVerbose(true)`;
  - רשומת `SEVERE` של כמה שורות מהלוגר `dev.nucleusframework.window.tao.TaoEventLoopWatchdog` נכתבת שלמה;
  - `FINE` של `dev.nucleusframework` נכתב רק במצב המפורט;
  - המתג נשמר בהגדרות;
  - חריגה בחוט רקע נכתבת;
  - ב-`@AfterTest` מוסרים ה-handler, ה-sink וה-handler של החריגות.
- **`T/features/settings/feedback/DiagnosticsSettingsUiTest.kt`:** כמו `FeedbackSettingsUiTest`, עם `runDesktopComposeUiTest` ובימין לשמאל.
  - התיבה מפעילה את ה-callback;
  - הכפתור "פתיחת תיקיית היומנים" קורא ל-`onOpenFolder`;
  - הנתיב מוצג.
- **בתוכנה בענן,** עם `pardes-desktop-run` (בנייה ל-JVM, Xvfb):
  - ב-`~/.local/share/io.github.efrul.pardes/logs/pardes.log` יש כותרת, שורות של Nucleus ושורות של האפליקציה (`[CatalogCache] …`).
  - בהגדרות › משוב רואים את הכרטיס.
  - אחרי סימון "יומן מפורט", פתיחת ספר כותבת מיד `[BookContentViewModel] init …`.
  - ‏`PREFS="diagnostics_verbose_log=true" .claude/skills/pardes-desktop-run/scripts/start.sh` פותח ישר במצב מפורט.
  - בלחיצה על הכפתור ב-Xvfb, שאין בו מנהל קבצים: התוכנה לא נופלת, ונכתבת שורה ביומן.
- **בגרסת ניסיון,** Windows x64 ב-native image (`desktop-release.yml` עם `quick: "true"`, לפי `references/lessons.md:227-243`): היומן נכתב, התיקייה נפתחת ב-Explorer, ואחרי סגירה יש שורת סיום. ב-macOS ובלינוקס בודקים את אותם דברים, ובנוסף שהתוכנה עולה כרגיל ושהכותרת נכתבת, כלומר שהוו שלפני `GraalVmInitializer` לא מפריע שם.
- **לפני commit:** ‏`./gradlew ktlintCheck detekt` על `SeforimApp` ועל `logger`, הבדיקות של `SeforimApp`, ו-`check_change_map.py`.

**היקף ותלות:**
- **היקף:** ארבעה קבצים חדשים, כ-350 שורות. כ-23 שורות בקבצים קיימים (בלי `BrandInfo`). ארבעה קובצי בדיקה, כ-310 שורות.
- **מאמץ:** S עד M: יום עד יומיים, ועוד גרסת ניסיון.
- **תלות:** הפריט לא תלוי באף פריט אחר. פריטים 2 ו-3 תלויים בו.

---

#### 2. לכידת קפיאות

**בקצרה:** ב-Windows, כשהחלון מפסיק להגיב, נכנסים ליומן של פריט 1 הדיווח `SEVERE` של `TaoEventLoopWatchdog` עם המחסניות של כל החוטים, שורות סימון של פרדס, וסימון של "קפיאה שלא נגמרה" אם התוכנה נסגרה בכוח באמצע. אין היום פעולה שצריך לעטוף ב-`expectUnresponsive`. בשלב ב' מציעים ל-Nucleus דוגם קפיאות בתוך התהליך, לכל המערכות.

**מה רואים:**
- **בממשק:** שום דבר. Nucleus לא מציג כלום משלו, וזה במכוון (`N:docs/internals/tao-event-loop-watchdog.md:3`), ו-Windows כבר מסמן חלון תקוע ומציע לסגור או לחכות.
- **ביומן, ב-Windows:**
  - **כ-10 עד 12 שניות אחרי שהחלון נתקע** (Windows מסמן אחרי כ-5 שניות, ועוד 5 שניות של חסד ובדיקה כל 2 שניות: `TaoEventLoopWatchdog.kt:20-23`):
    ```
    WARN  nucleus-tao-watchdog-events app - [Freeze] the window stopped responding (Windows watchdog); Nucleus's thread dump is in this log
    SEVERE nucleus-tao-watchdog d.n.w.t.TaoEventLoopWatchdog - Tao event loop has not pumped messages for at least 5000 ms — the UI is frozen. Thread dump follows.
    "main" (Tao event loop) RUNNABLE
    	at …
    ```
    המחסניות של כל החוטים, וחוט הלולאה ראשון ומסומן (`allThreadStacks`, ‏`TaoEventLoopWatchdog.kt:651-667`).
  - **כשהחלון חוזר להגיב:** השורה של Nucleus, `INFO … Tao event loop responded again after N ms` (שורה 559), ושורה של פרדס, `INFO [Freeze] responding again, about N s after it was reported`.
  - **אם התוכנה נסגרה בכוח בזמן קפיאה:** בהפעלה הבאה נכתבת השורה `WARN [Freeze] the previous session ended during a freeze reported at 2026-11-21 21:09:40`.
- **בדיסק:** `logs/freeze-in-progress.txt`, שבו שעת הדיווח. הוא קיים רק בזמן קפיאה, ונמחק כשהחלון חוזר להגיב.
- **ב-macOS ובלינוקס:** שום דבר עד שלב ב'. ה-watchdog לא רץ שם (`isSupported`, ‏`TaoEventLoopWatchdog.kt:166-169`).

**איך זה עובד:**
1. **המצב היום.** ב-Windows ה-watchdog כבר רץ בפרדס:
   - ‏`TaoApplication.run` מפעיל אותו ועוצר אותו (`TaoApplication.kt:120-128`).
   - הוא דלוק כברירת מחדל (`nucleus.tao.watchdog`, ‏`TaoEventLoopWatchdog.kt:171-172`), וכבוי רק כשמחובר דיבאגר (`:187-195`, ‏`:312-316`). פרדס לא קובע אף אחת מהתכונות שלו (חיפוש ב-`SeforimApp/`).
   - ‏`nucleusApplication` נקרא רק במקום אחד (`S/main.kt:183`). בפרדס אין שום שימוש ב-`onUnresponsive`, ‏`onResponsive` או `expectUnresponsive` (חיפוש).
   - ה-handler של פריט 1 על ה-root logger תופס את הדיווח בלי קוד נוסף.
2. **איך Nucleus מזהה קפיאה** (נקרא בקוד, כדי לדעת מה מקבלים):
   - כל 2 שניות הוא בודק את `IsHungAppWindow`, בחלונות שנרשמו ב-`WINDOW_READY` (`N:.../TaoWindow.kt:1746-1751`; ‏`registerWindow`, ‏`TaoEventLoopWatchdog.kt:212-234`).
   - הוא מדווח פעם אחת, אחרי 5 שניות של חסד (`EventLoopHangDetector`, ‏`:691-746`).
   - הוא ישן כשאין חלון (`:509-512`).
   - שינה של המחשב לא נחשבת לקפיאה (`:28`, ‏`:410-437`), וגם לא עצירה של איסוף זבל (`:469-472`).
   - קודם הוא קורא ל-callback, ורק אחר כך כותב את המחסניות (`report`, ‏`:577-603`). אם אי אפשר לקחת מחסניות, נכתב "thread dump unavailable: …" (שורה 591).
3. **`FreezeLog`**, קובץ חדש של פרדס. `DiagnosticsLog.install()` קורא ל-`FreezeLog.install(folder)`, ולכן לא נוסף וו.
   - **הרישום:** ‏`install` רושם `TaoApplication.onUnresponsive { onFrozen() }` ו-`TaoApplication.onResponsive { onThawed() }`.
     - הם ציבוריים ב-jar שפרדס בונה מולו (נבדק ב-`javap` על ה-jar שב-`nucleus-fork/maven`).
     - יש handler אחד בכל פעם: קריאה שנייה מחליפה את הראשונה (`TaoApplication.kt:603-633`). מאחר שהם שדות פשוטים, אפשר לרשום אותם לפני `run`.
     - התלות כבר קיימת: `implementation(libs.nucleus.decorated.window.tao)` (`SeforimApp/build.gradle.kts:172`).
     - החלופה, דרך `NucleusApplicationScope` (`NucleusApplicationScope.kt:58-108`), דורשת שורה בתוך הלמבדה של `nucleusApplication` (`S/main.kt:183-519`).
   - **`onFrozen()`** רץ בחוט `nucleus-tao-watchdog-events`, לא בחוט הממשק (`NucleusApplicationScope.kt:76-80`). הוא שומר את השעה, כותב את קובץ הסימון ושורת `WARN`, ולא חוסם.
   - **`onThawed()`** כותב `INFO` עם הזמן שעבר מאז `onFrozen`, ומוחק את קובץ הסימון.
   - **ב-`install`:** אם נשאר קובץ סימון, נכתבת שורת `WARN` על ההפעלה הקודמת, והקובץ נמחק. זה הרעיון של `processUnfinishedFreeze` של IntelliJ, שמחזיקה קובץ `.duration` בתוך תיקיית הקפיאה (`IJ:platform/platform-impl/src/com/intellij/diagnostic/PerformanceWatcherImpl.kt:261-290`).
4. **`expectUnresponsive`: נבדק שאין מה לעטוף.**
   - העברת הספרייה: `LaunchedEffect(mover) { withContext(Dispatchers.IO) { mover.run() } }` (`S/features/database/move/LibraryMoveWindow.kt:71`).
   - השחזור, ניקוי המסד והחלפת החבילה הסמנטית: ‏`remember { … }` בהרכבה הראשונה (`S/main.kt:196, 200, 215`), לפני שיש חלון, ולכן ל-watchdog אין מה לבדוק.
   - הגיבוי ל-Drive ביציאה: ב-shutdown hook (`GoogleDriveSync.kt:208-217`), אחרי שה-watchdog כבר נעצר (`TaoApplication.kt:125-128`).
   - ההורדה והחילוץ של הספרייה בהתקנה, ומחיקת המסד הישן: ב-`Dispatchers.IO` (`S/features/onboarding/extract/ExtractUseCase.kt:36`, ‏`S/features/onboarding/download/HttpFileDownloader.kt:43, 78`, ‏`S/features/database/update/DatabaseCleanupUseCase.kt:52`).
   - **ולכן:** לא עוטפים שום דבר עכשיו. כשהיומן יראה פעולה ארוכה וצפויה בחוט הממשק, עוטפים אותה ב-`TaoApplication.expectUnresponsive { }`, שאפשר לקרוא לו שוב מתוכו ומכל חוט (`TaoApplication.kt:657-664`). קובץ של פרדס יכול לקרוא לו בלי ה-scope. אם הפעולה בקוד של זית, זו עריכה (ג).
5. **מה נלקח מ-IntelliJ, ומה לא:**
   - *נלקחו:* קובץ הסימון של קפיאה שלא נגמרה, ושורת הסיכום בסוף ("UI was frozen for Xms", ‏`PerformanceWatcherImpl.kt:821`).
   - *לא נלקחו:* תיקייה נפרדת לכל קפיאה, `threadDumps-freeze-…`, ששמה מקבל את משך הקפיאה בסוף (`:101-102`, ‏`:526-545`, ‏`:796-831`), והניקוי של יותר מ-100 תיקיות או של תיקיות בנות יותר מ-10 ימים (`:1194-1210`). היומן המתגלגל כבר מגביל את המקום בדיסק, ושולחים קובץ אחד.
6. **שלב ב' (ד): הצעה ל-Nucleus לדוגם קפיאות לכל המערכות.**
   - **איך מציעים:**
     - כל תכונה חדשה ב-Nucleus מתחילה בדיון ב-GitHub (`N:CONTRIBUTING.md:10-12`).
     - את הדיון פותחים בעלי המאגר: סשן לא פונה לאלי (`docs/DESKTOP_STAGE2_PLAN.md:506`). ב-12.11 הוחלט ללכת גם לשינויים ב-Nucleus (`:254`, ‏`:499`).
     - אב-טיפוס יכול לחיות ב-efrul/Nucleus ולהיכנס לפרדס דרך `nucleus-fork/` (`nucleus-fork/README.md`). המודול שפרדס כבר לוקח מהפורק הוא בדיוק `decorated-window-tao`, שמחזיק את ה-watchdog, את `TaoApplication` ואת `TaoMainDispatcher`.
   - **המנגנון:**
     - חותמת זמן בתחילת ובסוף כל `EventDispatcher.onEvent` (`TaoApplication.kt:783-801`) וכל `TaoMainDispatcher.pump()` (`N:.../dispatch/TaoMainDispatcher.kt:275-306`), כמו `edtEventStarted`/`edtEventFinished` של IntelliJ (`PerformanceWatcherImpl.kt:402-413`), שנקראות מ-`IJ:platform/platform-impl/src/com/intellij/ide/IdeEventQueue.kt:332-333, 438-439`.
     - החוט הקיים של ה-watchdog בודק את הגיל של האירוע שעוד רץ. מעבר לסף, הוא מעלה את אותו `Stalled`, כלומר אותו `SEVERE` ואותו `onUnresponsive`.
     - כל עוד הקפיאה נמשכת, הוא לוקח מחסניות כל 5 שניות, עד מספר קבוע (`dumpInterval`, ‏`PerformanceWatcherImpl.kt:323-330`).
     - ‏`Recovered` עולה כשהאירוע נגמר.
   - **המחיר:** שתי כתיבות `@Volatile` לכל אירוע. שום דבר לא נשלח ללולאה, כמו שהמסמך דורש (`tao-event-loop-watchdog.md:3`).
   - **המגבלה:** תקיעה בתוך קוד native בין callback-ים, כמו #640, לא נראית. לכן ב-Windows `IsHungAppWindow` נשאר.
   - **תקדים בקוד של זית:** גלאי קפיאות לפי פעימות, ב-`S/core/e2e/E2eTortureScenario.kt:167-204` (`STALL_MS = 2000`, שורה 57). הוא שולח עבודה ללולאה כל 100ms, וזה סוג הבדיקה שהמסמך של Nucleus פוסל לשימוש קבוע.

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/FreezeLog.kt` | חדש | פרדס | `object FreezeLog`: ‏`install(folder)`, ‏`onFrozen()`, ‏`onThawed()`, וקובץ הסימון. כ-60 שורות | (ב) |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/DiagnosticsLog.kt` | חדש (מפריט 1) | פרדס | שורה אחת: `FreezeLog.install(folder)` | (ב) |
| `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/FreezeLogTest.kt` | חדש | פרדס | כ-60 שורות | (ב) |
| שלב ב' ב-Nucleus: `TaoApplication.kt`, ‏`dispatch/TaoMainDispatcher.kt`, ‏`TaoEventLoopWatchdog.kt`, ‏`docs/internals/tao-event-loop-watchdog.md`, ובדיקות | קיים | Nucleus, דרך efrul/Nucleus | דיון, ואחריו אב-טיפוס | (ד) |

**רשומות שצריך במפת השינויים:**
- רשומות ל-`FreezeLog.kt` ול-`FreezeLogTest.kt`, בקבוצה החדשה של פריט 1.
- אם אב-טיפוס של שלב ב' נכנס דרך הפורק: השורות של `nucleus-fork/` בסעיף 3, ועדכון הרשומות של `gradle/libs.versions.toml` (`nucleusTao`, שורה 15) ושל `settings.gradle.kts` (המאגר `NucleusFork`, שורות 91-92) לגרסה החדשה של הפורק.

**החלטות פתוחות:**
- **2.א לכתוב שורות סימון וקפיאה שלא נגמרה, או להסתפק בשורות של Nucleus.** *מומלץ:* `FreezeLog` הקטן. קובץ הסימון תופס את המקרה הנפוץ, שבו סוגרים תוכנה תקועה בכוח, ו-IntelliJ עושה אותו דבר.
- **2.ב מחסניות נוספות במשך הקפיאה, בפרדס** (רק חוט הממשק, כל 5 שניות). *מומלץ:* לא. זה שייך להצעה ל-Nucleus. המחסניות ש-Nucleus לוקח פעם אחת, בזמן הזיהוי, מספיקות לצעד הראשון.
- **2.ג מה עוטפים ב-`expectUnresponsive`.** *מומלץ:* כלום עכשיו, לפי הבדיקה שבסעיף 4.
- **2.ד גלאי זמני לפי פעימות ל-macOS ולינוקס, בפרדס,** כמו ב-`E2eTortureScenario`. *מומלץ:* לא. הוא שולח עבודה ללולאה ומעיר אותה כל שנייה, וזה עוד מנגנון שיצטרכו להסיר. כן, אם Nucleus דוחה את ההצעה.
- **2.ה להראות משהו בממשק בזמן קפיאה** (החלון המובנה של Nucleus דרך `-Dnucleus.tao.watchdogDialog=true`, `TaoEventLoopWatchdog.kt:203-204`, או "לחכות או לסגור"). *מומלץ:* לא.
- **2.ו סדר ההצעה ל-Nucleus.** *מומלץ:* קודם הדיון, כמו שהכלל של Nucleus דורש, ואב-טיפוס בפורק רק אחרי תשובה. אפשרות אחרת: אב-טיפוס קודם, כדי להראות אותו בדיון.
- **2.ז הסף בהצעה.** *מומלץ:* 5 שניות, בדומה לסימון של Windows ולמרווח של IntelliJ, וניתן לשינוי בתכונת מערכת.

**סיכונים ולא-ידועים:**
- **`Thread.getAllStackTraces()` ב-native image של Oracle GraalVM:** אם הוא מחזיר מחסניות Java מלאות **(לא נבדק)**. הוא לא צריך metadata (זה שירות של ה-VM, לא רפלקציה), ואם הוא נכשל, Nucleus כותב "thread dump unavailable" (שורה 591). התשובה תבוא מגרסת הניסיון ל-Windows x64.
- **רק Windows.** קפיאה ב-macOS ובלינוקס לא משאירה עקבה עד שלב ב'.
- **המשך שנכתב קצר מהקפיאה האמיתית.** הוא נמדד מה-callback, והקפיאה התחילה בערך 10 שניות קודם (חמש של Windows וחמש של חסד, `EventLoopHangDetector.sample`, ‏`:708-724`). השורה צריכה לומר את זה.
- **ה-callback-ים רצים בחוט אחד.** אסור לחסום אותו (`TaoApplication.kt:613-620`). ה-handler-ים כאן רק כותבים שורה וקובץ.
- **גודל המחסניות** **(לא נמדד)**. כנראה עשרות עד מאות KB לכל קפיאה, כך ש-5MB × 5 מחזיקים כמה קפיאות.
- **פרטיות:** במחסניות יש שמות של חוטים ושורות קוד, בלי טקסטים ובלי חיפושים.
- **דיבאג:** בזמן דיבאג ה-watchdog כבוי, במכוון.

**בדיקות:**
- **`T/framework/diagnostics/FreezeLogTest.kt`:**
  - `onFrozen` כותב את קובץ הסימון ושורת `WARN`;
  - `onThawed` מוחק אותו ורושם את המשך;
  - ‏`install` עם קובץ שנשאר כותב את ה-`WARN` על ההפעלה הקודמת, ומוחק את הקובץ;
  - `onThawed` בלי `onFrozen` לא עושה כלום.
- **דרך `DiagnosticsLogTest`:** רשומת `SEVERE` של כמה שורות, בשם הלוגר של ה-watchdog, נכתבת שלמה.
- **בתוכנה, ב-Windows בלבד:**
  - commit זמני רק בענף הבדיקה, שמבוטל אחר כך (כמו ה-changelog ב-`references/lessons.md:236-240`). הוא חוסם את חוט הממשק ל-20 שניות, למשל `Thread.sleep(20_000)` בתוך `LaunchedEffect` על `Dispatchers.Main` אחרי החלון הראשון.
  - גרסת ניסיון ל-Windows x64 ב-native image (`references/lessons.md:234-235`). ב-`pardes.log` צריך לראות את ה-`WARN`, את ה-`SEVERE` עם `"main" (Tao event loop)` ושורות קוד אמיתיות (זו התשובה לשאלה על ה-native image), ואת שורות החזרה.
  - קפיאה שנייה, וסגירה דרך מנהל המשימות: בהפעלה הבאה צריכה להופיע שורת הקפיאה שלא נגמרה.
  - הבדיקות של Nucleus עצמו: `EventLoopWatchdogHeadfulCases`, ו-`./gradlew :decorated-window-tao:taoWatchdogSmoke` (`tao-event-loop-watchdog.md:3`).

**היקף ותלות:**
- **היקף:** כ-60 שורות, ועוד כ-60 שורות בדיקה.
- **מאמץ:** S, חצי יום אחרי פריט 1. שלב ב' ב-Nucleus: M עד L, אחרי דיון.
- **תלות:** תלוי בפריט 1. בלי הקובץ, השורות הולכות ל-stderr.

---

#### 3. מדידת זמני עלייה

**בקצרה:** אובייקט קטן, `StartupTrace`, מודד מהשורה הראשונה של `main` מתי נוצר הגרף, מתי צויר הפריים הראשון של החלון הראשי, מתי התחמם הקטלוג, ומתי האינדקס נפתח והחיפוש התחמם. הוא כותב ליומן שורה לכל שלב ושורת סיכום אחת, כמו `StartUpMeasurer` של IntelliJ בצורה הקטנה ביותר שלו.

**מה רואים:**
- **ביומן בלבד:**
  ```
  INFO  main app - [Startup] graph created: 412 ms
  INFO  main app - [Startup] first frame: 1080 ms
  INFO  catalog-warmup app - [Startup] catalog warmed: 1350 ms
  INFO  search-warmup app - [Startup] text index warm: 3950 ms
  INFO  search-warmup app - [Startup] search warmed: 7800 ms
  INFO  search-warmup app - [Startup] graph 412 · first frame 1080 · catalog 1350 · index 3950 · search 7800 ms after main; the process started 35 ms before main (native image)
  ```
- **עמודת המילישניות** שבכל שורה (פריט 1) נותנת זמן גם לשורות INFO שכבר קיימות:
  - `[CatalogCache] Precomputed catalog loaded` (`S/framework/database/CatalogCache.kt:160`);
  - `Pre-loaded N acronyms` (`S/framework/search/AcronymFrequencyCache.kt:149`);
  - `[PardesLexicon] built … in N ms` (`S/framework/search/PardesLexicon.kt:177`);
  - `[HybridSearchEngine] Dense semantic search loaded successfully.` (`HybridSearchEngine.kt:88`).
- **בממשק:** שום דבר. אין קובץ חדש, אלא אם החלטה 3.ג בוחרת בזה.

**איך זה עובד:**
1. **השעון.** `DiagnosticsLog.install()` קורא ל-`StartupTrace.start()`, כלומר אחרי `S/main.kt:167`, לפני `PlatformInfo.warmUp()` (שורה 175) ולפני `nucleusApplication` (שורה 183).
   - הזמן הוא `System.nanoTime()`.
   - בנוסף נכתב כמה זמן עבר מתחילת התהליך עד `main`, לפי `ProcessHandle.current().info().startInstant()`. בבנייה ל-JVM של Windows arm64 זה זמן העלייה של ה-JVM; ב-native image זה כמעט אפס.
   - זה הרעיון של `startTime` ו-`startTimeUnixNanoDiff` (`IJ:platform/diagnostic/src/StartUpMeasurer.java:45-46`).
2. **`mark(phase)`.**
   - כל שלב נרשם רק בפעם הראשונה (`putIfAbsent`), וכותב שורת `infoln`, שמגיעה ליומן דרך ה-sink של פריט 1.
   - כשכל השלבים שמחכים להם נרשמו, נכתבת שורת סיכום, פעם אחת.
   - שמות השלבים הם מזהים קבועים בצורה "מה + שם עצם", כמו ב-`StartUpMeasurer.java:25-28`.
   - אין סף ואין תור. ה-`MEASURE_THRESHOLD` של 10ms והתקרה של 300,000 פריטים (`:23`, ‏`:61-62`) נועדו לאלפי פעילויות, וכאן יש חמש.
3. **איפה כל סימון, לפי מה שהקוד מאפשר למדוד:**
   - **הגרף נוצר:** בתוך `.also { … }` של `createGraph<AppGraph>()` (`S/main.kt:223-227`), כשורה ראשונה. זו שורה אחת בבלוק של זית, שכבר יש בו שורה של פרדס (`TextLinks.library = …`).
   - **הפריים הראשון:** ב-`S/core/presentation/window/MainAppWindow.kt`, אחרי שורה 331 (`val commentaryCopy = rememberCommentaryCopyShortcut()`, הוו של פרדס שכבר שם): הערה ו-`StartupFirstFrame()`.
     - זו פונקציה `@Composable` עם `LaunchedEffect(Unit) { withFrameNanos { StartupTrace.mark(FIRST_FRAME) } }`, שחוזרת בפריים הראשון של החלון.
     - Nucleus יודע מתי הפריים הראשון הוצג (`N:.../TaoWindow.kt:1771-1785`, שקורא ל-`UpdateHandoff.signalReady()`), אבל לא חושף listener. `TaoWindow.onWindowReady` מחזיק listener אחד בלבד (`:1378-1380`), ולכן אי אפשר להשתמש בו.
     - כל חלון ראשי קורא לפונקציה, ורק הראשון נספר.
   - **הקטלוג התחמם:** ב-`warmCatalog` (`S/main.kt:526-531`), אחרי `graph.catalogAccess.warm()` (שורה 529). זו שורה אחת בפונקציה של זית.
   - **האינדקס נפתח והתחמם:** ב-`HybridSearchEngine.warmUp()` של פרדס (`HybridSearchEngine.kt:177-188`), אחרי `lexical.warmUp()` (שורה 178). זה כולל שני סבבים של ארבעה חיפושים (`LuceneSearchEngine.kt:73-74`).
     - בפרדס תמיד יש `installedSemanticSearch`: התוסף `disabled` מעביר את החיפוש ל-`HybridSearchEngine` (`SeforimApp/addons/semantic/disabled/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/InstalledSemanticSearch.kt:11-17`).
     - רק `warmSearch` קורא ל-`warmUp()` (`S/main.kt:543`), בחוט רקע, ולא בהרצת E2E (שורה 384).
   - **החיפוש התחמם:** ב-`warmSearch` של פרדס, אחרי `runBlocking { engine.warmUp() }` (`S/main.kt:543`). כך השלב נספר גם כשקובצי המשמעות חסרים, ו-`ensureSemantic()` עוצר את `warmUp` (`HybridSearchEngine.kt:179`).
4. **מה נלקח מ-IntelliJ:** הרעיון בלבד, בלי קוד מועתק. שעון אחד, שלבים שהשמות שלהם הם מזהים, ושורת דוח אחת בסוף (`logAndClearStats` ב-`IJ:platform/diagnostic/startUpPerformanceReporter/src/StartUpPerformanceReporter.kt`).

**מה משתנה בקוד:**

| קובץ | חדש/קיים | של מי | מה בדיוק | טביעת רגל |
|---|---|---|---|---|
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/StartupTrace.kt` | חדש | פרדס | `object StartupTrace` (`start`, ‏`mark`, ‏`Phase`, הסיכום), ו-`@Composable fun StartupFirstFrame()`. כ-70 שורות | (ב) |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/DiagnosticsLog.kt` | חדש (מפריט 1) | פרדס | שורה אחת: `StartupTrace.start()` | (ב) |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/main.kt` | קיים | זית ששונה | שורה ב-`.also` (אחרי 223), שורה ב-`warmCatalog` (אחרי 529), שורה ב-`warmSearch` (אחרי 543), ו-import. ‏+4 | (ג) בשתי שורות בקוד של זית; (ב) בפונקציה של פרדס |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/core/presentation/window/MainAppWindow.kt` | קיים | זית ששונה (+21 −24) | אחרי שורה 331: הערה ו-`StartupFirstFrame()`, ו-import. ‏+3 | (ג) |
| `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features/search/semantic/HybridSearchEngine.kt` | קיים | פרדס (821 שורות) | סימון אחרי שורה 178, ו-import. ‏+2 | (ב) |
| `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/framework/diagnostics/StartupTraceTest.kt` | חדש | פרדס | כ-50 שורות | (ב) |

**רשומות שצריך במפת השינויים:**
- רשומות ל-`StartupTrace.kt` ול-`StartupTraceTest.kt` בקבוצה החדשה.
- `main.kt`: ה-Change עולה ל-+83 −8 עם פריט 1, ובתבליט של היומן מוסיפים את שלושת הסימונים.
- `MainAppWindow.kt` (שורות 445-467): Change ‏+24 −24, ותבליט חדש, "What, the start's first frame". ה-Merge risk צריך לומר ש-0.6.13 משנה את הקובץ הזה (Ctrl+Tab, במפה בשורה 7570).
- `HybridSearchEngine.kt` (משורה 4546): ‏Size 823, ו-What שמזכיר את הסימון.

**החלטות פתוחות:**
- **3.א אילו שלבים מסמנים.**
  - *מומלץ:* הארבעה שהתבקשו, ועוד "החיפוש התחמם". זה שתי שורות בקוד של זית ב-`main.kt`, שורה אחת ב-`MainAppWindow.kt`, ושתי שורות בקוד של פרדס.
  - אפשרות מינימלית: רק עמודת המילישניות, בלי אף וו. אז אין פריים ראשון, והשלבים נקראים מתוך השורות שכבר קיימות.
- **3.ב איך מודדים את הפריים הראשון.**
  - *מומלץ:* `withFrameNanos` ב-`MainAppWindow`.
  - אחר כך, בשלב ב' של פריט 2, אפשר להציע ל-Nucleus אות ציבורי "הפריים הראשון הוצג", לפי מה ש-`TaoWindow` כבר יודע (ד).
- **3.ג היסטוריה בין הפעלות.**
  - *מומלץ:* רק היומן, שמחזיק שבועות של הפעלות ברמה הרגילה.
  - אפשרות אחרת: `logs/startup.csv`, שורה לכל הפעלה (גרסה, native או JVM, השלבים), כדי להשוות בין גרסאות.
- **3.ד לסמן גם את בדיקת המעבד הגרפי ואת הצעדים שממתינים מההפעלה הקודמת** (`PlatformInfo.warmUp()`, ‏`S/main.kt:175`; ‏`:196-218`). *מומלץ:* לא. זה עוד שתיים עד ארבע שורות ב-`main.kt`. עמודת המילישניות, והשורות שכבר קיימות (`[PendingDbCleanup] …`, ‏`[PendingSemanticPack] …`), מראות אותם בחלקם.
- **3.ה שורת סיכום כשחסרים שלבים** (אשף ההתקנה, עדכון המסד, E2E). *מומלץ:* בלי סיכום. נשארות השורות של כל שלב.

**סיכונים ולא-ידועים:**
- **`withFrameNanos`** חוזר כשהפריים הראשון מורכב ומצויר, לפני שמערכת ההפעלה מציגה אותו, כלומר כמה מילישניות לפני שרואים אותו **(לא נמדד)**.
- **`ProcessHandle.info().startInstant()` ב-native image** **(לא נבדק)**. `info().command()` כבר בשימוש בקוד שרץ ב-native image (`S/core/presentation/utils/CliTerminalLauncher.kt:77-84`). אם הוא חסר, הסיכום נכתב בלי החלק הזה.
- **מיזוג עם זית.** שורות ב-`main.kt` (Merge risk: high) וב-`MainAppWindow.kt`, ש-0.6.13 משנה.
- **המספרים תלויים במטמון הדיסק.** עלייה קרה ועלייה חמה שונות מאוד, וצריך להשוות בין מקרים דומים. בבנייה ל-JVM נכנסים גם JIT ו-AOT.
- **פרטיות:** אין. רק זמנים.

**בדיקות:**
- **`T/framework/diagnostics/StartupTraceTest.kt`:**
  - שלב נרשם פעם אחת בלבד;
  - הזמנים יחסיים להתחלה, לא שליליים, ובסדר שבו סומנו;
  - הסיכום נכתב רק כשכל השלבים שמחכים להם נרשמו, ורק פעם אחת;
  - שמות השלבים קבועים.
- **בתוכנה בענן:** שורות `[Startup]` ושורת הסיכום ב-`pardes.log`; השוואה בין שתי הפעלות.
- **בגרסת ניסיון ל-Windows ב-native image:** המספרים שבעלי המאגר רואים אצלם.

**היקף ותלות:**
- **היקף:** כ-70 שורות, כ-50 שורות בדיקה, וכ-9 שורות בקבצים קיימים.
- **מאמץ:** S, חצי יום.
- **תלות:** תלוי בפריט 1, ב-sink שלו. בלעדיו, השורות מגיעות רק לקונסולה, ורק עם `SEFORIMAPP_LOGGING`. לא תלוי בפריט 2.

---

### סדר עבודה מוצע

1. פריט 1, ואחריו פריט 2, כל אחד ב-commit משלו באותו ענף. אחר כך גרסת ניסיון ל-Windows x64, שעונה גם על השאלה של `Thread.getAllStackTraces()` ב-native image, עם commit הקפיאה הזמני.
2. פריט 3.
3. הדיון ב-Nucleus, שבעלי המאגר פותחים. אם יוסכם, אב-טיפוס ב-efrul/Nucleus, שנכנס לפרדס דרך `nucleus-fork/`.

</div>

## The brief the agent got, in short

> Write an exact, code-verified implementation spec in Hebrew, planning only, for three diagnostics features: a rolling log file in the brand's data folder with a button that opens the logs' folder and a "detailed log" switch on the feedback settings page; Nucleus's `TaoEventLoopWatchdog` wired so that freezes reach that log with all thread stacks, with `expectUnresponsive {}` around known long operations and a proposal to Nucleus for a cross-platform sampler; and the time of each startup phase logged, as IntelliJ's StartUpMeasurer does. To find out in the code: Pardes's logger and its backends, the data folder per brand, `main.kt`'s startup, `CrashReporting`, the feedback settings page, what a GraalVM native image needs, which Nucleus version Pardes builds against and whether the watchdog's API is in it, and IntelliJ's own mechanisms.
