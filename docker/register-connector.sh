#!/usr/bin/env bash
# Registra (o actualiza) el conector de Debezium sobre la tabla outbox.
# PUT sobre /config es idempotente: POST /connectors falla si el conector ya existe.
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
CONNECTOR_NAME="${CONNECTOR_NAME:-osf-outbox-connector}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${CONFIG_FILE:-$SCRIPT_DIR/connect/osf-outbox-connector.json}"

echo "Esperando a Kafka Connect en $CONNECT_URL ..."
for _ in $(seq 1 60); do
    if curl -sf "$CONNECT_URL/" >/dev/null; then
        break
    fi
    sleep 2
done

curl -sS -X PUT "$CONNECT_URL/connectors/$CONNECTOR_NAME/config" \
     -H "Content-Type: application/json" \
     -d @"$CONFIG_FILE" >/dev/null

sleep 3
STATUS=$(curl -sS "$CONNECT_URL/connectors/$CONNECTOR_NAME/status")
echo "$STATUS"

echo "$STATUS" | grep -q '"state":"RUNNING"' || {
    echo "El conector no esta RUNNING" >&2
    exit 1
}
