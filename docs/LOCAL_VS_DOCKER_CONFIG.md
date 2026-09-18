# Local vs Docker Compose Configuration Guide

This document explains every configuration change made to run the application locally,
why each change was needed, how it relates to the Docker Compose setup, and how to
safely switch between the two modes.

---

## Overview

The application supports two distinct running modes:

| Mode | Frontend | Backend | Database | API Routing |
|---|---|---|---|---|
| **Local** | `ng serve` on port 4200 | Spring Boot on port 8080 (IDE or `mvnw`) | PostgreSQL on `localhost:5432` | Angular dev-server proxy (`proxy.conf.json`) |
| **Docker Compose** | Nginx container on port 80 | Spring Boot container on port 8080 | PostgreSQL on `host.docker.internal:5432` | Nginx reverse proxy (`nginx.conf`) |

Both modes share the **same relative API path** (`/api`) in the Angular source code — no source change is needed when switching modes.

---

## Changes Made for Local Running

### 1. `Cashier-web-back/src/main/resources/application.properties`

#### What changed

The original Docker-ready property block (which uses environment-variable placeholders) was **commented out** and replaced with a hardcoded local configuration block:

```properties
# ── ORIGINAL (Docker / env-var based) ─────────────────────── COMMENTED OUT ──
#spring.application.name=cashier-backend-application
#app.printer.name=${APP_PRINTER_NAME:POS-80}
#spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:barakaDb}
#spring.datasource.username=${DB_USERNAME:postgres}
#spring.datasource.password=${DB_PASSWORD:9268}
#spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:update}
#jwt.expiration=${JWT_EXPIRATION:28800000}
#invitation.quiz.session-ttl-minutes=${INVITATION_QUIZ_SESSION_TTL_MINUTES:10}
#logging.level.org.springframework.security=${LOG_LEVEL_SECURITY:INFO}
#logging.level.com.mgh.backend.auth.security=${LOG_LEVEL_AUTH:INFO}

# ── CURRENT (Local / hardcoded) ─────────────────────────────────── ACTIVE ──
server.port=8080
spring.application.name=cashier-backend-application
app.printer.name=POS-80

spring.datasource.url=jdbc:postgresql://localhost:5432/barakaDb
spring.datasource.username=postgres
spring.datasource.password=9268
spring.jpa.hibernate.ddl-auto=update

jwt.expiration=28800000
invitation.quiz.session-ttl-minutes=10

# Security debugging (temporary; remove or lower in production)
logging.level.org.springframework.security=DEBUG
logging.level.com.mgh.backend.auth.security=DEBUG
```

#### Why this was needed

| Concern | Explanation |
|---|---|
| **Database host** | Docker uses `${DB_HOST}` (resolves to `host.docker.internal` inside a container). Locally, the database runs on `localhost`, so the placeholder must be replaced or its default must be correct. |
| **Database password** | The local DB password (`9268`) differs from the Docker default in the `.env.example` (`your_db_password_here`). Hardcoding removes dependence on env vars not set in a plain IDE run. |
| **No env injection at IDE launch** | When starting Spring Boot from VS Code or IntelliJ without a `.env` loader, environment variables are **not** automatically injected. Hardcoded values guarantee the app starts without extra configuration. |
| **Security log level** | Changed to `DEBUG` temporarily to help diagnose JWT/CORS issues during local development. |

#### Impact on Docker Compose

> [!WARNING]
> **This is the most critical issue in the current setup.**
>
> The Docker Compose `backend` service injects environment variables (e.g., `DB_HOST`, `DB_PASSWORD`)
> via `docker-compose.yml`. Because Spring Boot's `application.properties` now contains **hardcoded
> values with no `${...}` placeholders**, those injected variables are **silently ignored**.
>
> **Current Docker behaviour:**
> - `DB_HOST` → ignored — backend tries to connect to `localhost:5432` inside the container → **connection fails**
> - `DB_PASSWORD` → ignored — backend uses `9268` regardless of `.env`
> - `LOG_LEVEL_SECURITY` / `LOG_LEVEL_AUTH` → ignored — always `DEBUG`

See [Section 4 (Recommended Fix)](#4-recommended-fix-dual-mode-properties) for the clean solution.

---

### 2. `Cashier-web-front/src/environments/environment.ts` — `apiUrl`

#### What changed

`apiUrl` was set (or kept) as a **relative path**:

```typescript
apiUrl: '/api',   // relative — works for BOTH local and Docker
```

#### Why this is correct

- **Local mode**: `ng serve` reads `proxy.conf.json` and forwards any request whose path starts with `/api` to `http://localhost:8080`. The Angular source never sees an absolute URL.
- **Docker mode**: Nginx forwards `/api` → `http://backend:8080` (Docker internal hostname). Same relative path, different forwarder.

A relative path is the **single source of truth** that works in both modes without any code change.

#### Impact on Docker Compose

✅ No impact. Both modes resolve `/api` correctly through their respective reverse proxy.

---

### 3. `Cashier-web-front/proxy.conf.json`

#### What changed / current content

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "info"
  }
}
```

#### Why this is needed (local only)

The Angular dev server (`ng serve`) is a Node.js process — it cannot forward browser requests to a different origin without a proxy, because the browser would block it as a cross-origin request. This file configures the dev server to act as a reverse proxy for all `/api/**` requests, forwarding them to the backend at `localhost:8080`.

#### Impact on Docker Compose

✅ No impact. This file is **only used by `ng serve`** (the dev server). It is not copied into the Docker image, and the `Dockerfile` builds the Angular app for production (`npm run build -- --configuration production`), which does not use `proxy.conf.json` at all.

---

### 4. `Cashier-web-front/angular.json` — `proxyConfig` wired to development serve configuration

#### What changed

The `serve` > `development` configuration includes:

```json
"serve": {
  "configurations": {
    "development": {
      "buildTarget": "R_Front_Project:build:development",
      "proxyConfig": "proxy.conf.json"
    }
  },
  "defaultConfiguration": "development"
}
```

#### Why this is needed

`proxyConfig` must be explicitly registered in `angular.json` for the Angular CLI to apply the proxy. Without this line, `proxy.conf.json` has no effect even if the file exists.

`defaultConfiguration: "development"` ensures `ng serve` (without flags) automatically picks up the proxy.

#### Impact on Docker Compose

✅ No impact. The production build used in Docker does not invoke the dev server.

---

### 5. `Cashier-web-back/src/main/java/com/mgh/backend/auth/security/config/SecurityConfig.java` — CORS Origins

#### What changed

```java
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:4200",           // ← local Angular dev server
    "https://rahem-social.web.app"     // ← Firebase hosted frontend
));
```

#### Why this is needed (local)

The Angular dev server runs on `http://localhost:4200`. Without this origin in the CORS allowlist, the browser blocks all API responses with a CORS error, even though the proxy forwards the request correctly.

> [!NOTE]
> The browser CORS check happens on the **response**, not the proxy request. Because `ng serve` proxies the request *server-side*, the browser still sends an `Origin: http://localhost:4200` header, and Spring Security must explicitly allow it.

#### Impact on Docker Compose

✅ No impact — but with a nuance. In Docker mode, the browser accesses the frontend through Nginx (`http://localhost:80`). Nginx forwards `/api` requests *internally* without sending the original browser `Origin` header to the backend (because it's a server-to-server call within the Docker network). The CORS origin `http://localhost:4200` is simply never evaluated in that case.

---

### 6. `Cashier-web-back/src/main/java/com/mgh/backend/Config/WebConfig.java` — Commented Out

#### What changed

The entire class is commented out. It contained view controller mappings that forwarded `/pos/**` routes to `index.html` — a server-side SPA fallback intended for when Spring Boot served the Angular static files directly.

#### Why this is correct

In the current architecture, Spring Boot **does not** serve the Angular application:
- **Local mode**: Angular is served by `ng serve` on port 4200.
- **Docker mode**: Angular is served by the Nginx container.

This forwarding logic is therefore not needed and was correctly disabled.

#### Impact on Docker Compose

✅ No impact. Nginx handles SPA routing with `try_files $uri $uri/ /index.html` in `nginx.conf`.

---

## Summary Table

| File | Change | Active In | Docker Compose Impact |
|---|---|---|---|
| `application.properties` | Hardcoded local DB/password/log config; env-var block commented out | Local only | ⚠️ **BREAKS** Docker — env vars injected by `docker-compose.yml` are ignored |
| `environment.ts` | `apiUrl: '/api'` (relative) | Both | ✅ Safe — works via proxy in both modes |
| `proxy.conf.json` | Proxies `/api` → `localhost:8080` for dev server | Local only | ✅ Safe — not included in Docker image |
| `angular.json` | Wires `proxy.conf.json` to `development` serve config | Local only | ✅ Safe — not used in production build |
| `SecurityConfig.java` | Adds `http://localhost:4200` to CORS origins | Local + Firebase | ✅ Safe — no effect on Docker internal routing |
| `WebConfig.java` | SPA forwarding commented out (not needed) | N/A | ✅ Safe — Nginx handles SPA routing |

---

## 4. Recommended Fix: Dual-Mode Properties

The only change that **breaks Docker Compose** is `application.properties`. The cleanest fix is to restore the env-var placeholders with the local values as defaults, so:

- Running locally (no env vars set) → defaults (`localhost`, `9268`, etc.) take effect automatically.
- Running in Docker Compose → injected env vars override the defaults.

**Replace the current `application.properties` content with:**

```properties
spring.application.name=cashier-backend-application
app.printer.name=${APP_PRINTER_NAME:POS-80}

spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:barakaDb}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:9268}
spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:update}

# 8 hours
jwt.expiration=${JWT_EXPIRATION:28800000}

invitation.quiz.session-ttl-minutes=${INVITATION_QUIZ_SESSION_TTL_MINUTES:10}

# Security log level: DEBUG locally, INFO in Docker (set LOG_LEVEL_SECURITY=INFO in .env)
logging.level.org.springframework.security=${LOG_LEVEL_SECURITY:DEBUG}
logging.level.com.mgh.backend.auth.security=${LOG_LEVEL_AUTH:DEBUG}
```

**Result after fix:**

| Variable | Local (no env vars set) | Docker Compose (from `.env`) |
|---|---|---|
| `DB_HOST` | `localhost` (default) | `host.docker.internal` (from `.env`) |
| `DB_PASSWORD` | `9268` (default) | value in `.env` |
| `LOG_LEVEL_SECURITY` | `DEBUG` (default) | `INFO` (if set in `.env`) |

This approach:
- Requires **zero code changes** between modes
- Keeps local running fully functional
- Restores Docker Compose to full functionality
- Makes secrets manageable through `.env` without touching source files

> [!IMPORTANT]
> After applying this fix, make sure your `.env` file in the project root contains at minimum:
> ```env
> DB_HOST=host.docker.internal
> DB_PASSWORD=9268
> LOG_LEVEL_SECURITY=INFO
> LOG_LEVEL_AUTH=INFO
> ```

---

## 5. How to Run Each Mode

### Local Mode

**Backend:**
```powershell
# From Cashier-web-back/
./mvnw spring-boot:run
# OR: use VS Code Java extension -> Run/Debug on CashierBackendApplication.java
```

**Frontend:**
```powershell
# From Cashier-web-front/
npm start         # runs: ng serve  (development config + proxy)
```

Access: **http://localhost:4200**

---

### Docker Compose Mode

```powershell
# From project root (Cashier-web/)
docker compose up --build -d
```

Access: **http://localhost** (or `http://localhost:<FRONTEND_PORT>`)

> [!TIP]
> See `DOCKER_SETUP.md` in the project root for the full Docker guide.

---

## 6. Request Flow Diagrams

### Local Mode
```
Browser (localhost:4200)
  |
  |  GET /api/products
  v
Angular Dev Server (ng serve :4200)
  |  proxy.conf.json: /api -> http://localhost:8080
  v
Spring Boot (:8080)
  |  CORS: Origin localhost:4200 -> allowed in SecurityConfig
  v
PostgreSQL (localhost:5432)
```

### Docker Compose Mode
```
Browser (localhost:80)
  |
  |  GET /api/products
  v
Nginx container (:80)
  |  nginx.conf: location /api -> proxy_pass http://backend:8080
  v
Spring Boot container (:8080)
  |  CORS: no browser Origin header (server-to-server) -> no CORS check
  v
PostgreSQL (host.docker.internal:5432)
```

---

*Last updated: September 2026*
