-- Sørg for at standardsekvensen fra SERIAL finnes
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relkind = 'S'
          AND c.relname = 'report_id_seq'
    ) THEN
CREATE SEQUENCE report_id_seq;
END IF;
END $$;

-- Knytt eierskap til kolonnen (rydder opp dependences)
ALTER SEQUENCE report_id_seq OWNED BY report.id;

-- Sett riktig DEFAULT på id-kolonnen
ALTER TABLE report
    ALTER COLUMN id SET DEFAULT nextval('report_id_seq');

-- Synk sekvensen til gjeldende maks id slik at neste nextval blir riktig
SELECT setval('report_id_seq', COALESCE((SELECT MAX(id) FROM report), 0), true);