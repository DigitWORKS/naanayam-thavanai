-- =====================================================================
-- Naanayam Thavanai - V2: custom amount payments + pre-closure
-- =====================================================================

-- ---------------------------------------------------------------------
-- Tenant-level rules
-- ---------------------------------------------------------------------
ALTER TABLE tenant_settings
    -- Custom amount allocation
    ADD COLUMN allocation_order      ENUM('PENALTY_FIRST','INSTALLMENT_FIRST') NOT NULL DEFAULT 'PENALTY_FIRST',
    ADD COLUMN advance_allocation    ENUM('NEXT_INSTALLMENTS','LAST_INSTALLMENTS') NOT NULL DEFAULT 'NEXT_INSTALLMENTS',
    -- Pre-closure
    ADD COLUMN preclosure_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN preclosure_rebate     ENUM('NONE','UNEARNED_INTEREST') NOT NULL DEFAULT 'NONE',
    ADD COLUMN preclosure_charge_type  ENUM('NONE','FIXED','PERCENT') NOT NULL DEFAULT 'NONE',
    ADD COLUMN preclosure_charge_value DECIMAL(10,2) NULL;   -- PERCENT is on outstanding amount

-- ---------------------------------------------------------------------
-- Loan / installment / payment status additions
-- ---------------------------------------------------------------------
ALTER TABLE loan
    MODIFY COLUMN status ENUM('ACTIVE','CLOSED','PRECLOSED','DEFAULTED') NOT NULL DEFAULT 'ACTIVE';

-- SETTLED = remaining installment closed by a pre-closure payment
ALTER TABLE installment
    MODIFY COLUMN status ENUM('PENDING','PARTIAL','PAID','MISSED','SETTLED') NOT NULL DEFAULT 'PENDING';

ALTER TABLE payment
    ADD COLUMN purpose ENUM('REGULAR','PRECLOSURE') NOT NULL DEFAULT 'REGULAR' AFTER entry_type;

-- ---------------------------------------------------------------------
-- Pre-closure record (one per loan) - snapshot of the settlement calculation
-- ---------------------------------------------------------------------
CREATE TABLE loan_preclosure (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT NOT NULL,
    loan_id               BIGINT NOT NULL,
    payment_id            BIGINT NOT NULL,
    outstanding_principal DECIMAL(12,2) NOT NULL,
    outstanding_interest  DECIMAL(12,2) NOT NULL,
    pending_penalty       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    interest_rebate       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    preclosure_charge     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    settlement_amount     DECIMAL(12,2) NOT NULL,  -- principal + interest + penalty - rebate + charge
    closed_by             BIGINT NOT NULL,
    closed_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_preclosure_loan (loan_id),
    INDEX idx_preclosure_tenant_date (tenant_id, closed_at),
    CONSTRAINT fk_lp_tenant  FOREIGN KEY (tenant_id)  REFERENCES tenant(id),
    CONSTRAINT fk_lp_loan    FOREIGN KEY (loan_id)    REFERENCES loan(id),
    CONSTRAINT fk_lp_payment FOREIGN KEY (payment_id) REFERENCES payment(id),
    CONSTRAINT fk_lp_user    FOREIGN KEY (closed_by)  REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
