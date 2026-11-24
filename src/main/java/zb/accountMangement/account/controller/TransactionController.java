package zb.accountMangement.account.controller;

import lombok.RequiredArgsConstructor;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.account.dto.DepositDto;
import zb.accountMangement.account.dto.TransactionDto;
import zb.accountMangement.account.dto.TransferDto;
import zb.accountMangement.account.dto.WithdrawalDto;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.idempotency.model.entity.IdempotencyRecord;
import zb.accountMangement.idempotency.service.IdempotencyService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.account.service.AccountValidationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/transactions")
public class TransactionController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TransactionService transactionService;
    private final AccountValidationService accountValidationService;
    private final IdempotencyService idempotencyService;

    @GetMapping("/validation/{account_number}")
    public ResponseEntity<String> validateRecipient(
          @PathVariable("account_number")String accountNumber) {
    return ResponseEntity.ok().body(transactionService.validateRecipient(accountNumber));
    }

    @PostMapping("/deposit")
    public ResponseEntity<String> deposit(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody DepositDto depositDto) {
        accountValidationService.validAccountOwner(loginMemberId, depositDto.getAccountId());
        return ResponseEntity.ok().body(idempotencyService.execute(loginMemberId, idempotencyKey,
                "DEPOSIT " + depositDto.getAccountId() + " " + depositDto.getAmount(),
                () -> transactionService.deposit(depositDto)));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<String> withdrawal(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody WithdrawalDto withdrawalDto) {
        String request = "WITHDRAW " + withdrawalDto.getAccountId() + " " + withdrawalDto.getAmount();
        return ResponseEntity.ok().body(idempotencyService.execute(loginMemberId, idempotencyKey, request,
                () -> accountValidationService.validAccountPassword(loginMemberId, withdrawalDto.getAccountId(), withdrawalDto.getAccountPassword()),
                () -> transactionService.withdrawal(withdrawalDto)));
    }

    @PostMapping("{sender_account_id}/transfer/{receiver_account_id}")
    public ResponseEntity<String> transfer(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @PathVariable("sender_account_id") @Min(1) Long senderAccountId ,
            @PathVariable("receiver_account_id") @Min(1) Long receiverAccountId,
            @Valid @RequestBody TransferDto transferDto) {
        String request = "TRANSFER " + senderAccountId + " " + receiverAccountId + " " + transferDto.getAmount();
        return ResponseEntity.ok().body(idempotencyService.execute(loginMemberId, idempotencyKey, request,
                () -> accountValidationService.validAccountPassword(loginMemberId, senderAccountId, transferDto.getAccountPassword()),
                () -> transactionService.transfer(senderAccountId, receiverAccountId, transferDto)));
    }
    /**
     * from·to는 그날을 포함하고, 비우면 기간을 제한하지 않음
     */
    @GetMapping("/{accountId}/history")
    public ResponseEntity<Page<TransactionDto>> getTransactionsByAccountId(
            @AuthenticationPrincipal Long loginMemberId,
            @PathVariable @Min(1) Long accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
      accountValidationService.validAccountOwner(loginMemberId,accountId);
      return ResponseEntity.ok(transactionService.getTransactionsByAccountId(accountId, type, from, to, page, size));
    }
}
