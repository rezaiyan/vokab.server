# Vokab Server Backend

A comprehensive backend service for the Vokab application built with Spring Boot and Kotlin.

## Features

- **User Management**: Complete user authentication and profile management
- **Google OAuth2 Authentication**: Secure login/logout with Google
- **JWT Token-based Authentication**: Access tokens and refresh tokens
- **Push Notifications**: Firebase Cloud Messaging integration
- **In-App Purchases**: RevenueCat webhook integration for subscription management
- **AI Middleware**: OpenRouter AI for vocabulary extraction from images and daily insights
- **Clean Architecture**: Layered architecture with dependency injection
- **PostgreSQL Database**: Production-ready relational database (H2 for development)

## Tech Stack

- **Kotlin** 1.9.25
- **Spring Boot** 3.5.6
- **Spring Security** with OAuth2
- **Spring Data JPA**
- **JWT** (JJWT 0.12.3)
- **Firebase Admin SDK** 9.2.0
- **PostgreSQL** / **H2** (development)
- **WebFlux** for reactive HTTP clients

## Architecture

```
src/main/kotlin/com/alirezaiyan/vokab/server/
├── config/               # Configuration classes
├── domain/
│   ├── entity/          # JPA entities
│   └── repository/      # Repository interfaces
├── exception/           # Exception handlers
├── presentation/
│   ├── controller/      # REST controllers
│   └── dto/            # Data transfer objects
├── security/            # Security configuration and JWT
├── service/             # Business logic
└── task/               # Scheduled tasks
```

## Getting Started

### Local Development (3 Steps)

```bash
# 1. Copy environment template
cp env.example .env

# 2. Edit .env and add your API keys
nano .env  # Add OPENROUTER_API_KEY and GOOGLE_CLIENT_ID

# 3. Start the server
./scripts/start-dev.sh
```

**🎉 That's it!** Server is running on `http://localhost:8080`

Test it: `curl http://localhost:8080/api/v1/health`

### Production Deployment (5 Minutes)

Deploy to Render.com with auto-deployments:

**📋 See [QUICKSTART_RENDER.md](QUICKSTART_RENDER.md) for step-by-step guide**

Or full documentation: [RENDER_DEPLOYMENT.md](RENDER_DEPLOYMENT.md)

### Prerequisites

- Java 21 or higher
- Gradle 8.x (included via wrapper)
- PostgreSQL (for production) or H2 (for development - default)

### Configuration

**Required API Keys:**

1. **OpenRouter AI** - https://openrouter.ai/keys
2. **Google OAuth** - https://console.cloud.google.com/apis/credentials

**Optional:**
- Firebase (push notifications)
- RevenueCat (in-app purchases)
- **GitHub Token** (for vocabulary collections) - https://github.com/settings/tokens
  - **Why?** Unauthenticated API calls have a 60/hour rate limit, authenticated calls have 5000/hour
  - **How?** See `GITHUB_TOKEN_SETUP.md` for step-by-step instructions
  - **Tip:** Without a token, the collections feature will hit rate limits quickly

### Setting Up .env File

The `.env` file contains all configuration. For local development with H2 database:

```bash
# Minimal setup for local testing
DATABASE_URL=jdbc:h2:mem:vokabdb
DATABASE_USERNAME=sa
DATABASE_PASSWORD=
DATABASE_DRIVER=org.h2.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.H2Dialect

JWT_SECRET=your-dev-secret-change-in-production
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
OPENROUTER_API_KEY=your-openrouter-api-key

CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8081
PORT=8080
```

**Note:** For production, use PostgreSQL and change all secrets!

### Database Setup

#### Development (H2)
No setup required. H2 runs in-memory by default.

#### Production (PostgreSQL)
```sql
CREATE DATABASE vokabdb;
CREATE USER vokab_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE vokabdb TO vokab_user;
```

### Running the Application

#### Option 1: Quick Development (H2 Database)
```bash
./scripts/start-dev.sh
```

#### Option 2: Production Mode (PostgreSQL)
```bash
# Setup database first
./scripts/setup-db.sh

# Start server
./scripts/start-prod.sh
```

#### Option 3: Docker
```bash
docker-compose up -d
```

#### Option 4: Manual
```bash
./gradlew bootRun
```

#### Option 5: Deploy to Render.com (Production)
```bash
# See RENDER_DEPLOYMENT.md for detailed instructions
# Quick start:
git push origin main  # Auto-deploys to Render
```

The server will start on port 8080 (configurable via `PORT` environment variable).

### Testing the Server

```bash
# Run endpoint tests
./scripts/test-endpoints.sh

# Test specific endpoint
curl http://localhost:8080/api/v1/health

# With authentication
ACCESS_TOKEN=your_jwt ./scripts/test-endpoints.sh
```

## API Endpoints

### Authentication
- `POST /api/v1/auth/google` - Authenticate with Google ID token
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/logout` - Logout (revoke refresh token)
- `POST /api/v1/auth/logout-all` - Logout from all devices

### User Management
- `GET /api/v1/users/me` - Get current user profile
- `PATCH /api/v1/users/me` - Update user profile
- `DELETE /api/v1/users/me` - Delete user account

### Push Notifications
- `POST /api/v1/notifications/register-token` - Register FCM push token
- `DELETE /api/v1/notifications/token/{token}` - Deactivate push token
- `DELETE /api/v1/notifications/tokens` - Deactivate all user tokens
- `POST /api/v1/notifications/send` - Send notification to user
- `GET /api/v1/notifications/tokens` - Get active token count

### Subscriptions
- `GET /api/v1/subscriptions` - Get user subscriptions
- `GET /api/v1/subscriptions/active` - Get active subscription

### Webhooks
- `POST /api/v1/webhooks/revenuecat` - RevenueCat webhook endpoint

### AI Services
- `POST /api/v1/ai/extract-vocabulary` - Extract vocabulary from image
- `POST /api/v1/ai/generate-insight` - Generate daily motivational insight
- `GET /api/v1/ai/health` - Check AI service status

## Authentication Flow

1. Client sends Google ID token to `/api/v1/auth/google`
2. Server verifies token with Google
3. Server creates/updates user and returns JWT access token and refresh token
4. Client uses access token in `Authorization: Bearer {token}` header
5. When access token expires, use refresh token at `/api/v1/auth/refresh`

## Push Notifications Setup

1. Add Firebase service account JSON file to your server
2. Set `FIREBASE_SERVICE_ACCOUNT_PATH` environment variable
3. Clients register push tokens via `/api/v1/notifications/register-token`
4. Send notifications via `/api/v1/notifications/send` or programmatically

## OpenRouter AI Integration

The backend acts as a middleware for OpenRouter AI API calls:

1. **Vocabulary Extraction**: Upload image (as base64) to extract vocabulary with translations
2. **Daily Insights**: Generate motivational messages based on learning progress
3. **Rate Limiting**: 
   - Image processing: 5 requests/minute per user
   - Daily insights: 10 requests/minute per user
4. **Benefits**:
   - API key stays secure on server
   - Centralized logging and monitoring
   - Rate limiting prevents abuse
   - Can add caching layer

## RevenueCat Integration

1. Configure webhook in RevenueCat dashboard to point to `/api/v1/webhooks/revenuecat`
2. Set `REVENUECAT_WEBHOOK_SECRET` for webhook verification
3. Server automatically updates user subscription status based on webhook events

Supported events:
- INITIAL_PURCHASE
- RENEWAL
- CANCELLATION
- UNCANCELLATION
- EXPIRATION
- NON_RENEWING_PURCHASE
- BILLING_ISSUE

## Security

- All endpoints except `/api/v1/auth/**` and `/api/v1/webhooks/**` require authentication
- JWT tokens are validated on every request
- Refresh tokens are stored in database and can be revoked
- CORS is configured for specified origins
- Passwords/secrets should be stored in environment variables, never in code

## Development

### Running Tests
```bash
./gradlew test
```

### Building
```bash
./gradlew build
```

### Creating Docker Image (optional)
```bash
./gradlew bootBuildImage
```

## Scheduled Tasks

- **Token Cleanup**: Runs daily at 2 AM to remove expired refresh tokens

## Monitoring

- Health check: `GET /api/v1/health`
- H2 Console (dev only): http://localhost:8080/h2-console

## Deployment

### Render.com (Recommended for Production)

This project is configured for one-click deployment on Render.com:

1. Push to Git repository
2. Connect to Render using the included `render.yaml` blueprint
3. Add required environment variables (API keys)
4. Auto-deploy on every push

**See [RENDER_DEPLOYMENT.md](RENDER_DEPLOYMENT.md) for complete deployment guide.**

### Other Platforms

The application can also be deployed on:
- **Heroku**: Use included Dockerfile
- **AWS ECS/Fargate**: Deploy Docker container
- **Google Cloud Run**: Deploy Docker container
- **DigitalOcean App Platform**: Use Dockerfile
- **Railway**: Connect Git repository

## License

Proprietary - All rights reserved

