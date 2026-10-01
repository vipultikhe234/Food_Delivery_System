#!/bin/sh
# Creates one database per service with an owner role (runs migrations) and an app role (DML only)
# (docs/06-database-design.md §1). Runs once, on the first start of an empty data volume.
# Passwords come from the environment; there are no defaults.
set -eu

: "${LOCAL_DB_OWNER_PASSWORD:?LOCAL_DB_OWNER_PASSWORD must be set in .env}"
: "${LOCAL_DB_APP_PASSWORD:?LOCAL_DB_APP_PASSWORD must be set in .env}"

# realtime-service has no database (Redis Pub/Sub only).
SERVICES="identity user audit restaurant catalog media cart promotion order payment delivery
location notification review search analytics admin recommendation ai pos kitchen inventory
procurement"
POSTGIS_DATABASES="user_db restaurant_db order_db delivery_db location_db search_db"

run_sql() {
  db="$1"
  shift
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" "$@"
}

for service in $SERVICES; do
  db="${service}_db"
  owner="${service}_owner"
  app="${service}_app"
  echo "Creating ${db} (${owner}, ${app})"

  run_sql postgres -v owner="$owner" -v app="$app" -v db="$db" \
    -v owner_pw="$LOCAL_DB_OWNER_PASSWORD" -v app_pw="$LOCAL_DB_APP_PASSWORD" <<'SQL'
CREATE ROLE :"owner" LOGIN PASSWORD :'owner_pw' BYPASSRLS;
CREATE ROLE :"app" LOGIN PASSWORD :'app_pw';
CREATE DATABASE :"db" OWNER :"owner";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"app";
SQL

  run_sql "$db" -v owner="$owner" -v app="$app" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"owner";
GRANT USAGE ON SCHEMA public TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app";
SQL
done

# These extensions need superuser rights; migrations may still declare them with IF NOT EXISTS.
for db in $POSTGIS_DATABASES; do
  run_sql "$db" -c "CREATE EXTENSION IF NOT EXISTS postgis;"
done
run_sql search_db -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
run_sql identity_db -c "CREATE EXTENSION IF NOT EXISTS citext;"
