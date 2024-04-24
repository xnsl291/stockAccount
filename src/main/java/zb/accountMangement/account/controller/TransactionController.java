package zb.accountMangement.account.controller;

import lombok.RequiredArgsConstructor;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.account.dto.TransactionDto;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.common.service.ValidationService;

import javax.validation.Valid;
import javax.validation.constraints.Min;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;
    private final ValidationService validationService;

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
    * @param depositDto - 계좌거래 dto (계좌ID, 이름, 금액, 메모, 거래일시)
    * @return "입금완료"
    */
    @PostMapping("/deposit")
    public ResponseEntity<String> deposit(
          @Valid @RequestBody TransactionDto depositDto) {
    return ResponseEntity.ok().body(transactionService.deposit(depositDto));
    }

    /**
    * 출금
    * @param withdrawalDto - 계좌거래 dto (계좌ID, 이름, 금액, 메모, 거래일시)
    * @return "출금완료"
    */
    @PostMapping("/withdrawal")
    public ResponseEntity<String> withdrawal(@Valid @RequestBody TransactionDto withdrawalDto) {
        return ResponseEntity.ok().body(transactionService.withdrawal(withdrawalDto));
    }

    /**
    * 송금
    * @param senderAccountId - 발신인 계좌 ID
    * @param receiverAccountId - 수신인 계좌 ID
    * @param transferDto - 계좌거래 dto (계좌ID, 이름, 금액, 메모, 거래일시)
    * @return "송금완료"
    */
    @PostMapping("{sender_account_id}/transfer/{receiver_account_id}")
    public ResponseEntity<String> transfer(
            @PathVariable("sender_account_id") @Min(1) Long senderAccountId ,
            @PathVariable("receiver_account_id") @Min(1) Long receiverAccountId,
            @Valid @RequestBody TransactionDto transferDto) {
	    return ResponseEntity.ok().body(transactionService.transfer(senderAccountId, receiverAccountId, transferDto));
    }
    /**
     * 거래내역 조회
     * @param token - 토큰
     * @param accountId - 계좌 ID
     * @return 거래내역 리스트
     */
    @GetMapping("/{accountId}/history")
    public ResponseEntity<List<TransactionDto>> getTransactionsByAccountId(
            @RequestHeader(value = "Authorization") String token,
            @PathVariable @Min(1) Long accountId) {
      validationService.validTokenNAccountOwner(token,accountId);
      return ResponseEntity.ok(transactionService.getTransactionsByAccountId(accountId));
    }
}
