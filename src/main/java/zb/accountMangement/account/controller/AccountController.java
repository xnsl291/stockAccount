package zb.accountMangement.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.dto.AccountManagementDto;
import zb.accountMangement.account.dto.SearchAccountDto;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.common.auth.JwtTokenProvider;
import zb.accountMangement.common.service.ValidationService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {
  private final AccountService accountService;
  private final JwtTokenProvider tokenProvider;
  private final ValidationService validationService;

  /**
   * 계좌개설
   * @param token - 토큰
   * @param accountManagementDto - 계좌 정보 dto (계좌별명, 계좌 PW)
   * @return Account
   */
  @PostMapping("/open")
  public ResponseEntity<Account> openAccount(
          @RequestHeader(value = "Authorization") String token,
          @RequestBody AccountManagementDto accountManagementDto){
    return ResponseEntity.ok().body(accountService.openAccount(tokenProvider.getId(token), accountManagementDto));
  }

  /**
   * 계좌 정보 조회
   * @param token - 토큰
   * @param accountId - 계좌 ID
   * @return Account
   */
  @GetMapping("/{account_id}")
  public ResponseEntity<Account> getAccountInfo(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("account_id") Long accountId){
    validationService.validTokenNAccountOwner(token,accountId);
    return ResponseEntity.ok().body(accountService.getAccountInfo(accountId));
  }

  /**
   * 계좌 수정
   * @param token - 토큰
   * @param accountId - 계좌 ID
   * @param accountManagementDto - 계좌 정보 dto (계좌별명, 계좌 PW)
   * @return Account
   */
    @PatchMapping("/{account_id}")
    public ResponseEntity<Account> updateAccountInfo(
              @RequestHeader(value = "Authorization") String token,
              @PathVariable("account_id") Long accountId,
              @RequestBody AccountManagementDto accountManagementDto){
      validationService.validTokenNAccountOwner(token,accountId);
      return ResponseEntity.ok().body(accountService.updateAccount(accountId,accountManagementDto));
    }

  /**
   * 계좌 해지
   * @param token - 토큰
   * @param accountId - 계좌 ID
   * @return 성공여부
   */
  @DeleteMapping("/{account_id}")
  public ResponseEntity<Boolean> deleteAccountInfo(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("account_id")Long accountId){
    validationService.validTokenNAccountOwner(token,accountId);
    return ResponseEntity.ok().body(accountService.deleteAccount(accountId));
  }

  /**
   * 사용자가 소유한 전체계좌조회
   * @param token - 토큰
   * @param userId - 사용자 ID
   * @return 사용자가 소유한 계좌 리스트
   */
  @GetMapping("/{user_id}")
  public ResponseEntity<List<Account>> getAllAccounts(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("user_id")Long userId){
    validationService.validTokenNUserId(token,userId);
    return ResponseEntity.ok().body(accountService.getAllAccounts(userId));
  }

  /**
   * 계좌 검색
   * @param token - 토큰
   * @param accountId - 검색하고자 하는 계좌 ID
   * @return 계좌정보
   */
  @GetMapping("/search/{account_id}")
  public ResponseEntity<SearchAccountDto> searchAccounts(
          @RequestHeader(value = "Authorization") String token,
          @PathVariable("account_id")Long accountId){
    return ResponseEntity.ok().body(accountService.searchAccount(accountId,tokenProvider.getId(token)));
  }
}
