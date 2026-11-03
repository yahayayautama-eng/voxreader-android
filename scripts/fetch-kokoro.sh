#!/usr/bin/env bash
# Downloads the Kokoro v1.1 multi-language voice (English + Mandarin) into the voice asset pack.
# The files are kept out of git because the model is over GitHub's 100 MB file limit.
# Run once per checkout, from the repository root: bash scripts/fetch-kokoro.sh
set -euo pipefail

URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2"
SHA256="259c9c8bc906d6078e4e0570421885dabb68b539c2f970fcbb890a58eeaf3e9c"
DEST="offlinevoices/src/main/assets/sherpa/kokoro"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# -C - resumes a dropped transfer; the 147 MB archive often needs it on slow links.
until curl -fSL --retry 5 -C - -o "$work/kokoro.tar.bz2" "$URL"; do echo "retrying download"; done
echo "$SHA256  $work/kokoro.tar.bz2" | sha256sum -c -
tar -xjf "$work/kokoro.tar.bz2" -C "$work"

src="$work/kokoro-int8-multi-lang-v1_1"
mkdir -p "$DEST"
cp "$src/model.int8.onnx" "$DEST/model.onnx"
# The espeak data and jieba dict in the archive are not copied: the app shares the espeak data
# already bundled for the English voice, and sherpa-onnx 1.13+ no longer reads the jieba dict.
cp "$src"/{voices.bin,tokens.txt,lexicon-us-en.txt,lexicon-zh.txt,phone-zh.fst,date-zh.fst,number-zh.fst,LICENSE} "$DEST/"
echo "Kokoro voice installed in $DEST"
