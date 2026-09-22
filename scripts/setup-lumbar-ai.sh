#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LUMBAR_AI_ROOT="${WEASIS_LUMBAR_AI_ROOT:-$HOME/.weasis/ai/lumbar}"
LUMBAR_PYTHON="${WEASIS_LUMBAR_PYTHON:-/opt/homebrew/bin/python3.13}"
umask 077
mkdir -p "$LUMBAR_AI_ROOT/models"
if [[ ! -x "$LUMBAR_AI_ROOT/venv/bin/python" ]]; then
  "$LUMBAR_PYTHON" -m venv "$LUMBAR_AI_ROOT/venv"
fi
"$LUMBAR_AI_ROOT/venv/bin/python" -m pip install -r "$ROOT/scripts/lumbar-ai/requirements.txt"
SPINEPS_SEGMENTOR_MODELS="$LUMBAR_AI_ROOT/models" SPINEPS_NO_CITATION_REMINDER=1 OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 ITK_GLOBAL_DEFAULT_NUMBER_OF_THREADS=2 \
  "$LUMBAR_AI_ROOT/venv/bin/python" -c 'import torch; assert torch.backends.mps.is_available(), "Apple Silicon MPS is required for this pilot"; from spineps import SpinepsPipeline; SpinepsPipeline(use_cpu=True); print("Lumbar models ready")'
cp "$ROOT/scripts/lumbar-ai/worker.py" "$LUMBAR_AI_ROOT/worker.py"
"$LUMBAR_AI_ROOT/venv/bin/python" -m pip freeze > "$LUMBAR_AI_ROOT/runtime-lock.txt"
"$LUMBAR_AI_ROOT/venv/bin/python" - "$LUMBAR_AI_ROOT" <<'PY'
import hashlib, json, sys
from pathlib import Path
root = Path(sys.argv[1])
manifest = {}
for path in sorted((root / 'models').rglob('*')):
    if path.is_file():
        with path.open('rb') as stream:
            manifest[str(path.relative_to(root))] = hashlib.file_digest(stream, 'sha256').hexdigest()
(root / 'model-checksums.json').write_text(json.dumps(manifest, indent=2))
PY
echo "Local lumbar mapping runtime installed at $LUMBAR_AI_ROOT"
