package tn.digitworks.naanayamthavanai.client;

import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanSummary;
import tn.digitworks.naanayamthavanai.user.UserDtos;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ClientDtos {

    private ClientDtos() {
    }

    public record ClientResponse(Long id, Long areaId, String areaName, String name, String phone, String altPhone,
                                 String address, String aadhaarLast4, String guarantorName, String guarantorPhone,
                                 LocalDateTime consentGivenAt, boolean isActive) {
    }

    public record ClientDetail(Long id, Long areaId, String areaName, String name, String phone, String altPhone,
                               String address, String aadhaarLast4, String guarantorName, String guarantorPhone,
                               LocalDateTime consentGivenAt, boolean isActive, List<LoanSummary> activeLoans,
                               BigDecimal totalOutstanding) {
    }

    public record ClientCreate(
            @NotNull Long areaId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = UserDtos.PHONE) String phone,
            @Pattern(regexp = UserDtos.PHONE) String altPhone,
            @Size(max = 500) String address,
            @Pattern(regexp = "^\\d{4}$", message = "must be the last 4 digits only") String aadhaarLast4,
            @Size(max = 100) String guarantorName,
            @Pattern(regexp = UserDtos.PHONE) String guarantorPhone,
            @NotNull @AssertTrue(message = "client consent is required") Boolean consentGiven,
            @NotBlank @Size(max = 20) String consentVersion) {
    }

    public record ClientUpdate(
            Long areaId,
            @Size(min = 1, max = 100) String name,
            @Pattern(regexp = UserDtos.PHONE) String altPhone,
            @Size(max = 500) String address,
            @Size(max = 100) String guarantorName,
            @Pattern(regexp = UserDtos.PHONE) String guarantorPhone,
            Boolean isActive) {
    }

    /** url points to the authenticated download endpoint. */
    public record DocumentResponse(Long id, ClientDocument.DocType docType, String url, LocalDateTime uploadedAt) {
    }
}
