-- =====================================================================
-- Naanayam Thavanai - V1 initial schema
-- MySQL 8.x | InnoDB | utf8mb4
-- Multi-tenant: every business table carries tenant_id
-- =====================================================================

-- ---------------------------------------------------------------------
-- Tenant (financier)
-- ---------------------------------------------------------------------
CREATE TABLE tenant (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    owner_name    VARCHAR(100) NOT NULL,
    phone         VARCHAR(15)  NOT NULL,
    licence_no    VARCHAR(50)  NULL,                 -- money-lending licence
    status        ENUM('TRIAL','ACTIVE','SUSPENDED') NOT NULL DEFAULT 'TRIAL',
    plan_expiry   DATE NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant_settings (
    tenant_id                    BIGINT PRIMARY KEY,
    max_interest_rate            DECIMAL(5,2) NOT NULL,         -- legal cap, % per annum
    allowed_interest_methods     SET('FLAT','REDUCING') NOT NULL DEFAULT 'FLAT',
    max_active_loans_per_client  INT NOT NULL DEFAULT 2,
    penalty_enabled              BOOLEAN NOT NULL DEFAULT FALSE,
    penalty_type                 ENUM('FIXED','PERCENT') NULL,
    penalty_value                DECIMAL(10,2) NULL,
    penalty_grace_days           INT NOT NULL DEFAULT 0,
    penalty_max_cap              DECIMAL(10,2) NULL,            -- max penalty per loan
    record_retention_years       INT NOT NULL DEFAULT 8,
    updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ts_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Users (tenant_id NULL only for SUPER_ADMIN)
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT NULL,
    name          VARCHAR(100) NOT NULL,
    phone         VARCHAR(15)  NOT NULL,
    pin_hash      VARCHAR(255) NOT NULL,
    role          ENUM('SUPER_ADMIN','OWNER','AGENT') NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_phone (phone),
    INDEX idx_user_tenant_role (tenant_id, role),
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Area + agent assignment (many-to-many)
-- ---------------------------------------------------------------------
CREATE TABLE area (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_area_tenant_name (tenant_id, name),
    CONSTRAINT fk_area_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_area (
    tenant_id   BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    area_id     BIGINT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, area_id),
    INDEX idx_ua_tenant_area (tenant_id, area_id),
    CONSTRAINT fk_ua_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_ua_user   FOREIGN KEY (user_id)   REFERENCES app_user(id),
    CONSTRAINT fk_ua_area   FOREIGN KEY (area_id)   REFERENCES area(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Client (borrower) + documents
-- ---------------------------------------------------------------------
CREATE TABLE client (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT NOT NULL,
    area_id            BIGINT NOT NULL,
    name               VARCHAR(100) NOT NULL,
    phone              VARCHAR(15)  NOT NULL,
    alt_phone          VARCHAR(15)  NULL,
    address            VARCHAR(500) NULL,
    aadhaar_last4      CHAR(4)      NULL,            -- never store full Aadhaar
    guarantor_name     VARCHAR(100) NULL,
    guarantor_phone    VARCHAR(15)  NULL,
    consent_given_at   DATETIME     NULL,
    consent_version    VARCHAR(20)  NULL,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by         BIGINT NOT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_client_tenant_phone (tenant_id, phone),
    INDEX idx_client_tenant_area (tenant_id, area_id),
    INDEX idx_client_tenant_name (tenant_id, name),
    CONSTRAINT fk_client_tenant  FOREIGN KEY (tenant_id)  REFERENCES tenant(id),
    CONSTRAINT fk_client_area    FOREIGN KEY (area_id)    REFERENCES area(id),
    CONSTRAINT fk_client_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE client_document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    client_id   BIGINT NOT NULL,
    doc_type    ENUM('PHOTO','AADHAAR','PAN','GUARANTOR_ID','OTHER') NOT NULL,
    file_key    VARCHAR(255) NOT NULL,               -- S3 object key, not a public URL
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cd_tenant_client (tenant_id, client_id),
    CONSTRAINT fk_cd_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(id),
    CONSTRAINT fk_cd_client   FOREIGN KEY (client_id)   REFERENCES client(id),
    CONSTRAINT fk_cd_uploader FOREIGN KEY (uploaded_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Loan (terms locked after creation: no renewal / top-up)
-- ---------------------------------------------------------------------
CREATE TABLE loan (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT NOT NULL,
    client_id            BIGINT NOT NULL,
    loan_no              VARCHAR(30) NOT NULL,       -- human-readable, per tenant
    principal            DECIMAL(12,2) NOT NULL,
    interest_rate        DECIMAL(5,2)  NOT NULL,     -- % per annum, validated <= tenant cap
    interest_method      ENUM('FLAT','REDUCING') NOT NULL,
    interest_collection  ENUM('UPFRONT_DEDUCTED','ADDED_TO_REPAYMENT') NOT NULL,
    interest_amount      DECIMAL(12,2) NOT NULL,
    disbursed_amount     DECIMAL(12,2) NOT NULL,     -- principal minus upfront interest, if deducted
    total_payable        DECIMAL(12,2) NOT NULL,
    frequency            ENUM('DAILY','WEEKLY') NOT NULL,
    installment_amount   DECIMAL(12,2) NOT NULL,
    tenure_count         INT NOT NULL,               -- number of installments
    start_date           DATE NOT NULL,
    original_end_date    DATE NOT NULL,
    current_end_date     DATE NOT NULL,              -- moves when tenure is extended
    status               ENUM('ACTIVE','CLOSED','DEFAULTED') NOT NULL DEFAULT 'ACTIVE',
    closed_at            DATETIME NULL,
    legal_note           VARCHAR(1000) NULL,
    created_by           BIGINT NOT NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_loan_tenant_no (tenant_id, loan_no),
    INDEX idx_loan_tenant_client_status (tenant_id, client_id, status),
    INDEX idx_loan_tenant_status (tenant_id, status),
    CONSTRAINT fk_loan_tenant  FOREIGN KEY (tenant_id)  REFERENCES tenant(id),
    CONSTRAINT fk_loan_client  FOREIGN KEY (client_id)  REFERENCES client(id),
    CONSTRAINT fk_loan_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT chk_loan_amounts CHECK (principal > 0 AND installment_amount > 0 AND tenure_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Installment schedule
-- Missed -> mark MISSED and append a new installment (tenure extension)
-- ---------------------------------------------------------------------
CREATE TABLE installment (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT NOT NULL,
    loan_id        BIGINT NOT NULL,
    seq_no         INT NOT NULL,
    due_date       DATE NOT NULL,
    due_amount     DECIMAL(12,2) NOT NULL,
    paid_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    penalty_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status         ENUM('PENDING','PARTIAL','PAID','MISSED') NOT NULL DEFAULT 'PENDING',
    is_extension   BOOLEAN NOT NULL DEFAULT FALSE,   -- added due to a missed installment
    extended_from  BIGINT NULL,                      -- the MISSED installment it replaces
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inst_loan_seq (loan_id, seq_no),
    INDEX idx_inst_tenant_due_status (tenant_id, due_date, status),
    INDEX idx_inst_loan_status (loan_id, status),
    CONSTRAINT fk_inst_tenant   FOREIGN KEY (tenant_id)     REFERENCES tenant(id),
    CONSTRAINT fk_inst_loan     FOREIGN KEY (loan_id)       REFERENCES loan(id),
    CONSTRAINT fk_inst_extended FOREIGN KEY (extended_from) REFERENCES installment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Payment (append-only; corrections via REVERSAL rows)
-- ---------------------------------------------------------------------
CREATE TABLE payment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    loan_id         BIGINT NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,          -- negative for REVERSAL
    entry_type      ENUM('PAYMENT','REVERSAL') NOT NULL DEFAULT 'PAYMENT',
    reversal_of     BIGINT NULL,
    mode            ENUM('CASH','UPI') NOT NULL,
    upi_ref_no      VARCHAR(50) NULL,
    paid_on         DATETIME NOT NULL,               -- actual collection time (device)
    collected_by    BIGINT NOT NULL,
    client_txn_id   CHAR(36) NOT NULL,               -- UUID from device, for idempotent offline sync
    remarks         VARCHAR(255) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- server receive time
    UNIQUE KEY uk_payment_client_txn (tenant_id, client_txn_id),
    INDEX idx_pay_loan_paid (loan_id, paid_on),
    INDEX idx_pay_tenant_collector_paid (tenant_id, collected_by, paid_on),
    CONSTRAINT fk_pay_tenant    FOREIGN KEY (tenant_id)    REFERENCES tenant(id),
    CONSTRAINT fk_pay_loan      FOREIGN KEY (loan_id)      REFERENCES loan(id),
    CONSTRAINT fk_pay_collector FOREIGN KEY (collected_by) REFERENCES app_user(id),
    CONSTRAINT fk_pay_reversal  FOREIGN KEY (reversal_of)  REFERENCES payment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- How each payment was applied to installments (supports partial / multi-installment payments)
CREATE TABLE payment_allocation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    payment_id      BIGINT NOT NULL,
    installment_id  BIGINT NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    penalty_part    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    INDEX idx_pa_payment (payment_id),
    INDEX idx_pa_installment (installment_id),
    CONSTRAINT fk_pa_tenant      FOREIGN KEY (tenant_id)      REFERENCES tenant(id),
    CONSTRAINT fk_pa_payment     FOREIGN KEY (payment_id)     REFERENCES payment(id),
    CONSTRAINT fk_pa_installment FOREIGN KEY (installment_id) REFERENCES installment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Agent cash handover (agent -> owner reconciliation)
-- ---------------------------------------------------------------------
CREATE TABLE cash_handover (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    agent_id        BIGINT NOT NULL,
    handover_date   DATE NOT NULL,
    expected_amount DECIMAL(12,2) NOT NULL,          -- sum of agent's CASH payments for the day
    handed_amount   DECIMAL(12,2) NOT NULL,
    received_by     BIGINT NULL,                     -- owner who confirmed
    status          ENUM('PENDING','CONFIRMED','DISPUTED') NOT NULL DEFAULT 'PENDING',
    remarks         VARCHAR(255) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at    DATETIME NULL,
    UNIQUE KEY uk_handover_agent_date (tenant_id, agent_id, handover_date),
    CONSTRAINT fk_ch_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(id),
    CONSTRAINT fk_ch_agent    FOREIGN KEY (agent_id)    REFERENCES app_user(id),
    CONSTRAINT fk_ch_receiver FOREIGN KEY (received_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
