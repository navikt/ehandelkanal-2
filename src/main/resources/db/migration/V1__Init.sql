CREATE TABLE Report(
  id                SERIAL PRIMARY KEY,
  file_name         VARCHAR(256) NOT NULL,
  document_type     VARCHAR(64) NOT NULL,
  org_number        INT,
  invoice_number    INT,
  party_name        VARCHAR(256),
  amount            BIGINT,
  currency          VARCHAR(32),
  received_at       DATE NOT NULL,
  issued_at         DATE NOT NULL
);
