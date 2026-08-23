#!/bin/bash
# Sync script for Chronos context

SHARED_BRAIN="/Users/sahil.makkar@grofers.com/.gemini/antigravity-cli/brain/ded15d14-3fea-4e72-8cd7-e0707e46024e"
DOCS_DIR="./docs"

case "$1" in
  pull)
    echo "Pulling context from shared brain to docs/..."
    mkdir -p "$DOCS_DIR"
    cp -R "$SHARED_BRAIN"/*.md "$DOCS_DIR"/ 2>/dev/null
    cp -R "$SHARED_BRAIN"/*.metadata.json "$DOCS_DIR"/ 2>/dev/null
    echo "Done!"
    ;;
  push)
    echo "Pushing context from docs/ to shared brain..."
    mkdir -p "$SHARED_BRAIN"
    cp -R "$DOCS_DIR"/*.md "$SHARED_BRAIN"/ 2>/dev/null
    cp -R "$DOCS_DIR"/*.metadata.json "$SHARED_BRAIN"/ 2>/dev/null
    echo "Done!"
    ;;
  chat)
    # Pull latest docs, run agy, then push updates back
    "$0" pull
    echo "Starting agy..."
    agy
    "$0" push
    ;;
  *)
    echo "Usage: $0 {pull|push|chat}"
    exit 1
    ;;
esac
