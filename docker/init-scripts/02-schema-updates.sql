-- 02-schema-updates.sql
-- Garantiza columnas de borrado logico activo / activa con valor por defecto true

\c donaciones_db;

ALTER TABLE IF EXISTS personas_donantes ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE IF EXISTS entidades_beneficiarias ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE IF EXISTS necesidades_materiales ADD COLUMN IF NOT EXISTS activa boolean NOT NULL DEFAULT true;
