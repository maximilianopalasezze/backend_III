param(
    [Parameter(Mandatory=$true)][ValidateSet('web','movil','cajero')][string]$Canal,
    [string]$DbUsuario='bank_batch_user',
    [string]$DbUrl='jdbc:mysql://localhost:3306/bank_xyz_semana5_db?useSSL=false&serverTimezone=America/Santiago&allowPublicKeyRetrieval=true'
)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$local = Join-Path $raiz '.local'
$config = Import-Clixml (Join-Path $local 'config.clixml')
$item = $config[$Canal]
function Texto-Secreto($valor) { return [System.Net.NetworkCredential]::new('', $valor).Password }
$env:BFF_TLS_STORE = 'file:' + (Join-Path $local "$Canal.p12").Replace('\','/')
$env:BFF_TLS_PASSWORD = Texto-Secreto $item.tls
$env:BFF_JWT_SECRET = Texto-Secreto $item.jwt
$env:BFF_LOGIN_PASSWORD = Texto-Secreto $item.login
$env:BFF_READ_PASSWORD = Texto-Secreto $item.consulta
$env:DB_URL=$DbUrl
$env:DB_USER=$DbUsuario
$env:DB_PASSWORD = Texto-Secreto (Read-Host "Contrasena MySQL de $DbUsuario" -AsSecureString)
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
$jar = Join-Path $raiz "bff\bff-$Canal\target\bff-$Canal-0.0.1-SNAPSHOT.jar"
if (!(Test-Path $jar)) { throw 'Falta el JAR. Ejecuta primero el comando clean package del README.' }
try { & $java -jar $jar } finally {
    foreach ($nombre in @('BFF_TLS_PASSWORD','BFF_JWT_SECRET','BFF_LOGIN_PASSWORD','BFF_READ_PASSWORD','DB_PASSWORD')) {
        Remove-Item "Env:$nombre" -ErrorAction SilentlyContinue
    }
}
