# Registra (o actualiza) el conector de Debezium sobre la tabla outbox.
# PUT sobre /config es idempotente: POST /connectors falla si el conector ya existe.
param(
    [string]$ConnectUrl = "http://localhost:8083",
    [string]$ConnectorName = "osf-outbox-connector",
    [string]$ConfigFile = "$PSScriptRoot/connect/osf-outbox-connector.json"
)

$ErrorActionPreference = "Stop"

Write-Host "Esperando a Kafka Connect en $ConnectUrl ..."
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-RestMethod -Uri "$ConnectUrl/" -Method Get -TimeoutSec 3 | Out-Null
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

$config = Get-Content $ConfigFile -Raw
Invoke-RestMethod -Uri "$ConnectUrl/connectors/$ConnectorName/config" -Method Put `
    -ContentType "application/json" -Body $config | Out-Null

Start-Sleep -Seconds 3
$status = Invoke-RestMethod -Uri "$ConnectUrl/connectors/$ConnectorName/status" -Method Get
Write-Host ("Conector: {0} | tarea: {1}" -f $status.connector.state, $status.tasks[0].state)

if ($status.connector.state -ne "RUNNING") {
    Write-Error "El conector no esta RUNNING"
    exit 1
}
