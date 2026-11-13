# Agents' reports

What the agents of a Claude Code session reported, kept as it came back so that a later session can start from it
instead of reading the same code again. Each run has a folder, `<date>-<name>/`, with one file per report: what it
was for, the commit it read, what was checked by hand afterwards, the report, and the brief the agent got. The line
numbers in a report are its commit's: find the place again by the function's name when the file has moved on.

A report is a reading, not a decision: the plan it was made for says what is done with it.

| Date | Run | What it was for | Code read | Reports |
|---|---|---|---|---|
| 2026-11-11 | [2026-11-11-stage2-code-maps](2026-11-11-stage2-code-maps) | The plan of stage 2 of Pardes for desktop (`docs/DESKTOP_STAGE2_PLAN.md`): where each of the sixteen requests meets the code. Four Explore agents side by side, read-only, and one reading by the session itself. | efrul/Pardes at `abcb687`; efrul/Nucleus at `65ccd12` for the restart | `daf-view.md` (requests 33, 34, 29, 21, the update by lines), `settings-feedback-startup.md` (30, 35, 29), `search-order-scope.md` (31, 37), `reader-fonts-limud-userdata.md` (32, 24, 25, 19), `semantic-pack-install.md` (36, the session's own reading) |

Two earlier researches for the same stage, on the docking of the reader's panes and on Otzaria's daily-study plugin
with the Nucleus PDF viewer, are in the private repository of the daf layouts (efrul/pardes-rashi-ocr,
`docs/agents/2026-11-11-docking` and `docs/agents/2026-11-11-otzaria-plugin-pdf`); `docs/DESKTOP_STAGE2.md` sums
them up.
