# Notice: the semantic search model

The semantic search model of Pardes is **neodictabert-bilingual-embed** by
[Dicta: The Israel Center for Text Analysis](https://dicta.org.il/),
<https://huggingface.co/dicta-il/neodictabert-bilingual-embed>, used under the
[Creative Commons Attribution 4.0 International licence (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).

Changes by the Pardes project: when `pardes-model.json` says `"derived": true`, fine-tuned on the library's own texts
(passages its links pair, questions on its lines) and, for a model of fewer dimensions than Dicta's 768, distilled into
it; in every case converted to ONNX (mean pooling and L2 normalisation added to the graph) and its weights quantized to
int8. The vectors of the Pardes semantic index were computed with this model.

Models trained from run 3 on also learn from pairs made from:
- Klei Kodesh's index of the library's reference works (Sefer HeAruch, Mikropedia Talmudit, Metzudat Tzion), their own
  work, used with its owners' permission. Based on Klei Kodesh — https://kleikodesh.github.io/
- the Aramaic-Hebrew dictionary of the Torat Emet (תורת אמת) software, as Klei Kodesh's dictionary carries it. By what
  Klei Kodesh's developer wrote (2026-11-04), Klei Kodesh received Torat Emet's permission long ago; that permission was
  given to Klei Kodesh.

Models trained from 2026-11-19 on also learn from the searches Pardes's users graded in the app and sent, with their
consent, to improve its search: the results they marked relevant and not relevant, sent without anything that
identifies them. Models trained from 2026-11-23 on also learn from searches those users sent without grading: the
results of these were graded for the project by a language model (Claude, by Anthropic), on the users' scale.

Dicta does not endorse Pardes or this conversion. The model is provided as is, without warranties.

## The reranker

The reranker of Pardes's search (the folder `pardes-reranker`, which reads a search's first results again and orders
them) is **multilingual-reranker-small** by Horizon Labs,
<https://huggingface.co/Horizon-Labs/multilingual-reranker-small>, Copyright 2026 Horizon Labs, used under the
[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). It is a fine-tuned version of
**mmBERT-small** by the mmBERT authors (Johns Hopkins University CLSP),
<https://huggingface.co/jhu-clsp/mmBERT-small>, which is under the MIT License. The model's own `LICENSE` and `NOTICE`
files are in its folder, with this notice.

Changes by the Pardes project: its ONNX export's weights quantized to int8, and its table of tokens and its tokenizer
cut to the tokens the library's texts can use. Pardes trained nothing in it.

Horizon Labs and the mmBERT authors do not endorse Pardes or this conversion. The reranker is provided as is, without
warranties.

## Zayit

Pardes is based on Zayit by Elie (Elyahou) Gambache, who did not make Pardes and is not responsible for it. Neither
this model, its index nor the reranker comes from Zayit or uses Zayit's semantic search package.
