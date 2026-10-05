-- ============================================================
-- Update login credentials for an ALREADY-SEEDED database
-- (only needed if the app was previously run with the old
--  admin@opportunity.com / Admin@123 seed data)
--
-- Usage in phpMyAdmin: select the `opportunity_school` database,
-- open the SQL tab, paste this file and click Go.
-- Or from a terminal:  mysql -u root opportunity_school < update-credentials.sql
-- ============================================================

USE opportunity_school;

-- 1. Rename the admin account
UPDATE users
SET email = 'admin@oes.com'
WHERE email = 'admin@opportunity.com';

-- 2. Rename the seeded teacher account
UPDATE users
SET email = 'teacher@oes.com'
WHERE email = 'teacher@opportunity.com';

-- 3. Reset passwords (bcrypt hashes of Admin@2026 / Teacher@2026)
UPDATE users
SET password = '$2a$10$Thb6Jtev6GXxbr1itGULkuzqvv.8jmJvpwLY8ErVZGd0E6dJrDpWS'
WHERE email = 'admin@oes.com';

UPDATE users
SET password = '$2a$10$SSEGI9tYLoKajHhPXa5mYeimHgGsKtf50Eq0mBWlvdYEaRjkjAGmS'
WHERE email = 'teacher@oes.com';

-- Verify
SELECT id, email, role, active FROM users;
