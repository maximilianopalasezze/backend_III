param(
    [string]$DbUsuario='bank_batch_user',
    [string]$DbUrl='jdbc:mysql://localhost:3306/bank_xyz_semana5_db?useSSL=false&serverTimezone=America/Santiago&allowPublicKeyRetrieval=true'
)
$ErrorActionPreference='Stop'
$raiz=Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'preparar-semana6.ps1')
$local=Join-Path $raiz '.local'
$runtime=@{dbUser=$DbUsuario;dbUrl=$DbUrl;dbPassword=(Read-Host "Contrasena MySQL de $DbUsuario" -AsSecureString)}
$runtime | Export-Clixml (Join-Path $local 'runtime-semana6.clixml')
$helper=Join-Path $PSScriptRoot 'ejecutar-servicio-semana6.ps1'

# Orden: Config Server -> Eureka -> backends -> BFF. Las pausas dan tiempo al registro.
foreach($servicio in @('config','discovery','cuentas','movimientos','operaciones','web','movil','cajero')){
    Start-Process powershell -ArgumentList @('-NoExit','-ExecutionPolicy','Bypass','-File',"`"$helper`"",'-Servicio',$servicio)
    if($servicio -eq 'config'){Start-Sleep -Seconds 5}
    elseif($servicio -eq 'discovery'){Start-Sleep -Seconds 5}
    else{Start-Sleep -Seconds 2}
}
Write-Host 'Servicios iniciados en ventanas separadas.' -ForegroundColor Green
Write-Host 'Eureka: http://localhost:8761 | Config: http://localhost:8888/ms-cuentas/default'
Write-Host 'BFF: https://localhost:8081, :8082 y :8083'
