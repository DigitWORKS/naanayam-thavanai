package tn.digitworks.naanayamthavanai.preclosure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanPreclosureRepository extends JpaRepository<LoanPreclosure, Long> {

    Optional<LoanPreclosure> findByPaymentId(Long paymentId);
}
