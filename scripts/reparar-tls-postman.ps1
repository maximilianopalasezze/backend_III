# Ejecutar desde scripts del proyecto. Windows PowerShell 5.1 y JDK 17.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$local = Join-Path $raiz '.local'
$configPath = Join-Path $local 'config.clixml'
if (!(Test-Path $configPath)) { throw 'Copia este archivo en la carpeta scripts del proyecto que ya tiene configuracion local.' }
$puertos = @([System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -in @(8081,8082,8083) })
if ($puertos.Count -gt 0) { throw 'Deten los BFF con Ctrl+C antes de cambiar los certificados (puertos 8081, 8082 y 8083).' }
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' } else { (Get-Command keytool -ErrorAction Stop).Source }
if (!(Test-Path $keytool)) { throw 'No se encuentra keytool. Revisa JAVA_HOME.' }
$config = Import-Clixml $configPath
$canales = @('web','movil','cajero')
$publico = Join-Path $raiz 'certificados-postman'
$publicPem = Join-Path $publico 'ca-banco-xyz.pem'
$marca = Join-Path $local 'tls-ca-instalado.txt'
if (Test-Path $marca) { Write-Host "La migracion ya se realizo. Carga en Postman: $publicPem"; return }
foreach ($nombre in @('ca-local.p12','ca-local-password.clixml','ca-banco-xyz.pem')) {
    if (Test-Path (Join-Path $local $nombre)) { throw "Ya existe $nombre sin marca de migracion. No se reemplaza; revisa la ejecucion anterior." }
}
if (Test-Path $publicPem) { throw 'Ya existe el certificado publico de destino. No se reemplaza automaticamente.' }
foreach ($canal in $canales) {
    if (!(Test-Path (Join-Path $local "$canal.p12")) -or !$config[$canal].tls) { throw "Falta el almacen o la clave TLS de $canal." }
}
function Ejecutar-Keytool([string[]]$Opciones) {
    & $keytool @Opciones
    if ($LASTEXITCODE -ne 0) { throw 'keytool devolvio un error. Los originales se conservan hasta validar los tres canales.' }
}
$id = [Guid]::NewGuid().ToString('N')
$etapa = Join-Path $local "tls-preparacion-$id"
$respaldo = Join-Path $local ("tls-respaldo-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + "-$id")
New-Item -ItemType Directory -Path $etapa | Out-Null
$oldCaEnv = $env:BFF_REPAIR_CA_PASS
$oldTlsEnv = $env:BFF_REPAIR_TLS_PASS
$nuevos = @('ca-local.p12','ca-local-password.clixml','ca-banco-xyz.pem','tls-ca-instalado.txt')
$existentes = @('certificados-locales.pem')
foreach ($canal in $canales) { $existentes += @("$canal.p12", "$canal.pem") }
$instalando = $false
try {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:BFF_REPAIR_CA_PASS = [Convert]::ToBase64String($bytes)
    ConvertTo-SecureString $env:BFF_REPAIR_CA_PASS -AsPlainText -Force | Export-Clixml (Join-Path $etapa 'ca-local-password.clixml')
    $caStore = Join-Path $etapa 'ca-local.p12'
    $caPem = Join-Path $etapa 'ca-banco-xyz.pem'
    Ejecutar-Keytool -Opciones @('-genkeypair','-noprompt','-alias','banco-xyz-ca','-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-startdate','-1d','-validity','730','-storetype','PKCS12','-keystore',$caStore,'-storepass:env','BFF_REPAIR_CA_PASS','-keypass:env','BFF_REPAIR_CA_PASS','-dname','CN=Banco XYZ CA Local,O=Banco XYZ,C=CL','-ext','BC:critical=ca:true,pathlen:0','-ext','KU:critical=keyCertSign,cRLSign')
    Ejecutar-Keytool -Opciones @('-exportcert','-rfc','-alias','banco-xyz-ca','-keystore',$caStore,'-storepass:env','BFF_REPAIR_CA_PASS','-file',$caPem)
    foreach ($canal in $canales) {
        Write-Host "Preparando cadena TLS de $canal..."
        $store = Join-Path $etapa "$canal.p12"
        $csr = Join-Path $etapa "$canal.csr"
        $leaf = Join-Path $etapa "$canal.pem"
        Copy-Item -LiteralPath (Join-Path $local "$canal.p12") -Destination $store
        $env:BFF_REPAIR_TLS_PASS = [Net.NetworkCredential]::new('', $config[$canal].tls).Password
        Ejecutar-Keytool -Opciones @('-certreq','-alias','bff-tls','-keystore',$store,'-storepass:env','BFF_REPAIR_TLS_PASS','-file',$csr)
        Ejecutar-Keytool -Opciones @('-gencert','-rfc','-alias','banco-xyz-ca','-keystore',$caStore,'-storepass:env','BFF_REPAIR_CA_PASS','-infile',$csr,'-outfile',$leaf,'-sigalg','SHA256withRSA','-startdate','-1d','-validity','365','-ext','BC:critical=ca:false','-ext','KU:critical=digitalSignature,keyEncipherment','-ext','EKU=serverAuth','-ext','SAN=dns:localhost,ip:127.0.0.1')
        Ejecutar-Keytool -Opciones @('-importcert','-noprompt','-alias','banco-xyz-ca','-keystore',$store,'-storepass:env','BFF_REPAIR_TLS_PASS','-file',$caPem)
        Ejecutar-Keytool -Opciones @('-importcert','-noprompt','-alias','bff-tls','-keystore',$store,'-storepass:env','BFF_REPAIR_TLS_PASS','-file',$leaf)
        $cadena = & $keytool -list -rfc -alias bff-tls -keystore $store -storepass:env BFF_REPAIR_TLS_PASS
        if ($LASTEXITCODE -ne 0 -or ([regex]::Matches(($cadena -join "`n"), 'BEGIN CERTIFICATE')).Count -ne 2) { throw "No se obtuvo una cadena de dos certificados para $canal." }
        Write-Host "Cadena de $canal verificada: servidor y CA."
    }
    # Compatibilidad con curl y medir-bff.ps1: el archivo ahora contiene la CA.
    Copy-Item -LiteralPath $caPem -Destination (Join-Path $etapa 'certificados-locales.pem')
    [IO.File]::WriteAllText((Join-Path $etapa 'tls-ca-instalado.txt'), "CA local instalada. Respaldo: $respaldo")
    New-Item -ItemType Directory -Path $respaldo | Out-Null
    foreach ($nombre in $existentes) {
        if (Test-Path (Join-Path $local $nombre)) { Copy-Item -LiteralPath (Join-Path $local $nombre) -Destination (Join-Path $respaldo $nombre) }
    }
    $instalando = $true
    foreach ($nombre in ($existentes + $nuevos)) { Copy-Item -LiteralPath (Join-Path $etapa $nombre) -Destination (Join-Path $local $nombre) -Force }
    New-Item -ItemType Directory -Path $publico -Force | Out-Null
    Copy-Item -LiteralPath $caPem -Destination $publicPem
    Write-Host 'Migracion TLS terminada. Credenciales, JWT y base de datos conservados.'
    Write-Host "Respaldo: $respaldo"
    Write-Host "Postman > CA certificates > cargar: $publicPem"
    Write-Host 'Reinicia Web con scripts\iniciar-bff.ps1 -Canal web. Mantener SSL certificate verification activado.'
} catch {
    $errorOriginal = $_
    if ($instalando) {
        foreach ($nombre in $existentes) {
            $copia = Join-Path $respaldo $nombre
            if (Test-Path $copia) { Copy-Item -LiteralPath $copia -Destination (Join-Path $local $nombre) -Force }
            else { Remove-Item -LiteralPath (Join-Path $local $nombre) -ErrorAction SilentlyContinue }
        }
        foreach ($nombre in $nuevos) { Remove-Item -LiteralPath (Join-Path $local $nombre) -ErrorAction SilentlyContinue }
        Remove-Item -LiteralPath $publicPem -ErrorAction SilentlyContinue
        Write-Host 'Se restauraron los certificados anteriores.'
    }
    throw $errorOriginal
} finally {
    $env:BFF_REPAIR_CA_PASS = $oldCaEnv
    $env:BFF_REPAIR_TLS_PASS = $oldTlsEnv
    Remove-Item -LiteralPath $etapa -Recurse -Force -ErrorAction SilentlyContinue
}
