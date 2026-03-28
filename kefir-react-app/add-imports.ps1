# add-imports.ps1
Write-Host "📝 Добавление импортов API_BASE_URL..." -ForegroundColor Cyan

$files = @(
    "src\pages\client\ClientCart.jsx",
    "src\pages\client\ClientPortal.jsx",
    "src\pages\client\PaymentModal.jsx",
    "src\pages\client\SupportPage.jsx",
    "src\pages\collector\CollectorApp.jsx",
    "src\pages\office\OfficeDeliveries.jsx",
    "src\pages\office\OfficeOrders.jsx",
    "src\pages\office\OfficePage.jsx",
    "src\pages\office\OfficeProblemOrders.jsx",
    "src\pages\auth\Register.jsx",
    "src\components\ApiSwitcher.js",
    "src\components\DevTools.js",
    "src\components\office\PollingManager.jsx",
    "src\config\config.js",
    "src\services\api.js"
)

$modifiedCount = 0

foreach ($file in $files) {
    if (Test-Path $file) {
        $content = Get-Content $file -Raw
        # Проверяем, есть ли уже импорт
        if ($content -match '\$\{API_BASE_URL\}' -and $content -notmatch 'import.*API_BASE_URL.*from') {
            # Определяем правильный путь для импорта
            $depth = ($file -split '\\').Count - 2
            $importPath = if ($depth -eq 1) { "'./config/api'" } 
            elseif ($depth -eq 2) { "'../config/api'" }
            elseif ($depth -ge 3) { "'" + ("../" * ($depth - 1)) + "config/api'" }
            else { "'./config/api'" }
            
            $importStatement = "import { API_BASE_URL } from $importPath;`n"
            $newContent = $importStatement + $content
            Set-Content -Path $file -Value $newContent -NoNewline
            Write-Host "  ✅ Добавлен импорт в: $file" -ForegroundColor Green
            $modifiedCount++
        }
        elseif ($content -match 'localhost:8080') {
            Write-Host "  ⚠️  В файле $file остались старые URL!" -ForegroundColor Yellow
        }
    }
    else {
        Write-Host "  ⚠️  Файл не найден: $file" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "✅ Добавлено импортов в $modifiedCount файлов" -ForegroundColor Green