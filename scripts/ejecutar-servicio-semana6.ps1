param(
    [Parameter(Mandatory=$true)][ValidateSet('config','discovery','cuentas','movimientos','operaciones','web','movil','cajero')][string]$Servicio
)
$ErrorActionPreference='Stop'
$raiz=Split-Path $PSScriptRoot -Parent
$local=Join-Path $raiz '.local'
$runtime=Import-Clixml (Join-Path $local 'runtime-semana6.clixml')
$cloud=Import-Clixml (Join-Path $local 'semana6.clixml')
$base=Import-Clixml (Join-Path $local 'config.clixml')
function Texto-Secreto($valor){return [System.Net.NetworkCredential]::new('', $valor).Password}
$env:BACKEND_BASIC_USER=$cloud.serviceUser
$env:BACKEND_BASIC_PASSWORD=Texto-Secreto $cloud.servicePassword
$env:BACKEND_VIEWER_USER=$cloud.viewerUser
$env:BACKEND_VIEWER_PASSWORD=Texto-Secreto $cloud.viewerPassword
$env:DB_URL=$runtime.dbUrl
$env:DB_USER=$runtime.dbUser
$env:DB_PASSWORD=Texto-Secreto $runtime.dbPassword
$env:CONFIG_REPO='file:///' + (Join-Path $raiz 'bff\config-repo').Replace('\','/')
if($Servicio -in @('web','movil','cajero')){
    $item=$base[$Servicio]
    $env:BFF_TLS_STORE='file:' + (Join-Path $local "$Servicio.p12").Replace('\','/')
    $env:BFF_TLS_PASSWORD=Texto-Secreto $item.tls
    $env:BFF_JWT_SECRET=Texto-Secreto $item.jwt
    $env:BFF_LOGIN_PASSWORD=Texto-Secreto $item.login
    $env:BFF_READ_PASSWORD=Texto-Secreto $item.consulta
}
$map=@{
    config='config-server'; discovery='discovery-server'; cuentas='ms-cuentas'; movimientos='ms-movimientos'; operaciones='ms-operaciones';
    web='bff-web'; movil='bff-movil'; cajero='bff-cajero'
}
$modulo=$map[$Servicio]
$jar=Join-Path $raiz "bff\$modulo\target\$modulo-0.0.1-SNAPSHOT.jar"
if(!(Test-Path $jar)){throw "Falta $jar. Ejecuta .\bff\mvnw.cmd -f .\bff\pom.xml clean package"}
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin\java.exe'}else{(Get-Command java -ErrorAction Stop).Source}
Write-Host "== Banco XYZ Semana 6 | $Servicio ==" -ForegroundColor Cyan
& $java -jar $jar
