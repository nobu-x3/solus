#!/usr/bin/env python3
"""
download_model.py

Downloads model files from Hugging Face Hub (via huggingface_hub) into a local directory,
supports filtering via glob patterns, and optionally merges .gguf-split files into a unified .gguf
using gguf-split tool (from llama.cpp) if multiple shards are found and merge flag is given.
"""

import argparse
import os
import sys
import subprocess
from huggingface_hub import snapshot_download

def download_model(repo_id: str, out_dir: str, pattern: str, token: str = None) -> str:
    """Download the snapshot of repo_id into out_dir, only files matching pattern."""
    print(f"Downloading from {repo_id} → {out_dir} (pattern={pattern})")
    path = snapshot_download(
        repo_id = repo_id,
        local_dir = out_dir,
        allow_patterns = [pattern],
        token = token,
    )
    print("Download complete; snapshot path:", path)
    return path

def find_sharded_ggufs(dir_path: str):
    """Return list of .gguf-split-* (or *-of-*) files in directory."""
    files = [f for f in os.listdir(dir_path)
             if f.endswith(".gguf") is False and ("‐of-" in f or ".gguf-split" in f)]
    return sorted(files)

def merge_gguf_shards(dir_path: str, shards: list, output_name: str):
    """Invoke gguf-split --merge on first shard to produce output_name."""
    first_shard = shards[0]
    output_path = os.path.join(dir_path, output_name)
    cmd = ["gguf-split", "--merge", os.path.join(dir_path, first_shard), output_path]
    print("Merging shards:", shards)
    print("Running:", " ".join(cmd))
    subprocess.check_call(cmd)
    print("Merge complete →", output_path)
    return output_path

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Download and optionally merge GGUF model files from Hugging Face.")
    parser.add_argument("--repo", required=True, help="Hugging Face model repo id e.g. user/model-name")
    parser.add_argument("--out-dir", required=True, help="Local directory to download into")
    parser.add_argument("--pattern", default="*.gguf", help="Glob pattern for files to download (default: *.gguf)")
    parser.add_argument("--token", default=None, help="Hugging Face API token (optional).")
    parser.add_argument("--merge-shards", action="store_true",
                        help="If shards detected (-of-N or -split), merge into single .gguf via gguf-split.")
    parser.add_argument("--output-name", default=None,
                        help="When merging, the output file name (within out-dir). If omitted, uses repo name + “.gguf”.")
    args = parser.parse_args()

    token = args.token
    if token is None:
        token = os.environ.get("HF_HUB_TOKEN")

    out_dir = args.out_dir
    os.makedirs(out_dir, exist_ok=True)

    # Step 1: Download via huggingface_hub
    download_path = download_model(args.repo, out_dir, args.pattern, token)

    # Step 2: If merging requested, detect shards and merge
    if args.merge_shards:
        shards = find_sharded_ggufs(out_dir)
        if len(shards) >= 2:
            # Determine output filename
            if args.output_name:
                out_name = args.output_name
            else:
                repo_base = args.repo.split("/")[-1]
                out_name = f"{repo_base}.gguf"
            merged_path = merge_gguf_shards(out_dir, shards, out_name)
            print("Merged model available at:", merged_path)
        else:
            print("No multiple shards found (found {} file(s)). Skipping merge.".format(len(shards)))
    else:
        print("Merge flag not set; skipping shard merge check.")
