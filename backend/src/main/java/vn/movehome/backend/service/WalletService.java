package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.customer.wallet.TransactionDTO;
import vn.movehome.backend.dto.customer.wallet.WalletSummaryDTO;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.WalletTransaction;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Transactional
    public WalletSummaryDTO getOrCreateSummary(UUID customerId) {
        CustomerWallet wallet = walletRepository.findByCustomerId(customerId)
                .orElseGet(() -> walletRepository.save(CustomerWallet.builder()
                        .customerId(customerId)
                        .balance(BigDecimal.ZERO)
                        .totalToppedUp(BigDecimal.ZERO)
                        .totalSpent(BigDecimal.ZERO)
                        .build()));

        return new WalletSummaryDTO(
                wallet.getBalance(),
                wallet.getTotalToppedUp(),
                wallet.getTotalSpent()
        );
    }

    @Transactional(readOnly = true)
    public Page<TransactionDTO> getTransactions(UUID customerId, int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số trang không hợp lệ.");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return transactionRepository.findByUserId(customerId, pageable)
                .map(this::toTransactionDTO);
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    private TransactionDTO toTransactionDTO(WalletTransaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                null,
                transaction.getRelatedOrderId(),
                transaction.getDescription(),
                maskVnpayTxnRef(transaction.getVnpayTxnRef()),
                transaction.getCreatedAt()
        );
    }

    private String maskVnpayTxnRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
