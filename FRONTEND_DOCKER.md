# Running ARIA with Docker (Backend + Frontend)

## Overview

The Docker setup now includes both the Java Spring Boot backend API and the React frontend. The backend serves both the REST API and the React static files.

## Quick Start

1. **Build and run with Docker Compose:**
   ```bash
   docker-compose up -d --build
   ```

2. **Access the application:**
   - **Frontend UI**: http://localhost:8080
   - **API**: http://localhost:8080/api

## How It Works

1. **Multi-stage Docker Build:**
   - Stage 1: Builds Java backend with Maven
   - Stage 2: Builds React frontend with npm
   - Stage 3: Runtime - copies both builds and runs Spring Boot

2. **Static File Serving:**
   - Spring Boot serves React static files from `/static/`
   - All non-API routes forward to React's `index.html`
   - API routes are available at `/api/*`

## Development vs Production

### Development (Recommended)
Run frontend and backend separately for hot-reload:

**Terminal 1 - Backend:**
```bash
mvn spring-boot:run
```

**Terminal 2 - Frontend:**
```bash
cd frontend
npm install
npm start
```

Access:
- Frontend: http://localhost:3000 (with hot-reload)
- API: http://localhost:8080/api

### Production (Docker)
Use Docker for production deployment:
```bash
docker-compose up -d
```

Access everything at: http://localhost:8080

## Troubleshooting

### If React files aren't loading:
1. Check that `npm run build` succeeded in Docker build
2. Verify files exist in `target/classes/static/` after build
3. Check Spring Boot logs for static resource mapping

### If API isn't accessible:
1. Check backend logs: `docker-compose logs aria-app`
2. Verify database connection: `docker-compose logs aria-postgres`
3. Ensure port 8080 isn't already in use

