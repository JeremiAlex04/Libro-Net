param(
    [string]$Workspace = "d:\Proyectos\bibliotecaDistribuido",
    [string]$GatewayBase = "http://localhost:8080",
    [string]$BookIdConcurrencia = "123e4567-e89b-12d3-a456-426614174000",
    [string]$BookIdCristian = "11111111-1111-1111-1111-111111111111"
)

$ErrorActionPreference = "Stop"
Set-Location $Workspace

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$evidenceRoot = Join-Path $Workspace ("evidencias\pruebas_" + $timestamp)
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null

function Write-Log {
    param([string]$msg)
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $msg
    Write-Host $line
    Add-Content -Path (Join-Path $evidenceRoot "ejecucion.log") -Value $line
}

function Save-Json {
    param([string]$name, $obj)
    $path = Join-Path $evidenceRoot $name
    $obj | ConvertTo-Json -Depth 10 | Out-File -FilePath $path -Encoding utf8
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )

    $params = @{
        Method = $Method
        Uri = $Url
        Headers = $Headers
        TimeoutSec = 20
    }

    if ($null -ne $Body) {
        $params["Body"] = ($Body | ConvertTo-Json -Depth 5)
        $params["ContentType"] = "application/json"
    }

    return Invoke-WebRequest @params
}

Write-Log "Levantando servicios con docker compose up -d"
docker compose up -d | Out-File -FilePath (Join-Path $evidenceRoot "docker_up.txt") -Encoding utf8

Write-Log "Esperando disponibilidad de Gateway en /api/time"
$ready = $false
for ($i = 1; $i -le 40; $i++) {
    try {
        $res = Invoke-WebRequest -Method Get -Uri "$GatewayBase/api/time" -TimeoutSec 5
        if ($res.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ready) {
    throw "Gateway no disponible. Revisa docker compose logs."
}

Write-Log "===== PRUEBA 1: Round-robin catalogo ====="
$rrRows = @()
for ($i = 1; $i -le 10; $i++) {
    $res = Invoke-Api -Method Get -Url "$GatewayBase/api/catalogo/instancia"
    $body = $res.Content | ConvertFrom-Json
    $rrRows += [pscustomobject]@{
        intento = $i
        status = $res.StatusCode
        gatewayTarget = $res.Headers["X-Gateway-Routed-Host"]
        serviceInstanceHeader = $res.Headers["X-LibroNet-Instance"]
        serviceInstanceBody = $body.instance
    }
}
Save-Json -name "P2_round_robin.json" -obj $rrRows

Write-Log "===== PRUEBA 2: Cristian en prestamo ====="
$headersPrestamo = @{
    "X-Sede" = "Sede Norte"
    "X-Bibliotecario" = "admin_norte"
}
$prestamoRes = Invoke-Api -Method Post -Url "$GatewayBase/api/prestamos/$BookIdCristian?digital=false" -Headers $headersPrestamo
$prestamoMsg = [pscustomobject]@{
    status = $prestamoRes.StatusCode
    response = $prestamoRes.Content
    gatewayTarget = $prestamoRes.Headers["X-Gateway-Routed-Host"]
    instance = $prestamoRes.Headers["X-LibroNet-Instance"]
}
Save-Json -name "P3_cristian_prestamo_response.json" -obj $prestamoMsg

$allPrestamos = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/prestamos"
$lastPrestamo = $allPrestamos |
    Where-Object { $_.bibliotecario -eq "admin_norte" -and $_.libroId -eq $BookIdCristian } |
    Sort-Object fechaSolicitud -Descending |
    Select-Object -First 1
Save-Json -name "P3_cristian_prestamo_registro.json" -obj $lastPrestamo

Write-Log "===== PRUEBA 3: Concurrencia sobre ultimo ejemplar local ====="
$pgPassword = (docker exec libronet-postgres sh -lc 'echo "$POSTGRES_PASSWORD"').Trim()
if ([string]::IsNullOrWhiteSpace($pgPassword)) {
    throw "No se pudo obtener POSTGRES_PASSWORD desde el contenedor libronet-postgres"
}

$updateSql = "UPDATE libro SET copias_norte=1, copias_sur=0 WHERE id='$BookIdConcurrencia';"
$checkSql = "SELECT id, copias_norte, copias_sur FROM libro WHERE id='$BookIdConcurrencia';"

docker exec libronet-postgres sh -lc "PGPASSWORD='$pgPassword' psql -U postgres -d biblioteca_db -c \"$updateSql\"" |
    Out-File -FilePath (Join-Path $evidenceRoot "P1_preparacion_stock.txt") -Encoding utf8

$job1 = Start-Job -ScriptBlock {
    param($url)
    try {
        Invoke-WebRequest -Method Post -Uri $url -Headers @{"X-Sede"="Sede Norte";"X-Bibliotecario"="admin_norte"} -TimeoutSec 20 |
            Select-Object StatusCode, Content
    } catch {
        if ($_.Exception.Response) {
            $r = $_.Exception.Response
            $sr = New-Object System.IO.StreamReader($r.GetResponseStream())
            [pscustomobject]@{ StatusCode = [int]$r.StatusCode; Content = $sr.ReadToEnd() }
        } else {
            [pscustomobject]@{ StatusCode = 0; Content = $_.Exception.Message }
        }
    }
} -ArgumentList "$GatewayBase/api/prestamos/$BookIdConcurrencia?digital=false"

$job2 = Start-Job -ScriptBlock {
    param($url)
    try {
        Invoke-WebRequest -Method Post -Uri $url -Headers @{"X-Sede"="Sede Norte";"X-Bibliotecario"="admin_norte"} -TimeoutSec 20 |
            Select-Object StatusCode, Content
    } catch {
        if ($_.Exception.Response) {
            $r = $_.Exception.Response
            $sr = New-Object System.IO.StreamReader($r.GetResponseStream())
            [pscustomobject]@{ StatusCode = [int]$r.StatusCode; Content = $sr.ReadToEnd() }
        } else {
            [pscustomobject]@{ StatusCode = 0; Content = $_.Exception.Message }
        }
    }
} -ArgumentList "$GatewayBase/api/prestamos/$BookIdConcurrencia?digital=false"

Wait-Job $job1, $job2 | Out-Null
$concurrentResults = @(
    Receive-Job $job1
    Receive-Job $job2
)
Remove-Job $job1, $job2
Save-Json -name "P1_concurrencia_respuestas.json" -obj $concurrentResults

docker exec libronet-postgres sh -lc "PGPASSWORD='$pgPassword' psql -U postgres -d biblioteca_db -c \"$checkSql\"" |
    Out-File -FilePath (Join-Path $evidenceRoot "P1_stock_final.txt") -Encoding utf8

Write-Log "===== PRUEBA 4: Caida de lider y reeleccion ====="
$norte = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/norte/estado"
$sur = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/sur/estado"
$este = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/este/estado"
$estadosIniciales = [pscustomobject]@{ norte = $norte; sur = $sur; este = $este }
Save-Json -name "P4_estados_antes_caida.json" -obj $estadosIniciales

$leaderId = @($norte.liderId, $sur.liderId, $este.liderId | Where-Object { $_ -gt 0 } | Select-Object -First 1)
$leaderId = @($norte.liderId, $sur.liderId, $este.liderId) | Where-Object { $_ -gt 0 } | Select-Object -First 1
if (-not $leaderId) { throw "No se pudo detectar lider actual." }

$leaderSede = switch ($leaderId) {
    1 { "norte" }
    2 { "sur" }
    3 { "este" }
    default { throw "liderId no mapeado: $leaderId" }
}

$caidaRes = Invoke-WebRequest -Method Post -Uri "$GatewayBase/api/eleccion/$leaderSede/simular-caida?offline=true"
$caidaRes.Content | Out-File -FilePath (Join-Path $evidenceRoot "P4_caida_response.txt") -Encoding utf8
Start-Sleep -Seconds 8

$norteAfter = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/norte/estado"
$surAfter = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/sur/estado"
$esteAfter = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/este/estado"
$estadosAfter = [pscustomobject]@{ liderCaido = $leaderSede; norte = $norteAfter; sur = $surAfter; este = $esteAfter }
Save-Json -name "P4_estados_despues_caida.json" -obj $estadosAfter

$eventosLeader = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/$leaderSede/eventos"
$eventosLeader | Select-Object -Last 30 | Save-Json -name "P4_eventos_lider_caido.json"

Write-Log "===== PRUEBA 5: Recuperacion de nodo con RESYNC ====="
$onlineRes = Invoke-WebRequest -Method Post -Uri "$GatewayBase/api/eleccion/$leaderSede/simular-caida?offline=false"
$onlineRes.Content | Out-File -FilePath (Join-Path $evidenceRoot "P5_restore_response.txt") -Encoding utf8

try {
    $resyncRes = Invoke-WebRequest -Method Post -Uri "$GatewayBase/api/eleccion/$leaderSede/resincronizar"
    $resyncRes.Content | Out-File -FilePath (Join-Path $evidenceRoot "P5_resync_response.txt") -Encoding utf8
} catch {
    $_.Exception.Message | Out-File -FilePath (Join-Path $evidenceRoot "P5_resync_response.txt") -Encoding utf8
}

Start-Sleep -Seconds 2
$estadoResync1 = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/$leaderSede/estado"
Start-Sleep -Seconds 6
$estadoResync2 = Invoke-RestMethod -Method Get -Uri "$GatewayBase/api/eleccion/$leaderSede/estado"
Save-Json -name "P5_estado_resync_t1.json" -obj $estadoResync1
Save-Json -name "P5_estado_resync_t2.json" -obj $estadoResync2

Write-Log "Recolectando logs recientes de servicios clave"
docker compose logs --tail=200 gateway prestamos-norte prestamos-sur prestamos-este catalogo catalogo-2 |
    Out-File -FilePath (Join-Path $evidenceRoot "logs_servicios_tail200.txt") -Encoding utf8

Write-Log "Pruebas finalizadas. Evidencias generadas en: $evidenceRoot"
Write-Host "EVIDENCIAS => $evidenceRoot"
