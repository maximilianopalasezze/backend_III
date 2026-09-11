param([ValidateRange(10,100)][int]$Muestras=20)
$ErrorActionPreference='Stop'
$raiz=Split-Path $PSScriptRoot -Parent
$local=Join-Path $raiz '.local'
$config=Import-Clixml (Join-Path $local 'config.clixml')
$ca=Join-Path $local 'certificados-locales.pem'
$curl=(Get-Command curl.exe -ErrorAction Stop).Source
$destino=Join-Path $raiz 'mediciones'
New-Item -ItemType Directory -Force -Path $destino | Out-Null
$registros=@()
$temporales=@()
try {
    foreach ($canal in @('web','movil','cajero')) {
        $puerto=@{web=8081;movil=8082;cajero=8083}[$canal]
        $base="https://localhost:$puerto"
        $id=[Guid]::NewGuid().ToString('N')
        $cred=Join-Path $local "$id-cred.json"
        $auth=Join-Path $local "$id-auth.json"
        $cab=Join-Path $local "$id-header.txt"
        $body=Join-Path $local "$id-body.json"
        $temporales+=@($cred,$auth,$cab,$body)
        $clave=[System.Net.NetworkCredential]::new('', $config[$canal].login).Password
        [IO.File]::WriteAllText($cred, (@{usuario="$canal-demo";password=$clave}|ConvertTo-Json -Compress))
        $estado=& $curl --silent --show-error --max-time 20 --ssl-revoke-best-effort --cacert $ca -H 'Content-Type: application/json' --data-binary "@$cred" -o $auth -w '%{http_code}' "$base/api/auth/token"
        if ($LASTEXITCODE -ne 0 -or $estado -ne '200') { throw "Fallo de autenticacion o TLS en $canal. Revisa que el BFF este iniciado." }
        $token=(Get-Content $auth -Raw | ConvertFrom-Json).access_token
        [IO.File]::WriteAllText($cab,"Authorization: Bearer $token")
        $ruta="/api/bff/$canal/cuentas/106"
        if ($canal -eq 'cajero') { $ruta+='/saldo' }
        for($i=0;$i -lt ($Muestras+3);$i++) {
            $salida=& $curl --silent --show-error --max-time 20 --ssl-revoke-best-effort --cacert $ca --compressed -H "@$cab" -o $body -w '%{http_code},%{time_total},%{size_download}' "$base$ruta"
            if($LASTEXITCODE -ne 0) { throw "Fallo de conexion en $canal" }
            $valores=$salida.Split(',')
            if($valores[0] -ne '200') { throw "HTTP $($valores[0]) en $canal; no se registran errores como resultados validos." }
            if($i -ge 3) {
                $ms=1000*[double]::Parse($valores[1],[Globalization.CultureInfo]::InvariantCulture)
                $registros += [pscustomobject]@{
                    canal=$canal; muestra=($i-2); http=200
                    tiempo_ms=[Math]::Round($ms,3)
                    bytes_transferidos=[long]$valores[2]
                    bytes_json=(Get-Item $body).Length
                }
            }
        }
    }
    $fecha=Get-Date -Format 'yyyyMMdd-HHmmss'
    $registros | Export-Csv (Join-Path $destino "muestras-$fecha.csv") -NoTypeInformation -Encoding UTF8
    $resumen=foreach($canal in @('web','movil','cajero')) {
        $grupo=@($registros|Where-Object canal -eq $canal)
        $tiempos=@($grupo.tiempo_ms|Sort-Object)
        [pscustomobject]@{
            canal=$canal; muestras=$grupo.Count
            p50_ms=$tiempos[[Math]::Ceiling(0.50*$grupo.Count)-1]
            p95_ms=$tiempos[[Math]::Ceiling(0.95*$grupo.Count)-1]
            bytes_transferidos_promedio=[Math]::Round(($grupo.bytes_transferidos|Measure-Object -Average).Average,1)
            bytes_json_promedio=[Math]::Round(($grupo.bytes_json|Measure-Object -Average).Average,1)
        }
    }
    $resumen | Export-Csv (Join-Path $destino "resumen-$fecha.csv") -NoTypeInformation -Encoding UTF8
    $resumen | Format-Table -AutoSize
    Write-Host 'Mediciones guardadas. Se descartaron tres solicitudes de calentamiento por canal; no se ejecutaron retiros.'
} finally {
    foreach($archivo in $temporales) { Remove-Item -LiteralPath $archivo -ErrorAction SilentlyContinue }
}
