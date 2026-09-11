$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$destino = Join-Path $raiz '.local'
if (Test-Path (Join-Path $destino 'config.clixml')) {
    Write-Host 'La configuracion local ya existe. Se conservan certificados y credenciales.'
    exit 0
}
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' } else { (Get-Command keytool -ErrorAction Stop).Source }
if (!(Test-Path $keytool)) { throw 'No se encuentra keytool. Revisa JAVA_HOME.' }
New-Item -ItemType Directory -Force -Path $destino | Out-Null
function Nueva-Clave {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
$config = @{}
$variables = @()
$pem = ''
foreach ($canal in @('web','movil','cajero')) {
    $tls = Nueva-Clave
    $jwt = Nueva-Clave
    $login = Nueva-Clave
    $consulta = Nueva-Clave
    $almacen = Join-Path $destino "$canal.p12"
    if (Test-Path $almacen) { throw "Existe $almacen sin configuracion completa. Conserva esa carpeta y extrae una copia limpia del paquete para preparar de nuevo." }
    $env:BFF_KEYTOOL_PASSWORD = $tls
    try {
        & $keytool -genkeypair -noprompt -alias bff-tls -keyalg RSA -keysize 3072 -validity 365 -storetype PKCS12 -keystore $almacen -storepass:env BFF_KEYTOOL_PASSWORD -keypass:env BFF_KEYTOOL_PASSWORD -dname "CN=localhost,OU=BFF $canal,O=Banco XYZ,C=CL" -ext 'SAN=dns:localhost,ip:127.0.0.1' -ext 'BC=ca:false' -ext 'EKU=serverAuth' -ext 'KU=digitalSignature,keyEncipherment'
        if ($LASTEXITCODE -ne 0) { throw "No se pudo generar el certificado $canal" }
        & $keytool -exportcert -rfc -alias bff-tls -keystore $almacen -storepass:env BFF_KEYTOOL_PASSWORD -file (Join-Path $destino "$canal.pem")
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo exportar el certificado' }
    } finally { Remove-Item Env:BFF_KEYTOOL_PASSWORD -ErrorAction SilentlyContinue }
    $config[$canal] = @{
        tls = (ConvertTo-SecureString $tls -AsPlainText -Force)
        jwt = (ConvertTo-SecureString $jwt -AsPlainText -Force)
        login = (ConvertTo-SecureString $login -AsPlainText -Force)
        consulta = (ConvertTo-SecureString $consulta -AsPlainText -Force)
    }
    $variables += @{key="${canal}Password";value=$login;type='secret';enabled=$true}
    $variables += @{key="${canal}ReadPassword";value=$consulta;type='secret';enabled=$true}
    $pem += [IO.File]::ReadAllText((Join-Path $destino "$canal.pem")) + "`n"
}
$config | Export-Clixml (Join-Path $destino 'config.clixml')
[IO.File]::WriteAllText((Join-Path $destino 'certificados-locales.pem'), $pem)
$entorno = @{name='Banco XYZ Semana 5 Local';values=$variables;_postman_variable_scope='environment'}
$entorno | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 (Join-Path $destino 'Semana5_Local.postman_environment.json')
Write-Host 'Preparacion terminada. Importa el entorno Postman desde .local y agrega certificados-locales.pem como certificado CA en Postman.'
Write-Host 'La carpeta .local contiene claves privadas y credenciales locales. Queda excluida de Git y de la entrega.'
