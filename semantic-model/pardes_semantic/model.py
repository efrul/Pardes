"""Loading dicta-il/neodictabert-bilingual-embed and the sentence-embedding graph that is exported to ONNX.

The exported graph takes input_ids and attention_mask ([batch, tokens], int64) and returns `sentence_embedding`
([batch, 768], float32): the model's last hidden state, mean-pooled over the attention mask (as its
1_Pooling/config.json does) and L2-normalised. The app reads that output as is, so the pooling lives in the graph and
not in Kotlin.
"""

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F
from torch import nn

MODEL_ID = "dicta-il/neodictabert-bilingual-embed"
# Pinned: the vectors of an index are only comparable with queries embedded by the same weights
MODEL_REVISION = "1da86250a8021a7f7c3e427defbd9604fdb16c25"
DIMENSION = 768
# The model card's usage: "query: " before a query, nothing before a document
QUERY_PREFIX = "query: "
DOCUMENT_PREFIX = ""
QUERY_MAX_TOKENS = 128
DOCUMENT_MAX_TOKENS = 512


class EagerRMSNorm(nn.Module):
    """nn.RMSNorm spelled out: torch exports nn.RMSNorm as the opset-23 RMSNormalization op, which ONNX Runtime 1.17
    (the app's) does not have. Same formula as the model's own NeoBERTEagerRMSNorm."""

    def __init__(self, weight: torch.Tensor, eps: float):
        super().__init__()
        self.weight = nn.Parameter(weight.detach().clone())
        self.eps = eps

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        dtype = x.dtype
        x = x.to(torch.float32)
        x = x * torch.rsqrt(x.pow(2).mean(-1, keepdim=True) + self.eps)
        return self.weight * x.to(dtype)


def _swap_rms_norms(module: nn.Module) -> int:
    swapped = 0
    for name, child in module.named_children():
        if isinstance(child, nn.RMSNorm):
            setattr(module, name, EagerRMSNorm(child.weight, child.eps if child.eps is not None else 1e-6))
            swapped += 1
        else:
            swapped += _swap_rms_norms(child)
    return swapped


class SentenceEmbedder(nn.Module):
    def __init__(self, base: nn.Module, dense: nn.Linear | None = None):
        super().__init__()
        self.base = base
        # A distilled model's projection to fewer dimensions (sentence-transformers' Dense, identity activation)
        self.dense = dense

    @property
    def dimension(self) -> int:
        return self.dense.out_features if self.dense is not None else self.base.config.hidden_size

    def forward(self, input_ids: torch.Tensor, attention_mask: torch.Tensor) -> torch.Tensor:
        # NeoBERT's SDPA path masks padding with a boolean mask; its "onnx_eager" path multiplies the scores by the
        # mask instead (padding still attended), so it is not used: batches of the corpus are padded
        hidden = self.base(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state
        mask = attention_mask.unsqueeze(-1).to(hidden.dtype)
        pooled = (hidden * mask).sum(dim=1) / mask.sum(dim=1).clamp(min=1e-9)
        if self.dense is not None:
            pooled = self.dense(pooled)
        return F.normalize(pooled, p=2.0, dim=-1)


@dataclass
class Loaded:
    embedder: SentenceEmbedder
    tokenizer: object
    device: torch.device
    model_id: str


def _dense(model_dir: Path, dtype: torch.dtype) -> nn.Linear | None:
    """The Dense module of a sentence-transformers model directory, if it has one."""
    modules = json.loads((model_dir / "modules.json").read_text("utf-8")) if (model_dir / "modules.json").exists() else []
    for module in modules:
        if module["type"].endswith("models.Dense"):
            d = model_dir / module["path"]
            cfg = json.loads((d / "config.json").read_text("utf-8"))
            act = cfg.get("activation_function", "torch.nn.modules.linear.Identity")
            if not act.endswith("Identity"):
                raise ValueError(f"{d}: activation {act} is not supported (identity only)")
            from safetensors.torch import load_file

            weights = load_file(str(d / "model.safetensors")) if (d / "model.safetensors").exists() \
                else torch.load(d / "pytorch_model.bin", map_location="cpu")
            linear = nn.Linear(cfg["in_features"], cfg["out_features"], bias=cfg.get("bias", True))
            linear.load_state_dict({k.removeprefix("linear."): v for k, v in weights.items()})
            return linear.to(dtype)
    return None


def model_id(source: str | None) -> str:
    """What the manifest and the vector index call the weights: queries and corpus must share it."""
    from .clean import CLEANER_ID

    if source is None:
        return f"neodictabert-bilingual-embed@{MODEL_REVISION[:7]}/{CLEANER_ID}"
    d = Path(source)
    h = hashlib.sha256()
    for f in sorted(d.rglob("*.safetensors")):
        h.update(f.read_bytes())
    return f"{d.name}@{h.hexdigest()[:7]}/{CLEANER_ID}"


def load(device: str = "cpu", dtype: torch.dtype = torch.float32, source: str | None = None) -> Loaded:
    """The pinned Dicta model, or with [source] a local sentence-transformers directory (train_model.py,
    distill_model.py): same tokenizer, possibly fewer layers and a Dense projection."""
    from transformers import AutoModel, AutoTokenizer

    name, revision = (MODEL_ID, MODEL_REVISION) if source is None else (source, None)
    tokenizer = AutoTokenizer.from_pretrained(name, revision=revision)
    base = AutoModel.from_pretrained(name, revision=revision, trust_remote_code=True, dtype=dtype)
    _swap_rms_norms(base)
    dense = _dense(Path(source), dtype) if source is not None else None
    dev = torch.device(device)
    embedder = SentenceEmbedder(base, dense).to(dev).eval()
    return Loaded(embedder, tokenizer, dev, model_id(source))


def tokenize(tokenizer, texts: list[str], max_tokens: int) -> dict[str, torch.Tensor]:
    return tokenizer(texts, padding=True, truncation=True, max_length=max_tokens, return_tensors="pt",
                     return_token_type_ids=False)


@torch.inference_mode()
def embed(loaded: Loaded, texts: list[str], max_tokens: int) -> np.ndarray:
    enc = tokenize(loaded.tokenizer, texts, max_tokens)
    out = loaded.embedder(enc["input_ids"].to(loaded.device), enc["attention_mask"].to(loaded.device))
    return out.float().cpu().numpy()


def quantize_int8(vectors: np.ndarray) -> np.ndarray:
    """Per vector, the largest component maps to 127, as LuceneTextIndexWriter.quantizeInt8 and the app's
    OnnxEmbeddingProvider do (COSINE ignores the scale), bit for bit: v * 127f / max in float32 as Kotlin computes it,
    then roundToInt, which rounds halves up (numpy's rint rounds to even): floor(x + 0.5), exact in float64."""
    v = np.asarray(vectors, dtype=np.float32)
    peak = np.abs(v).max(axis=-1, keepdims=True)
    safe = np.where(peak > 0, peak, np.float32(1))
    q = np.floor((v * np.float32(127.0) / safe).astype(np.float64) + 0.5)
    return np.where(peak > 0, np.clip(q, -128, 127), 0).astype(np.int8)
