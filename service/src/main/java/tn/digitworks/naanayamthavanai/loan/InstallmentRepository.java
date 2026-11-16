package tn.digitworks.naanayamthavanai.loan;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    List<Installment> findByLoanIdOrderBySeqNo(Long loanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Installment i where i.loanId = :loanId order by i.seqNo")
    List<Installment> lockByLoanId(Long loanId);

    List<Installment> findByLoanIdIn(Collection<Long> loanIds);
}
