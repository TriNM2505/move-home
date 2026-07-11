package vn.movehome.backend.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.dto.admin.finance.AdminTransactionResponse;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.service.AdminTransactionService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tai hien loi 500 cua GET /api/admin/transactions bang cach chay TOAN BO service
 * (bao gom map sang DTO) tren H2 co seed du lieu.
 */
@DataJpaTest
class TransactionQueryReproTest {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private WithdrawalRequestRepository withdrawalRequestRepository;

    @Test
    void findTransactions_withSeededRow_shouldNotThrow() {
        // Giao dich khach thanh toan don — userId tro toi user khong ton tai (branch user=null)
        transactionRepository.saveAndFlush(Transaction.builder()
                .userId(UUID.randomUUID())
                .type(TransactionType.ORDER_PAYMENT)
                .amount(new BigDecimal("1500000"))
                .description("Test payment")
                .build());

        // Giao dich rut tien co balance_after (branch WITHDRAWAL, amount am)
        transactionRepository.saveAndFlush(Transaction.builder()
                .userId(UUID.randomUUID())
                .type(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("-500000"))
                .balanceAfter(new BigDecimal("1000000"))
                .relatedWithdrawalId(UUID.randomUUID())
                .build());

        AdminTransactionService service = new AdminTransactionService(
                transactionRepository, userRepository, orderRepository, withdrawalRequestRepository);

        Page<AdminTransactionResponse> result =
                service.findTransactions("ALL", null, null, null, 0, 10);

        System.out.println(">>> REPRO total=" + result.getTotalElements());
        result.getContent().forEach(r -> System.out.println(">>> ITEM type=" + r.type()
                + " amount=" + r.amount() + " user=" + r.userName()));
        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
