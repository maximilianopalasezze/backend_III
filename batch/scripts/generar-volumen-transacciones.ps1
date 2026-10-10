param(
    [ValidateRange(1, 1000)]
    [int]$Repeticiones = 100
)

$ErrorActionPreference = 'Stop'
$carpetaBatch = Split-Path -Parent $PSScriptRoot
$origen = Join-Path $carpetaBatch 'src/main/resources/data/semana_9/movimientos_financieros_diarios.csv'
$lineas = @(Get-Content -LiteralPath $origen -Encoding UTF8)
if ($lineas.Count -ne 1001 -or $lineas[0] -ne 'id,fecha,monto,tipo') {
    throw 'El CSV oficial debe contener el encabezado esperado y 1000 registros.'
}

# Se conserva cada dato de fecha, monto y tipo, incluidos los datos invalidos.
# Cada copia recibe un rango de IDs distinto para evitar escrituras duplicadas.
$filas = @()
$ids = [System.Collections.Generic.HashSet[long]]::new()
foreach ($linea in $lineas[1..1000]) {
    $campos = $linea.Split(',')
    $idOrigen = [long]0
    if ($campos.Count -ne 4 -or
        -not [long]::TryParse($campos[0], [ref]$idOrigen) -or
        $idOrigen -lt 1 -or $idOrigen -gt 1000 -or
        -not $ids.Add($idOrigen)) {
        throw 'El CSV oficial debe tener cuatro columnas e IDs unicos entre 1 y 1000.'
    }
    $filas += [PSCustomObject]@{
        Id = $idOrigen
        Datos = ($campos[1..3] -join ',')
    }
}

$cantidad = 1000 * $Repeticiones
$directorioSalida = Join-Path $carpetaBatch '.local/datos-prueba'
New-Item -ItemType Directory -Path $directorioSalida -Force | Out-Null
$salida = Join-Path $directorioSalida "transacciones_$cantidad.csv"
$temporal = "$salida.tmp"
$escritor = [System.IO.StreamWriter]::new(
    $temporal, $false, [System.Text.UTF8Encoding]::new($false))
try {
    $escritor.WriteLine('id,fecha,monto,tipo')
    for ($copia = 0; $copia -lt $Repeticiones; $copia++) {
        foreach ($fila in $filas) {
            $idNuevo = $fila.Id + (1000L * $copia)
            $escritor.WriteLine("$idNuevo,$($fila.Datos)")
        }
    }
} finally {
    $escritor.Dispose()
}
Move-Item -LiteralPath $temporal -Destination $salida -Force
$uriSalida = [System.Uri]::new($salida).AbsoluteUri
$manifiesto = [ordered]@{
    origen = 'fin_legacy_data/data/semana_3/movimientos_financieros_diarios.csv'
    sha256_origen = (Get-FileHash -LiteralPath $origen -Algorithm SHA256).Hash
    repeticiones = $Repeticiones
    registros_generados = $cantidad
    transformacion = 'Repetir filas; ID nuevo = ID original + 1000 * indice de copia; otros campos intactos.'
    uri_salida = $uriSalida
    sha256_salida = (Get-FileHash -LiteralPath $salida -Algorithm SHA256).Hash
}
$manifiesto | ConvertTo-Json | Set-Content -LiteralPath "$salida.json" -Encoding UTF8

Write-Host "Registros generados: $cantidad"
Write-Host "Archivo: $salida"
Write-Host "URI para BATCH_ARCHIVO_TRANSACCIONES: $uriSalida"
Write-Host "SHA-256 del archivo: $($manifiesto.sha256_salida)"
Write-Host "Manifiesto: $salida.json"
