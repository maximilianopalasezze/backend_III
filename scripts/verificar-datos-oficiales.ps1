$ErrorActionPreference = "Stop"

function Get-Sha256Normalizado {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Ruta
    )

    $rutaAbsoluta = (Resolve-Path -LiteralPath $Ruta).Path
    $contenido = [System.IO.File]::ReadAllText($rutaAbsoluta)
    $contenidoNormalizado = $contenido.Replace("`r`n", "`n").Replace("`r", "`n")
    $utf8SinBom = New-Object System.Text.UTF8Encoding($false)
    $bytes = $utf8SinBom.GetBytes($contenidoNormalizado)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()

    try {
        return -join ($sha256.ComputeHash($bytes) | ForEach-Object {
            $_.ToString("X2")
        })
    }
    finally {
        $sha256.Dispose()
    }
}

$archivos = @(
    @{
        Ruta = "src/main/resources/data/semana_3/cuentas_anuales.csv"
        Registros = 1000
        Sha256 = "0690D81FE446AFFE39857A448FF5590C4F6E6F998150C08934DB3AA41683E328"
    },
    @{
        Ruta = "src/main/resources/data/semana_3/intereses.csv"
        Registros = 1000
        Sha256 = "69CC39F468DB47E4D752657DB95EE9CAF00EBCAF31581E45E104C4DC4A5E3120"
    },
    @{
        Ruta = "src/main/resources/data/semana_3/transacciones.csv"
        Registros = 1000
        Sha256 = "9A209C71D5556380481731F0FE9AC148A586A7C91698B377481B62E96341E836"
    }
)

$resultado = foreach ($archivo in $archivos) {
    if (-not (Test-Path $archivo.Ruta)) {
        throw "No se encontro el archivo oficial: $($archivo.Ruta)"
    }

    # Git puede usar CRLF en Windows y LF en Linux. Para comparar el
    # contenido real del CSV, el hash se calcula siempre con saltos LF.
    $hash = Get-Sha256Normalizado -Ruta $archivo.Ruta
    $registros = (Import-Csv $archivo.Ruta).Count

    [PSCustomObject]@{
        Archivo = [System.IO.Path]::GetFileName($archivo.Ruta)
        Ruta = $archivo.Ruta
        Registros = $registros
        RegistrosEsperados = $archivo.Registros
        Sha256Normalizado = $hash
        EsOficial = ($registros -eq $archivo.Registros -and
            $hash -eq $archivo.Sha256)
    }
}

$resultado |
    Select-Object Archivo, Registros, RegistrosEsperados, EsOficial |
    Format-Table -AutoSize

foreach ($archivo in $resultado) {
    Write-Host "Ruta oficial: $($archivo.Ruta)"
    Write-Host "SHA256 normalizado: $($archivo.Sha256Normalizado)"
}

if ($resultado.EsOficial -contains $false) {
    throw "La verificacion fallo: existe al menos un archivo diferente del oficial."
}

Write-Host "VERIFICACION CORRECTA: los tres archivos coinciden con los datos oficiales." -ForegroundColor Green
