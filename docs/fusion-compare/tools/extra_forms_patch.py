"""For the measurement build only (build.sh): lets LuceneSearchEngine's copy take a word's extra forms (ExtraForms.kt).

    python extra_forms_patch.py BUILD_SRC_DIR/LuceneSearchEngine.kt

Each query word's dictionary entry gets, before it, one more entry holding ExtraForms.of(word) as variants. With no
-DextraForms the list is the dictionary's own, as in the app. Fails loudly when the code it changes is not there.
"""

import sys

path = sys.argv[1]
src = open(path, encoding="utf-8").read()
old = """                val expansion = magicDict?.expansionFor(token) ?: return@associateWith emptyList()
                listOf(expansion)"""
new = """                val expansion = magicDict?.expansionFor(token)
                val extra = ExtraForms.of(token)
                listOfNotNull(
                    extra.takeIf { it.isNotEmpty() }?.let {
                        MagicDictionaryIndex.Expansion(surface = emptyList(), variants = it, base = emptyList())
                    },
                    expansion,
                )"""
if old not in src:
    sys.exit(f"{path}: the tokenExpansions block changed; update extra_forms_patch.py")
open(path, "w", encoding="utf-8").write(src.replace(old, new, 1))
print(f"patched {path}")
