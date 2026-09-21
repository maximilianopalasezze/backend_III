$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$local = Join-Path $raiz '.local'
$base = Join-Path $local 'config.clixml'
if (!(Test-Path $base)) {
    & (Join-Path $PSScriptRoot 'preparar-local.ps1')
}
if (!(Test-Path $base)) { throw 'No fue posible generar la configuracion local de los BFF.' }

function Nueva-Clave {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
function Texto-Secreto($valor) { return [System.Net.NetworkCredential]::new('', $valor).Password }

$s6 = Join-Path $local 'semana6.clixml'
if (!(Test-Path $s6)) {
    $cfg6 = @{
        serviceUser = 'svc-bff'
        servicePassword = (ConvertTo-SecureString (Nueva-Clave) -AsPlainText -Force)
        viewerUser = 'viewer'
        viewerPassword = (ConvertTo-SecureString (Nueva-Clave) -AsPlainText -Force)
    }
    $cfg6 | Export-Clixml $s6
} else {
    $cfg6 = Import-Clixml $s6
}

$config = Import-Clixml $base
$variables = @(
    @{key='backendServiceUser';value=$cfg6.serviceUser;type='default';enabled=$true},
    @{key='backendServicePassword';value=(Texto-Secreto $cfg6.servicePassword);type='secret';enabled=$true},
    @{key='backendViewerUser';value=$cfg6.viewerUser;type='default';enabled=$true},
    @{key='backendViewerPassword';value=(Texto-Secreto $cfg6.viewerPassword);type='secret';enabled=$true}
)
foreach ($canal in @('web','movil','cajero')) {
    $item = $config[$canal]
    $variables += @{key="${canal}Password";value=(Texto-Secreto $item.login);type='secret';enabled=$true}
    $variables += @{key="${canal}ReadPassword";value=(Texto-Secreto $item.consulta);type='secret';enabled=$true}
}
$entorno = @{name='Banco XYZ Semana 6 Cloud Local';values=$variables;_postman_variable_scope='environment'}
$entorno | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $local 'Semana6_Cloud_Local.postman_environment.json')
Write-Host 'Semana 6 preparada.' -ForegroundColor Green
Write-Host 'Credenciales de servicios y entorno Postman creados en .local (excluido de Git).'
