@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo     ТЕСТИРОВАНИЕ AUTH SERVICE
echo ========================================
echo.

set "GATEWAY_URL=http://localhost:8080"
set "AUTH_URL=http://localhost:8097"

echo 1️⃣  Проверка health endpoints
echo --------------------------------

:: Прямой health
curl -s %AUTH_URL%/api/auth/health
echo.
echo.

:: Health через gateway
curl -s %GATEWAY_URL%/api/auth/health
echo.
echo.

:: Общий health gateway
curl -s %GATEWAY_URL%/api/health
echo.
echo.

echo 2️⃣  Тестирование регистрации
echo --------------------------------
set "TIMESTAMP=%time::=%"
set "TIMESTAMP=%TIMESTAMP:,=%"
set "TIMESTAMP=%TIMESTAMP: =%"
set "TEST_USER=testuser_%TIMESTAMP%"

echo Создаем тестового пользователя: %TEST_USER%
echo.

curl -s -X POST %GATEWAY_URL%/api/clients/register ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"%TEST_USER%\",\"password\":\"password123\",\"email\":\"%TEST_USER%@test.com\",\"firstname\":\"Test\"}" > register.txt

type register.txt
echo.
echo.

:: Извлекаем ID пользователя из ответа регистрации
set "USER_ID="
for /f "tokens=*" %%a in ('type register.txt ^| find "id"') do (
    set "LINE=%%a"
)

:: Парсим ID
set "LINE=!LINE:*id=!"
for /f "tokens=1 delims=,}" %%b in ("!LINE!") do set "USER_ID=%%b"
set "USER_ID=!USER_ID::=!"
set "USER_ID=!USER_ID:"=!"
set "USER_ID=!USER_ID: =!"

if defined USER_ID (
    echo ✅ ID созданного пользователя: !USER_ID!
) else (
    echo ⚠️ Не удалось извлечь ID пользователя
)
echo.

echo 3️⃣  Тестирование логина
echo --------------------------------

:: Выполняем логин
curl -s -X POST %GATEWAY_URL%/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"%TEST_USER%\",\"password\":\"password123\"}" > login.txt

:: Показываем ответ
type login.txt
echo.
echo.

:: Парсим токен
set "TOKEN="
for /f "tokens=*" %%a in ('type login.txt ^| find "auth-"') do (
    set "LINE=%%a"
)

:: Вырезаем от auth- до следующей кавычки
set "TOKEN=!LINE:*auth-=auth-!"
for /f "tokens=1 delims=," %%b in ("!TOKEN!") do set "TOKEN=%%b"
set "TOKEN=!TOKEN:"=!"
set "TOKEN=!TOKEN:}=!"

echo Токен: !TOKEN!
echo.

echo 4️⃣  Тестирование валидации токена
echo --------------------------------

curl -s -X POST "%GATEWAY_URL%/api/auth/validate?clientToken=!TOKEN!"
echo.
echo.

echo 5️⃣  Тестирование получения информации о пользователе
echo --------------------------------

curl -s -X GET "%GATEWAY_URL%/api/auth/me?clientToken=!TOKEN!"
echo.
echo.

echo 6️⃣  Тестирование logout
echo --------------------------------

curl -s -X POST "%GATEWAY_URL%/api/auth/logout?clientToken=!TOKEN!"
echo.
echo.

echo 7️⃣  Проверка после logout (должен быть невалидным)
echo --------------------------------

curl -s -X POST "%GATEWAY_URL%/api/auth/validate?clientToken=!TOKEN!"
echo.
echo.

:: ✅ НОВЫЙ ШАГ: Удаление тестового пользователя
if defined USER_ID (
    echo 8️⃣  Удаление тестового пользователя
    echo --------------------------------
    
    echo Удаляем пользователя с ID: !USER_ID!
    echo.
    
    curl -s -X DELETE "%GATEWAY_URL%/api/admin/clients/!USER_ID!"
    echo.
    echo.
    
    echo ✅ Пользователь удален
    echo.
) else (
    echo 8️⃣  Пропускаем удаление (ID не найден)
    echo --------------------------------
    echo.
)

:: Удаляем временные файлы
del login.txt 2>nul
del register.txt 2>nul

echo ========================================
echo ✅ ТЕСТИРОВАНИЕ ЗАВЕРШЕНО
echo ========================================
pause