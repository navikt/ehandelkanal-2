SELECT pg_get_serial_sequence('report', 'id');

ALTER TABLE report
    ALTER COLUMN id SET DEFAULT nextval('report_id_seq');

SELECT pg_get_serial_sequence('report', 'id');  -- usually 'report_id_seq'

