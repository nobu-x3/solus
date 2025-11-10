#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENV_DIR="$PROJECT_ROOT/.venv"
SCRIPT="$PROJECT_ROOT/scripts/download_model.py"

REPO_ID="Qwen/Qwen2.5-14B-Instruct-GGUF"
OUT_DIR="$PROJECT_ROOT/models"
PATTERN="*qwen2.5-14b-instruct-q4_k_m*.gguf"

echo -e "\n=== Model Setup ==="
if [ ! -d "$VENV_DIR" ]; then
  echo "Creating virtual environment at: $VENV_DIR"
  python3 -m venv "$VENV_DIR"
fi

# Upgrade pip and install dependencies
"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/python" -m pip install --upgrade huggingface_hub

# Now call the Python script
"$VENV_DIR/bin/python" "$SCRIPT" \
  --repo "$REPO_ID" \
  --out-dir "$OUT_DIR" \
  --pattern "$PATTERN" \
  --merge-shards \
  --output-name "qwen2.5-14b-instruct-q4_k_m.gguf"

echo " === Compiling llama.cpp separately ==="
mkdir ${PROJECT_ROOT}/vendor/llama.cpp/build -p
cmake -B ${PROJECT_ROOT}/vendor/llama.cpp/build ${PROJECT_ROOT}/vendor/llama.cpp
cmake --build ${PROJECT_ROOT}/vendor/llama.cpp/build -j $(nproc)

echo " === Merging GGUF model files ==="
${PROJECT_ROOT}/vendor/llama.cpp/build/bin/llama-gguf-split --merge ${OUT_DIR}/qwen2.5-14b-instruct-q4_k_m-00001-of-00003.gguf ${OUT_DIR}/qwen2.5-14b-instruct-q4_k_m.gguf

echo "✅ Done."
