package tn.digitworks.naanayamthavanai.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.tenant.Tenant;
import tn.digitworks.naanayamthavanai.tenant.TenantRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpiRefCheckTest {

    private static final long TENANT = 7L;

    private PaymentRepository payments;
    private TenantRepository tenants;
    private UpiRefCheck check;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        tenants = mock(TenantRepository.class);
        when(tenants.lockById(anyLong())).thenReturn(Optional.of(new Tenant()));
        when(payments.isUpiRefInUse(anyLong(), any())).thenReturn(false);
        check = new UpiRefCheck(payments, tenants);
    }

    @Test
    void cashKeepsNoUtr() {
        assertNull(check.resolve(TENANT, Payment.Mode.CASH, "431912345678"));
        verify(tenants, never()).lockById(anyLong());
    }

    @Test
    void validUtrIsCleanedAndLocksTenant() {
        assertEquals("431912345678", check.resolve(TENANT, Payment.Mode.UPI, " 4319 1234 5678 "));
        verify(tenants).lockById(TENANT);
        verify(payments).isUpiRefInUse(TENANT, "431912345678");
    }

    @Test
    void missingUtr() {
        ApiException e = assertThrows(ApiException.class, () -> check.resolve(TENANT, Payment.Mode.UPI, "  "));
        assertEquals("UPI_REF_REQUIRED", e.getCode());
    }

    @Test
    void utrMustBe12Digits() {
        assertEquals("INVALID_UPI_REF", assertThrows(ApiException.class,
                () -> check.resolve(TENANT, Payment.Mode.UPI, "12345")).getCode());
        assertEquals("INVALID_UPI_REF", assertThrows(ApiException.class,
                () -> check.resolve(TENANT, Payment.Mode.UPI, "43191234567A")).getCode());
        assertEquals("INVALID_UPI_REF", assertThrows(ApiException.class,
                () -> check.resolve(TENANT, Payment.Mode.UPI, "4319123456789")).getCode());
    }

    @Test
    void duplicateUtrIsConflict() {
        when(payments.isUpiRefInUse(TENANT, "431912345678")).thenReturn(true);
        ApiException e = assertThrows(ApiException.class,
                () -> check.resolve(TENANT, Payment.Mode.UPI, "431912345678"));
        assertEquals("DUPLICATE_UPI_REF", e.getCode());
        assertEquals(409, e.getStatus().value());
    }
}
