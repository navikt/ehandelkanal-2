CREATE TABLE Report(
  id                SERIAL PRIMARY KEY,
  file_name         VARCHAR(256) NOT NULL,
  document_type     VARCHAR(64) NOT NULL,
  org_number        INT,
  invoice_number    VARCHAR(64),
  party_name        VARCHAR(256),
  amount            NUMERIC(18,4),
  currency          VARCHAR(32),
  received_at       DATE NOT NULL,
  issued_at         DATE NOT NULL
);
