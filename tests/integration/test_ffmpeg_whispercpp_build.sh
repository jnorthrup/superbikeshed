#!/usr/bin/env bash
set -euo pipefail

# Integration test: build ffmpeg and whisper.cpp from source

# --- ffmpeg build ---
FFMPEG_DIR="ffmpeg"
FFMPEG_BUILD_SCRIPT="ffmpeg-build-script"
FFMPEG_BIN="/usr/local/bin/ffmpeg"

if ! command -v ffmpeg >/dev/null; then
  echo "[INFO] ffmpeg not found, building from source..."
  if [ ! -d "$FFMPEG_BUILD_SCRIPT" ]; then
    git clone https://github.com/markus-perl/ffmpeg-build-script.git "$FFMPEG_BUILD_SCRIPT"
  fi
  cd "$FFMPEG_BUILD_SCRIPT"
  ./build-ffmpeg --build
  cd ..
  FFMPEG_BIN="$FFMPEG_BUILD_SCRIPT/workspace/bin/ffmpeg"
else
  echo "[INFO] ffmpeg found in PATH. Skipping build."
  FFMPEG_BIN="$(command -v ffmpeg)"
fi

if [ -x "$FFMPEG_BIN" ]; then
  echo "[SUCCESS] ffmpeg binary present: $FFMPEG_BIN"
else
  echo "[FAIL] ffmpeg binary not found or not executable: $FFMPEG_BIN"
  exit 1
fi

# --- whisper.cpp build ---
WHISPER_DIR="whisper.cpp"
WHISPER_BIN="whisper.cpp/main"

if [ ! -d "$WHISPER_DIR" ]; then
  git clone https://github.com/ggerganov/whisper.cpp.git "$WHISPER_DIR"
fi
cd "$WHISPER_DIR"

# Detect Apple Silicon for CoreML support
detect_apple_silicon() {
  unameOut="$(uname -s)"
  if [ "$unameOut" = "Darwin" ]; then
    archName="$(uname -m)"
    if [[ "$archName" == "arm64" ]]; then
      return 0
    fi
  fi
  return 1
}

if detect_apple_silicon; then
  echo "[INFO] Detected Apple Silicon. Building with CoreML support."
  WHISPER_COREML=1 make -j
else
  echo "[INFO] Building whisper.cpp (no CoreML)"
  make -j
fi
cd ..

if [ -x "$WHISPER_BIN" ]; then
  echo "[SUCCESS] whisper.cpp main binary present: $WHISPER_BIN"
else
  echo "[FAIL] whisper.cpp main binary not found or not executable: $WHISPER_BIN"
  exit 1
fi

echo "[INTEGRATION TEST PASSED] ffmpeg and whisper.cpp built successfully." 