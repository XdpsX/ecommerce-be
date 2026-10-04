# Ecommerce Backend

Spring Boot backend for a personal E-commerce learning project.

## Run locally

Requirements:

- Java 25
- Docker with Docker Compose

Create the local environment file:

```powershell
Copy-Item .env.example .env
```

Update the placeholder credentials in `.env` when the related integrations are needed. The file is ignored by Git.

Start MySQL:

```powershell
docker compose up -d mysql
```

The initialization script creates the `ecommerce` database the first time MySQL starts with an empty volume.

Run the application with the `dev` profile configured in `.env`:

```powershell
.\mvnw.cmd spring-boot:run
```

The API runs at `http://localhost:8080` by default. Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.

## Verify changes

```powershell
.\mvnw.cmd spotless:check verify
```

Use `spotless:apply` explicitly when source formatting is needed:

```powershell
.\mvnw.cmd spotless:apply
```

Stop the local database without deleting its data:

```powershell
docker compose down
```
