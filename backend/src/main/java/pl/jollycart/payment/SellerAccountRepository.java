package pl.jollycart.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerAccountRepository extends JpaRepository<SellerAccount, Long> {

    Optional<SellerAccount> findByUserId(Long userId);
}