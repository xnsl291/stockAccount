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
import zb.accountMangement.account.model.entity.IdempotencyRecord;
import zb.accountMangement.account.service.IdempotencyService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.account.service.AccountValidationService;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/transactions")
public class TransactionController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TransactionService transactionService;
    private final AccountValidationService accountValidationService;
    private final IdempotencyService idempotencyService;

    /**
    * 계좌 소유주 확인
    * @param accountNumber - 계좌번호
    * @return 사용자이름
    */
    @GetMapping("/validation/{account_number}")
    public ResponseEntity<String> validateRecipient(
          @PathVariable("account_number")String accountNumber) {
    return ResponseEntity.ok().body(transactionService.validateRecipient(accountNumber));
    }

    /**
    * 입금
    * @param loginMemberId - 로그인한 사용자 ID
    * @param idempotencyKey - 요청마다 새로 만든 키. 같은 키로 다시 보내면 처음 결과만 돌려줌
    * @param depositDto - 입금 dto (계좌ID, 금액, 메모)
    * @return "입금완료"
    */
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

    /**
    * 출금
    * @param loginMemberId - 로그인한 사용자 ID
    * @param idempotencyKey - 요청마다 새로 만든 키. 같은 키로 다시 보내면 처음 결과만 돌려줌
    * @param withdrawalDto - 출금 dto (계좌ID, 금액, 메모, 계좌 PW)
    * @return "출금완료"
    */
    @PostMapping("/withdrawal")
    public ResponseEntity<String> withdrawal(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @Valid @RequestBody WithdrawalDto withdrawalDto) {
        accountValidationService.validAccountPassword(loginMemberId, withdrawalDto.getAccountId(), withdrawalDto.getAccountPassword());
        return ResponseEntity.ok().body(idempotencyService.execute(loginMemberId, idempotencyKey,
                "WITHDRAW " + withdrawalDto.getAccountId() + " " + withdrawalDto.getAmount(),
                () -> transactionService.withdrawal(withdrawalDto)));
    }

    /**
    * 송금
    * @param loginMemberId - 로그인한 사용자 ID
    * @param idempotencyKey - 요청마다 새로 만든 키. 같은 키로 다시 보내면 처음 결과만 돌려줌
    * @param senderAccountId - 발신인 계좌 ID
    * @param receiverAccountId - 수신인 계좌 ID
    * @param transferDto - 송금 dto (금액, 메모, 발신인 계좌 PW)
    * @return "송금완료"
    */
    @PostMapping("{sender_account_id}/transfer/{receiver_account_id}")
    public ResponseEntity<String> transfer(
            @AuthenticationPrincipal Long loginMemberId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = IdempotencyRecord.KEY_MAX_LENGTH) String idempotencyKey,
            @PathVariable("sender_account_id") @Min(1) Long senderAccountId ,
            @PathVariable("receiver_account_id") @Min(1) Long receiverAccountId,
            @Valid @RequestBody TransferDto transferDto) {
        accountValidationService.validAccountPassword(loginMemberId, senderAccountId, transferDto.getAccountPassword());
        return ResponseEntity.ok().body(idempotencyService.execute(loginMemberId, idempotencyKey,
                "TRANSFER " + senderAccountId + " " + receiverAccountId + " " + transferDto.getAmount(),
                () -> transactionService.transfer(senderAccountId, receiverAccountId, transferDto)));
    }
    /**
     * 거래내역 조회 - 최근 거래부터 페이지 단위로
     * @param loginMemberId - 로그인한 사용자 ID
     * @param accountId - 계좌 ID
     * @param type - 거래 종류 (없으면 전체)
     * @param from - 시작일 yyyy-MM-dd, 그날 포함 (없으면 처음부터)
     * @param to - 종료일 yyyy-MM-dd, 그날 포함 (없으면 지금까지)
     * @param page - 페이지 번호 (0부터)
     * @param size - 페이지 크기 (최대 100)
     * @return 거래내역 한 페이지
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
