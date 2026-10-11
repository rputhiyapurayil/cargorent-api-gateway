# cargorent-api-gateway

Single entry point for CarGoRent (Spring Cloud Gateway, port **8080**). Part of **CarGoRent** – see
[cargorent-infra](../cargorent-infra/README.md) for the full system.

- **Routing** (`application.yml`, `StripPrefix=1`): `/api/auth/**`, `/api/users/**` → user-service ·
  `/api/vehicles/**` → vehicle-service · `/api/bookings/**`, `/api/reports/**` → booking-service ·
  `/api/payments/**` → payment-service · `/api/notifications/**` → notification-service.
- **JWT validation** (`JwtAuthFilter`): verifies the HS256 token from user-service, strips any client-sent
  `X-User-*` headers and forwards `X-User-Id`, `X-User-Role`, `X-User-Name`, `X-User-Email`.
- **Public routes** (no token needed): login, register, vehicle search/details, quote.
- **Internal routes** (always 404 from outside): `PATCH /api/bookings/*/status`, `GET /api/bookings/booked-vehicle-ids`,
  `/api/bookings/upcoming`, `/api/bookings/user/**`, `POST /api/notifications`.
- **CORS** for `http://localhost:5173` and `http://localhost:3000`.
- Fixed service URLs via env vars (`USER_SERVICE_URL`, …). Eureka discovery is future scope.

```bash
mvn spring-boot:run      # JWT_SECRET must match user-service
```
