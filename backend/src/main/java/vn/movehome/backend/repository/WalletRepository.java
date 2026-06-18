package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.movehome.backend.entity.CustomerWallet;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<CustomerWallet, UUID> {

    Optional<CustomerWallet> findByCustomerId(UUID customerId);
}
