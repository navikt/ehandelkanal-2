CREATE SEQUENCE IF NOT EXISTS report;

ALTER TABLE report ALTER COLUMN id SET DEFAULT nextval('report');

-- Valgfritt: synkroniser sekvensen til neste ID hvis tabellen kan ha eksisterende rader
-- SELECT setval('report', GREATEST(COALESCE((SELECT MAX(id) FROM report), 0) + 1, 1), false);
