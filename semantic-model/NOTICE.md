# Notice: the semantic search model

The semantic search model of Pardes is **neodictabert-bilingual-embed** by
[Dicta: The Israel Center for Text Analysis](https://dicta.org.il/),
<https://huggingface.co/dicta-il/neodictabert-bilingual-embed>, used under the
[Creative Commons Attribution 4.0 International licence (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).

Changes by the Pardes project: when `pardes-model.json` says `"derived": true`, fine-tuned on the library's own texts
(passages its links pair, questions on its lines) and, for a model of fewer dimensions than Dicta's 768, distilled into
it; in every case converted to ONNX (mean pooling and L2 normalisation added to the graph) and its weights quantized to
int8. The vectors of the Pardes semantic index were computed with this model.

Dicta does not endorse Pardes or this conversion. The model is provided as is, without warranties.

Pardes is based on Zayit by Elie (Elyahou) Gambache, who did not make Pardes and is not responsible for it. Neither
this model nor its index comes from Zayit or uses Zayit's semantic search package.
