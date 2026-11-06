# daf-layouts

The layouts of Pardes's daf view (צורת הדף): one JSON file a tractate of the Bavli, every line of the Gemara, Rashi
and Tosafot of the Vilna Shas with its words and its place on the page. They are pardes-rashi-ocr's `layouts/`,
from its commit c34117a1e. This branch holds nothing else: `.github/workflows/daf-release.yml` on main publishes them
as a `daf-<n>` release, which Pardes for desktop downloads them from (1.0.8 and later).

**Sources and licence**
- The lines' places were measured on the Vilna Shas's scans, which is in the public domain.
- The Gemara's words are Wikisource's text (Wikisource Talmud Bavli), through Sefaria, under CC BY-SA. Rashi and
  Tosafot are the Vilna edition's, through Sefaria, in the public domain.
- The files are distributed under CC BY-SA: https://creativecommons.org/licenses/by-sa/4.0/

Pardes is based on Zayit by Elie (Elyahou) Gambache, who did not make it and is not responsible for it.
