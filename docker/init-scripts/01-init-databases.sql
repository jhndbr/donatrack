-- 01-init-databases.sql
-- Script de inicialización para PostgreSQL
-- Crea las bases de datos independientes para cada microservicio desacoplado

SELECT 'CREATE DATABASE donaciones_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'donaciones_db')\gexec

SELECT 'CREATE DATABASE incentivos_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'incentivos_db')\gexec

SELECT 'CREATE DATABASE notificaciones_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notificaciones_db')\gexec

SELECT 'CREATE DATABASE keycloak_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak_db')\gexec

