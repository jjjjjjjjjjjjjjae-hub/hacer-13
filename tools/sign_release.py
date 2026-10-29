#!/usr/bin/env python3
"""Sign a data-only HACER 13 menu payload with an offline Ed25519 private key.

Install dependency: python -m pip install cryptography
Usage:
  python tools/sign_release.py private_key.pem payload.json updates/lac/release.json
NEVER commit the private key to GitHub.
"""
import base64
import json
import pathlib
import sys
from cryptography.hazmat.primitives.serialization import load_pem_private_key

def main():
    if len(sys.argv) != 4:
        raise SystemExit("Usage: sign_release.py PRIVATE_KEY.pem PAYLOAD.json OUTPUT.json")
    key_path, payload_path, output_path = map(pathlib.Path, sys.argv[1:])
    private = load_pem_private_key(key_path.read_bytes(), password=None)
    payload = json.loads(payload_path.read_text(encoding="utf-8"))
    if payload.get("schema") != 1 or not isinstance(payload.get("features"), list):
        raise SystemExit("Expected schema=1 and features array")
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    if len(raw) > 32768:
        raise SystemExit("Payload > 32 KiB")
    envelope = {
        "algorithm": "Ed25519",
        "payload_b64": base64.b64encode(raw).decode("ascii"),
        "signature_b64": base64.b64encode(private.sign(raw)).decode("ascii")
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(envelope, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Signed data revision", payload.get("revision"), "into", output_path)

if __name__ == "__main__":
    main()
