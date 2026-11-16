-- =====================================================================
-- Naanayam Thavanai - one-time local database setup (MySQL 8)
-- Run as root:  mysql -u root -p < db/create-database.sql
-- Tables are created by Flyway when the service starts.
-- Change the password for anything other than local development.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS naanayam
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'naanayam'@'localhost' IDENTIFIED BY 'naanayam';
CREATE USER IF NOT EXISTS 'naanayam'@'%' IDENTIFIED BY 'naanayam';

GRANT ALL PRIVILEGES ON naanayam.* TO 'naanayam'@'localhost';
GRANT ALL PRIVILEGES ON naanayam.* TO 'naanayam'@'%';

FLUSH PRIVILEGES;
