# replace-urls.ps1 - Автоматическая замена хардкодных URL на конфиг
Write-Host "🔍 Поиск и замена хардкодных URL..." -ForegroundColor Cyan

# Создаем бэкап всех файлов
$backupDir = "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
Write-Host "📦 Создание бэкапа в папку: $backupDir" -ForegroundColor Yellow
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

# Получаем все JS/JSX/TS/TSX файлы
$files = Get-ChildItem -Path . -Recurse -Include *.js, *.jsx, *.ts, *.tsx -Exclude node_modules, *.min.js

$totalFiles = $files.Count
$modifiedCount = 0

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    
    $originalContent = $content
    
    # Заменяем разные варианты URL
    $content = $content -replace '["'']http://localhost:8080/api', '`${API_BASE_URL}'
    $content = $content -replace '["'']http://localhost:8080', '`${API_BASE_URL}'
    $content = $content -replace '["'']https://localhost:8080/api', '`${API_BASE_URL}'
    $content = $content -replace '["'']https://localhost:8080', '`${API_BASE_URL}'
    
    # Заменяем в строковых литералах (более безопасно)
    $content = $content -replace '(?<=`"|'')http://localhost:8080/api(?=`"|'')', '${API_BASE_URL}'
    $content = $content -replace '(?<=`"|'')http://localhost:8080(?=`"|'')', '${API_BASE_URL}'
    
    if ($content -ne $originalContent) {
        # Создаем бэкап файла
        $relativePath = $file.FullName.Substring((Get-Location).Path.Length + 1)
        $backupPath = Join-Path $backupDir $relativePath
        $backupFolder = Split-Path $backupPath -Parent
        if (-not (Test-Path $backupFolder)) {
            New-Item -ItemType Directory -Path $backupFolder -Force | Out-Null
        }
        Copy-Item $file.FullName $backupPath
        
        # Обновляем файл
        Set-Content -Path $file.FullName -Value $content -NoNewline
        Write-Host "  ✅ Обновлен: $($file.Name)" -ForegroundColor Green
        $modifiedCount++
    }
}

Write-Host ""
Write-Host "📊 Статистика:" -ForegroundColor Cyan
Write-Host "  - Всего файлов: $totalFiles"
Write-Host "  - Изменено файлов: $modifiedCount"
Write-Host "  - Бэкап сохранен в: $backupDir" -ForegroundColor Yellow

Write-Host ""
Write-Host "⚠️  ВАЖНО: После замены нужно:" -ForegroundColor Yellow
Write-Host "  1. Добавить импорт API_BASE_URL в каждый измененный файл:"
Write-Host "     import { API_BASE_URL } from '../config/api';"
Write-Host "  2. Проверить, что файл config/api.js существует"
Write-Host "  3. Пересобрать проект: npm run build"
Write-Host ""
Write-Host "Нажми Enter для продолжения или Ctrl+C для отмены..."
pause

Write-Host ""
Write-Host "✅ Готово!" -ForegroundColor Green