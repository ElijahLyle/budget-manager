# Budget Manager API Specification

Version: 0.1.0 (MVP)
Machine-readable contract: `openapi.yaml` (this document is the human-readable source of truth; keep both in sync)

## 1. Purpose

Budget Manager helps people manage their personal finances. It records a user's money flow (income and expenses), lets them categorize each transaction to see where money goes, and tracks which account each transaction was charged to. A monthly summary shows total income, total expenses and the net balance.

## 2. Technical overview

| Item | Value |
|---|---|
| API style | REST over HTTPS |
| Base URL | `https://api.example.com/api/v1` |
| Data format | JSON (`Content-Type: application/json`) |
| Backend (proposed) | Java + Spring Boot |
| Database (proposed) | PostgreSQL |

## 3. Business process (MVP)

1. The user is identified by `userId` in the URL. There is no login endpoint in the MVP; authentication is added post-MVP (see section 9).
2. The user creates one or more accounts (cash, bank, ...).
3. The user records a transaction (income or expense) against an account.
4. The user categorizes the transaction.
5. The user requests a monthly summary.

## 4. Conventions

- **Naming:** plural nouns for collections (`/accounts`, `/categories`); JSON fields in `camelCase`.
- **One name per concept:** a field has the same name in requests, responses, the database and these docs.
- **Identifiers:** integer `id`, assigned by the server. Never sent in a create request.
- **Money:** decimal with at most 2 decimal places, always positive. The sign is carried by `type`. Use `BigDecimal` in Java and `NUMERIC(19,2)` in PostgreSQL, never `double`.
- **Dates and times:** ISO 8601 in the API. `transactionDate` is `YYYY-MM-DD`, `transactionTime` is `HH:mm:ss` (optional). All values are interpreted in the user's local time (MVP: `Asia/Manila`). Monthly totals use this same calendar.
- **Audit fields:** every resource has server-managed audit fields (section 6.6). They are read-only and are rejected or ignored in request bodies.
- **Security:** `POST`, `PUT`, `PATCH` and `DELETE` are unauthenticated in the MVP. Authentication is post-MVP v1.0.

## 5. Endpoints

| Method | Endpoint | Purpose | Success |
|---|---|---|---|
| POST | `/users` | Create a user | 201 |
| GET | `/users/{userId}` | Get a user | 200 |
| GET | `/users/{userId}/accounts` | List a user's accounts | 200 |
| POST | `/users/{userId}/accounts` | Create an account | 201 |
| GET | `/categories` | List categories | 200 |
| POST | `/categories` | Create a category | 201 |
| GET | `/users/{userId}/transactions` | List / filter transactions | 200 |
| POST | `/users/{userId}/transactions` | Create a transaction | 201 |
| GET | `/users/{userId}/transactions/{transactionId}` | Get one transaction | 200 |
| PUT | `/users/{userId}/transactions/{transactionId}` | Update a transaction | 200 |
| DELETE | `/users/{userId}/transactions/{transactionId}` | Delete a transaction | 204 |
| GET | `/users/{userId}/summaries/monthly` | Monthly summary | 200 |

Why `userId` is in the path: until authentication exists, the path is the only way to say whose data is meant. After auth, the user will come from the token and these paths can drop the `/users/{userId}` prefix in v2.

## 6. Data model and validation

### 6.1 User

| Field | Type | Required (create) | Rules |
|---|---|---|---|
| `id` | integer | no (read-only) | server-assigned |
| `userName` | string | yes | 3-50 chars, unique, `[A-Za-z0-9_.-]` |
| `password` | string | yes | 8-72 chars, **write-only**, stored hashed, never returned |
| `firstName` | string | yes | 1-100 chars |
| `lastName` | string | yes | 1-100 chars |
| `email` | string | yes | valid email, unique |
| `phone` | string | no | 7-20 chars, digits, `+`, spaces, `-` |
| `status` | string | no (read-only) | `ACTIVE` or `INACTIVE`; new users are `ACTIVE` |
| `currency` | string | yes | user local currency |

### 6.2 Account

| Field | Type | Required (create) | Rules |
|---|---|---|---|
| `id` | integer | no (read-only) | |
| `userId` | integer | no (read-only) | taken from the path |
| `name` | string | yes | 1-100 chars, unique per user |
| `type` | string | yes | `CASH`, `BANK`, `CREDIT_CARD`, `E_WALLET` |
| `currency` | string | yes | ISO 4217 code, 3 uppercase letters (e.g. `PHP`) |

### 6.3 Category

| Field | Type | Required (create) | Rules |
|---|---|---|---|
| `id` | integer | no (read-only) | |
| `name` | string | yes | 1-100 chars, unique per `type` |
| `type` | string | yes | `INCOME` or `EXPENSE` |
| `description` | string | no | up to 255 chars |

### 6.4 Transaction

| Field | Type | Required (create) | Rules |
|---|---|---|---|
| `id` | integer | no (read-only) | |
| `userId` | integer | no (read-only) | taken from the path |
| `accountId` | integer | yes | must exist and belong to the user |
| `categoryId` | integer | yes | must exist; category `type` must equal transaction `type` |
| `type` | string | yes | `INCOME` or `EXPENSE` |
| `amount` | number | yes | greater than 0, max 2 decimal places |
| `transactionDate` | string (date) | yes | `YYYY-MM-DD` |
| `transactionTime` | string | no | `HH:mm:ss` |
| `description` | string | no | up to 255 chars |
| `note` | string | no | up to 1000 chars |

### 6.5 Monthly summary (read-only)

| Field | Type | Meaning |
|---|---|---|
| `year` | integer | requested year |
| `month` | integer | requested month, 1-12 |
| `totalIncome` | number | sum of `INCOME` amounts in the month |
| `totalExpense` | number | sum of `EXPENSE` amounts in the month |
| `netBalance` | number | `totalIncome - totalExpense` (may be negative) |

### 6.6 Audit fields (all resources, read-only)

Your current design stores these as integers. Keep that in the database if you like; the API exposes them in a readable form.

| Database column | API field | Note |
|---|---|---|
| `createdAtDate` + `createdAtTime` | `createdAt` | ISO 8601 date-time |
| `changedAtDate` + `changedAtTime` | `updatedAt` | ISO 8601 date-time |
| `changeNo` | `changeNo` | increments on every update; can double as an optimistic-locking version |
| `timestamp` | (not exposed) | duplicates the date/time columns, so consider dropping it |

## 7. Request and response examples

### 7.1 Create a user

`POST /users`

```json
{
  "userName": "juan.delacruz",
  "password": "S3cure-passw0rd",
  "firstName": "Juan",
  "lastName": "Dela Cruz",
  "email": "juan@example.com",
  "phone": "+639171234567"
}
```

`201 Created`, header `Location: /api/v1/users/1`

```json
{
  "id": 1,
  "userName": "juan.delacruz",
  "firstName": "Juan",
  "lastName": "Dela Cruz",
  "email": "juan@example.com",
  "phone": "+639171234567",
  "status": "ACTIVE",
  "createdAt": "2026-10-25T21:43:00+08:00",
  "updatedAt": "2026-10-25T21:43:00+08:00",
  "changeNo": 0
}
```

### 7.2 Create an account

`POST /users/1/accounts`

```json
{ "name": "BDO Savings", "type": "BANK", "currency": "PHP" }
```

`201 Created`

```json
{
  "id": 10,
  "userId": 1,
  "name": "BDO Savings",
  "type": "BANK",
  "currency": "PHP",
  "createdAt": "2026-10-25T21:45:00+08:00",
  "updatedAt": "2026-10-25T21:45:00+08:00",
  "changeNo": 0
}
```

### 7.3 Create a category

`POST /categories`

```json
{ "name": "Groceries", "type": "EXPENSE", "description": "Food and household supplies" }
```

`201 Created`

```json
{
  "id": 3,
  "name": "Groceries",
  "type": "EXPENSE",
  "description": "Food and household supplies",
  "createdAt": "2026-10-25T21:46:00+08:00",
  "updatedAt": "2026-10-25T21:46:00+08:00",
  "changeNo": 0
}
```

### 7.4 Create a transaction

`POST /users/1/transactions`

```json
{
  "accountId": 10,
  "categoryId": 3,
  "type": "EXPENSE",
  "amount": 1250.75,
  "transactionDate": "2026-10-25",
  "transactionTime": "18:30:00",
  "description": "Weekly groceries",
  "note": "Paid with debit card"
}
```

`201 Created`, header `Location: /api/v1/users/1/transactions/100`

```json
{
  "id": 100,
  "userId": 1,
  "accountId": 10,
  "categoryId": 3,
  "type": "EXPENSE",
  "amount": 1250.75,
  "transactionDate": "2026-10-25",
  "transactionTime": "18:30:00",
  "description": "Weekly groceries",
  "note": "Paid with debit card",
  "createdAt": "2026-10-25T21:47:00+08:00",
  "updatedAt": "2026-10-25T21:47:00+08:00",
  "changeNo": 0
}
```

### 7.5 List and filter transactions

`GET /users/1/transactions?from=2026-10-01&to=2026-10-31&type=EXPENSE&accountId=10&page=0&size=20`

| Query parameter | Required | Meaning |
|---|---|---|
| `from`, `to` | no | inclusive date range (`YYYY-MM-DD`); `from` must not be after `to` |
| `accountId` | no | only this account |
| `categoryId` | no | only this category |
| `type` | no | `INCOME` or `EXPENSE` |
| `page` | no | zero-based page, default `0` |
| `size` | no | page size, 1-100, default `20` |

Results are sorted by `transactionDate` then `transactionTime`, newest first.

`200 OK`

```json
{
  "content": [
    {
      "id": 100,
      "userId": 1,
      "accountId": 10,
      "categoryId": 3,
      "type": "EXPENSE",
      "amount": 1250.75,
      "transactionDate": "2026-10-25",
      "transactionTime": "18:30:00",
      "description": "Weekly groceries",
      "note": "Paid with debit card",
      "createdAt": "2026-10-25T21:47:00+08:00",
      "updatedAt": "2026-10-25T21:47:00+08:00",
      "changeNo": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 7.6 Update a transaction

`PUT /users/1/transactions/100` replaces the whole transaction, so the body has the same shape and rules as the create request. Returns `200 OK` with the updated transaction (`changeNo` incremented).

### 7.7 Delete a transaction

`DELETE /users/1/transactions/100` returns `204 No Content` with an empty body.

### 7.8 Monthly summary

`GET /users/1/summaries/monthly?year=2026&month=10`

`200 OK`

```json
{
  "year": 2026,
  "month": 10,
  "totalIncome": 45000.00,
  "totalExpense": 18250.75,
  "netBalance": 26749.25
}
```

A month with no transactions returns zeros, not 404.

## 8. Errors

Every error uses the same body (implemented once, centrally, on Day 18):

```json
{
  "timestamp": "2026-10-25T13:47:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/users/1/transactions",
  "details": [
    { "field": "amount", "message": "must be greater than 0" }
  ]
}
```

`details` is present for validation errors and may be omitted otherwise.

| Status | When |
|---|---|
| 400 Bad Request | invalid input: missing required field, wrong type, bad enum value, amount <= 0, malformed date, category type does not match transaction type |
| 404 Not Found | the user, account, category or transaction does not exist (or does not belong to this user) |
| 409 Conflict | duplicate `userName`, `email` or account name; or deleting something that is still referenced |
| 500 Internal Server Error | unexpected failure; no internal details in the body |
| 401 / 403 | **post-MVP**, once authentication is added |

Note: a nonexistent `accountId` or `categoryId` inside a request body is reported as **400** (the request is invalid), while a nonexistent resource in the URL path is **404**.

## 9. Business rules

1. Amounts are always positive. `type` decides whether the amount adds to income or expense.
2. A transaction's category `type` must match the transaction's `type`.
3. A transaction can only use an account that belongs to the same user.
4. An account or category that still has transactions cannot be deleted (409). This also means account and category deletion are not in the MVP endpoint list.
5. `userId`, `id` and audit fields can't be changed through the API.
6. Security (authentication and authorization) for all write methods comes after MVP v1.0. Passwords are hashed from day one, never returned.

## 10. Open questions to decide before coding

- **Single currency?** A monthly summary adds amounts together. If accounts can have different currencies the totals are meaningless. MVP suggestion: one currency per user, or reject mixed currencies in a summary.
  + Currencies are converted into the user's local currency
- **Seed categories:** should the app ship with default categories (Food, Transport, Salary, ...)? `POST /categories` is global right now, so every user shares one list.
  + Yes, we will have the basic categories 
- **Transfers between accounts:** out of scope for the MVP; mention it in the README as a future feature.