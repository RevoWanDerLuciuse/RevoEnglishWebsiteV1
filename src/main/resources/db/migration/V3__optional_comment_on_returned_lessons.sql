-- Payments, cancellations and refunds are handled by the teacher outside the site; the portal only records them.
-- Returning a consumed lesson therefore no longer requires a written reason (an optional comment is enough).
-- Manual ADJUSTMENTs still require a reason and the administrator.
ALTER TABLE lesson_credit_transaction DROP CONSTRAINT ck_credit_shape;
ALTER TABLE lesson_credit_transaction ADD CONSTRAINT ck_credit_shape CHECK (
    (type = 'GRANT' AND delta > 0)
    OR (type = 'CONSUME' AND delta = -1 AND lesson_ref IS NOT NULL AND lesson_date IS NOT NULL)
    OR (type = 'RESTORE' AND delta = 1 AND reverses_transaction_id IS NOT NULL)
    OR (type = 'ADJUSTMENT' AND delta <> 0 AND reason IS NOT NULL AND created_by IS NOT NULL));
