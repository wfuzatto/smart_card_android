#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../backend"
test -f .env || { echo "Copie .env.example para .env e configure LAB_API_KEY"; exit 1; }
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
