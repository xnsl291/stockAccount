package zb.accountMangement.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.dto.AccountManagementDto;
import zb.accountMangement.account.dto.SearchAccountDto;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.common.service.ValidationService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {
  private final AccountService accountService;
  private final ValidationService validationService;

  /**
   * 계좌개설
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountManagementDto - 계좌 정보 dto (계좌별명, 계좌 PW)
   * @return Account
   */
  @PostMapping("/open")
  public ResponseEntity<Account> openAccount(
          @AuthenticationPrincipal Long loginMemberId,
          @RequestBody AccountManagementDto accountManagementDto){
    return ResponseEntity.ok().body(accountService.openAccount(loginMemberId, accountManagementDto));
  }

  /**
   * 계좌 정보 조회
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountId - 계좌 ID
   * @return Account
   */
  @GetMapping("/{account_id}")
  public ResponseEntity<Account> getAccountInfo(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id") Long accountId){
    validationService.validAccountOwner(loginMemberId,accountId);
    return ResponseEntity.ok().body(accountService.getAccountInfo(accountId));
  }

  /**
   * 계좌 수정
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountId - 계좌 ID
   * @param accountManagementDto - 계좌 정보 dto (계좌별명, 계좌 PW)
   * @return Account
   */
    @PatchMapping("/{account_id}")
    public ResponseEntity<Account> updateAccountInfo(
              @AuthenticationPrincipal Long loginMemberId,
              @PathVariable("account_id") Long accountId,
              @RequestBody AccountManagementDto accountManagementDto){
      validationService.validAccountOwner(loginMemberId,accountId);
      return ResponseEntity.ok().body(accountService.updateAccount(accountId,accountManagementDto));
    }

  /**
   * 계좌 해지
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountId - 계좌 ID
   * @return 성공여부
   */
  @DeleteMapping("/{account_id}")
  public ResponseEntity<Boolean> deleteAccountInfo(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id")Long accountId){
    validationService.validAccountOwner(loginMemberId,accountId);
    return ResponseEntity.ok().body(accountService.deleteAccount(accountId));
  }

  /**
   * 로그인한 사용자가 소유한 전체계좌조회
   * @param loginMemberId - 로그인한 사용자 ID
   * @return 사용자가 소유한 계좌 리스트
   */
  @GetMapping
  public ResponseEntity<List<Account>> getAllAccounts(
          @AuthenticationPrincipal Long loginMemberId){
    return ResponseEntity.ok().body(accountService.getAllAccounts(loginMemberId));
  }

  /**
   * 계좌 검색
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountId - 검색하고자 하는 계좌 ID
   * @return 계좌정보
   */
  @GetMapping("/search/{account_id}")
  public ResponseEntity<SearchAccountDto> searchAccounts(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id")Long accountId){
    return ResponseEntity.ok().body(accountService.searchAccount(accountId,loginMemberId));
  }
}
