# Payment Operations

Demo backend service for processing payment operations.

The service accepts payment requests, validates data, creates payments in PSQL, sends them to a demo provider processor, updates statuses and shows operations in a read-only admin panel.

## Описание

`Payment Operations` - демонстрационный backend-сервис для проведения платежных операций.

Сервис принимает платежный запрос через API, проверяет данные, создает платеж в PSQL, передает его в демонстрационный обработчик провайдера, обновляет статус платежа и показывает результат в админ-панели.

## Main Files

| File | Description |
|---|---|
| `src/main/kotlin/org/eltech/Main.kt` | Точка входа приложения |
| `src/main/kotlin/org/eltech/app/PaymentApplicationVerticle.kt` | Запускает Vert.x HTTP server |
| `src/main/kotlin/org/eltech/adapter/in/http/PaymentRoutes.kt` | REST API для платежей |
| `src/main/kotlin/org/eltech/adapter/in/http/AuthHandler.kt` | Проверка demo Bearer token |
| `src/main/kotlin/org/eltech/application/usecase/PaymentService.kt` | Основная логика платежей |
| `src/main/kotlin/org/eltech/adapter/out/provider/DemoProviderProcessor.kt` | Демонстрационный процессинг провайдера |
| `src/main/kotlin/org/eltech/adapter/out/persistence/PostgresPaymentRepository.kt` | Работа с PostgreSQL |
| `src/main/java/org/eltech/infrastructure/security/PaymentRequestFingerprint.java` | Создает hash запроса для idempotency |
| `src/main/java/org/eltech/infrastructure/validation/PaymentIds.java` | Проверяет и парсит UUID платежа |
| `src/main/java/org/eltech/infrastructure/validation/NativePaymentValidator.java` | Java wrapper для C validation module |
| `src/main/java/org/eltech/infrastructure/routing/NativePaymentRouter.java` | Java wrapper для C++ routing module |
| `src/main/resources/db/schema.sql` | Схема базы данных |
| `src/main/resources/webroot/admin/` | Read-only admin panel |
| `src/main/c/payment_validation.c` | C-модуль валидации |
| `src/main/cpp/payment_routing.cpp` | C++-модуль маршрутизации |
| `docker-compose.yml` | Локальный PostgreSQL |
| `Procfile` | Команда запуска для Heroku |
| `Aptfile` | Пакеты для native build на Heroku |

## Payment Status Flow

```text
CREATED -> CHECK_REQUISITE -> CONFIRMED -> PROCESSING -> SUCCESS
```

Possible final statuses:

```text
SUCCESS
FAILED
CANCELLED
```

## How To Run Locally

Go to project folder

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Build service:

```bash
./gradlew stage
```

Run:

```bash
build/install/PaymentOperations/bin/PaymentOperations
```

Service URL:

```text
http://localhost:8080
```

Admin panel:

```text
http://localhost:8080/admin/
```

Health check:

```bash
curl http://localhost:8080/health
```

## Запуск Локально

Перейти в папку проекта

Запустить PostgreSQL:

```bash
docker compose up -d postgres
```

Собрать сервис:

```bash
./gradlew stage
```

Запустить:

```bash
build/install/PaymentOperations/bin/PaymentOperations
```

Админ-панель:

```text
http://localhost:8080/admin/
```

## Database

Schema file:

```text
src/main/resources/db/schema.sql
```

If database schema changed, recreate local database:

```bash
docker compose down -v
docker compose up -d postgres
```

## Run With C/C++

Build native modules:

```bash
./gradlew nativeStage
```

Run service with native modules on macOS:

```bash
PAYMENT_NATIVE_VALIDATOR_PATH=build/native/libpayment_validation.dylib \
PAYMENT_NATIVE_ROUTER_PATH=build/native/libpayment_routing.dylib \
build/install/PaymentOperations/bin/PaymentOperations
```

On Linux, native files use `.so` instead of `.dylib`.

If C/C++ modules are not found, the service uses JVM fallback.

## API Examples

### Health

```bash
curl http://localhost:8080/health
```

### Create Payment

```bash
curl -X POST http://localhost:8080/payments \
  -H "Authorization: Bearer demo-token" \
  -H "Idempotency-Key: demo-1" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "client-a",
    "providerId": "demo-provider",
    "amount": "150.00",
    "currency": "KGS",
    "requisite": "BANKB-300-400"
  }'
```

### List Payments

```bash
curl http://localhost:8080/payments \
  -H "Authorization: Bearer demo-token"
```

### Get Payment

```bash
curl http://localhost:8080/payments/{paymentId} \
  -H "Authorization: Bearer demo-token"
```

### Cancel Payment

```bash
curl -X POST http://localhost:8080/payments/{paymentId}/cancel \
  -H "Authorization: Bearer demo-token"
```

## Demo Requisites

| Requisite | Result |
|---|---|
| `BANKB-300-400` | Successful payment |
| `BAD-300-400` | Invalid requisite, final status `FAILED` |
| `TIMEOUT-300-400` | Provider timeout with retries, final status `FAILED` |

## What Works

| Function |        Status | How to Show |
|---|--------------:|---|
| Backend startup |          Done | Run `build/install/PaymentOperations/bin/PaymentOperations` |
| Health check |          Done | Open `http://localhost:8080/health` |
| Admin panel |          Done | Open `http://localhost:8080/admin/` |
| PostgreSQL connection |          Done | Health check returns `UP` |
| Payment creation |          Done | Send `POST /payments` |
| Bearer token check |          Done | Request without token returns `401` |
| Idempotency key |          Done | Same request key returns same payment |
| Payment validation |          Done | Invalid amount or empty fields return error |
| Provider processing |          Done | Payment status changes after creation |
| Successful payment |          Done | Use requisite `BANKB-300-400` |
| Invalid requisite |          Done | Use requisite `BAD-300-400` |
| Provider timeout retry |          Done | Use requisite `TIMEOUT-300-400` |
| Status history |          Done | Open payment details in admin panel |
| Provider response saving |          Done | Saved in `provider_responses` |
| Audit log |          Done | Saved by database triggers |
| Payment cancellation |          Done | Call `POST /payments/{paymentId}/cancel` |
| Docker database |          Done | Run `docker compose up -d postgres` |
| C validation module |          Done | `/health` shows `native-c` when native path is set |
| C++ routing module |          Done | `/health` shows `native-cpp` when native path is set |
| JVM fallback |          Done | Run without native paths, service still works |
| Heroku deployment | Not Ready yet | Project has `Procfile`, `Aptfile`, `stage`, `nativeStage` |

## Что Работает

| Функция |                                     Статус | Как показать |
|---|-------------------------------------------:|---|
| Запуск backend |                                     Готово | Запустить `build/install/PaymentOperations/bin/PaymentOperations` |
| Health check |                                     Готово | Открыть `http://localhost:8080/health` |
| Админ-панель |                                     Готово | Открыть `http://localhost:8080/admin/` |
| Подключение к PostgreSQL |                                     Готово | `/health` возвращает `UP` |
| Создание платежа |                                     Готово | Отправить `POST /payments` |
| Проверка Bearer token |                                     Готово | Запрос без token возвращает `401` |
| Idempotency key |                                     Готово | Повторный ключ возвращает тот же платеж |
| Валидация платежа |                                     Готово | Некорректные данные возвращают ошибку |
| Обработка провайдером |                                     Готово | Статус платежа меняется после создания |
| Успешный платеж |                                     Готово | Использовать `BANKB-300-400` |
| Неверный реквизит |                                     Готово | Использовать `BAD-300-400` |
| Retry при timeout |                                     Готово | Использовать `TIMEOUT-300-400` |
| История статусов |                                     Готово | Открыть детали платежа в админке |
| Ответ провайдера |                                     Готово | Сохраняется в `provider_responses` |
| Audit log |                                     Готово | Сохраняется триггерами БД |
| Отмена платежа |                                     Готово | Вызвать `POST /payments/{paymentId}/cancel` |
| Docker database |                                     Готово | `docker compose up -d postgres` |
| C validation module |                                     Готово | `/health` показывает `native-c` |
| C++ routing module |                                     Готово | `/health` показывает `native-cpp` |
| JVM fallback |                                     Готово | Работает без native paths |
| Heroku deployment | Пока не готово к настройке, но тестируется | Есть `Procfile`, `Aptfile`, `stage`, `nativeStage` |


## Not Production Ready Yet

| Item | Status | Why |
|---|---:|---|
| Real JWT auth | Not done | Uses `Bearer demo-token` |
| Real provider API | Not done | Uses demo provider processor |
| Account balances | Not done | No real balance table |
| Ledger entries | Not done | No accounting ledger |
| Secure secrets | Not done | Demo configuration |
| Automated tests | Not done | No full test suite |
| Real reports | Partial | Admin panel shows data, but no export |

## Что Не Готово Для Production

| Часть | Статус | Причина |
|---|---:|---|
| Настоящий JWT | Не готово | Используется `Bearer demo-token` |
| Реальный API провайдера | Не готово | Используется demo processor |
| Балансы счетов | Не готово | Нет таблицы балансов |
| Бухгалтерские проводки | Не готово | Нет полноценного ledger |
| Хранение секретов | Не готово | Demo configuration |
| Автоматические тесты | Не готово | Нет полного набора тестов |
| Отчеты | Частично | Есть просмотр в админке, но нет export |

## Notes

This is a demo project, not a production banking system.

Это демонстрационный проект, а не production банковская система.
