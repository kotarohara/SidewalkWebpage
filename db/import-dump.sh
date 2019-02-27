#!/bin/bash

psql -v ON_ERROR_STOP=1 -U postgres -d postgres <<-EOSQL
    SELECT pg_terminate_backend(pg_stat_activity.pid)
    FROM pg_stat_activity
    WHERE (pg_stat_activity.datname = '$1')
    AND pid <> pg_backend_pid();

    DROP DATABASE IF EXISTS "$1";
    DROP SCHEMA IF EXISTS $1;
    DROP USER IF EXISTS sidewalk;

    CREATE USER sidewalk WITH PASSWORD 'sidewalk';
    CREATE DATABASE "$1" WITH OWNER=sidewalk TEMPLATE template0;
    GRANT ALL PRIVILEGES ON DATABASE $1 to sidewalk;

    ALTER USER sidewalk SUPERUSER;
    GRANT ALL PRIVILEGES ON DATABASE $1 TO sidewalk;

    CREATE SCHEMA $1;
    GRANT ALL ON ALL TABLES IN SCHEMA $1 TO sidewalk;
    ALTER DEFAULT PRIVILEGES IN SCHEMA $1 GRANT ALL ON TABLES TO sidewalk;
    ALTER DEFAULT PRIVILEGES IN SCHEMA $1 GRANT ALL ON SEQUENCES TO sidewalk;
EOSQL

pg_restore -U sidewalk -d sidewalk /opt/$1-dump
