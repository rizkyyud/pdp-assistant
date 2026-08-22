#!/bin/bash
Q="Apa itu data pribadi menurut hukum Indonesia?"

for TEMP in 0.0 0.3 0.9; do
  echo "=== temperature $TEMP ==="
  for i in 1 2 3; do
    curl -sS -X POST http://localhost:8080/api/chat \
      -H "Content-Type: application/json" \
      -d "{\"message\":\"$Q\",\"temperature\":$TEMP}"
    echo ""
  done
  echo ""
done