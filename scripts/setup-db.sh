#!/bin/bash

# Setup PostgreSQL database for Vokab Server

echo "🗄️  Setting up PostgreSQL database for Vokab Server"
echo ""

# Check if PostgreSQL is installed
if ! command -v psql &> /dev/null; then
    echo "❌ PostgreSQL is not installed"
    echo "   Install with: brew install postgresql (macOS)"
    echo "   or: sudo apt install postgresql (Ubuntu)"
    exit 1
fi

# Database configuration
DB_NAME="${DB_NAME:-vokabdb}"
DB_USER="${DB_USER:-vokab_user}"
DB_PASSWORD="${DB_PASSWORD:-vokab_password}"

echo "Creating database: $DB_NAME"
echo "Creating user: $DB_USER"
echo ""

# Create database and user
sudo -u postgres psql <<EOF
-- Create user if not exists
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_user WHERE usename = '$DB_USER') THEN
        CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';
    END IF;
END
\$\$;

-- Create database if not exists
SELECT 'CREATE DATABASE $DB_NAME'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB_NAME')\gexec

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;

-- Connect to the database and grant schema privileges
\c $DB_NAME
GRANT ALL ON SCHEMA public TO $DB_USER;
EOF

if [ $? -eq 0 ]; then
    echo "✅ Database setup completed successfully!"
    echo ""
    echo "Connection string: jdbc:postgresql://localhost:5432/$DB_NAME"
    echo "Username: $DB_USER"
    echo "Password: $DB_PASSWORD"
    echo ""
    echo "Update your .env file with:"
    echo "DATABASE_URL=jdbc:postgresql://localhost:5432/$DB_NAME"
    echo "DATABASE_USERNAME=$DB_USER"
    echo "DATABASE_PASSWORD=$DB_PASSWORD"
    echo "DATABASE_DRIVER=org.postgresql.Driver"
    echo "HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect"
else
    echo "❌ Database setup failed"
    exit 1
fi

