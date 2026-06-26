package vn.movehome.backend.dto.admin.finance;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

public record ProcessWithdrawalRequest(
        @JsonAlias({"bank_txn_ref", "bankTxnRef"})
        String bankTxnRef,

        @JsonAlias({"processing_note", "processingNote"})
        @Size(max = 500, message = "Ghi chu xu ly toi da 500 ky tu.")
        String processingNote
) {
}
