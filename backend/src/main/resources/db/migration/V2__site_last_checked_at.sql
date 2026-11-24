-- Lets the scheduler find due sites without scanning site_check.
ALTER TABLE site ADD COLUMN last_checked_at TIMESTAMPTZ;
