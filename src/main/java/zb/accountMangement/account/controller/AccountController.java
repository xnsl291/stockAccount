package zb.accountMangement.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import zb.accountMangement.account.dto.AccountManagementDto;
import zb.accountMangement.account.dto.AccountResponse;
import zb.accountMangement.account.dto.SearchAccountDto;
import zb.accountMangement.account.dto.UpdateAccountDto;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.account.service.AccountValidationService;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {
  private final AccountService accountService;
  private final AccountValidationService accountValidationService;

  @PostMapping("/open")
  public ResponseEntity<AccountResponse> openAccount(
          @AuthenticationPrincipal Long loginMemberId,
          @Valid @RequestBody AccountManagementDto accountManagementDto){
    return ResponseEntity.ok().body(AccountResponse.from(accountService.openAccount(loginMemberId, accountManagementDto)));
  }

  @GetMapping("/{account_id}")
  public ResponseEntity<AccountResponse> getAccountInfo(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id") Long accountId){
    return ResponseEntity.ok().body(AccountResponse.from(accountValidationService.validAccountOwner(loginMemberId, accountId)));
  }

  /**
   * 현재 계좌 비밀번호가 맞아야 하고, 틀리면 틀린 횟수에 더해짐
   */
  @PatchMapping("/{account_id}")
  public ResponseEntity<AccountResponse> updateAccountInfo(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id") Long accountId,
          @Valid @RequestBody UpdateAccountDto updateAccountDto){
    accountValidationService.validAccountPassword(loginMemberId, accountId, updateAccountDto.getCurrentPassword());
    return ResponseEntity.ok().body(AccountResponse.from(accountService.updateAccount(accountId, updateAccountDto)));
  }

  /**
   * 등록된 핸드폰번호로 문자 인증을 먼저 마쳐야 함
   */
  @PostMapping("/{account_id}/unlock")
  public ResponseEntity<AccountResponse> unlockAccount(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id") Long accountId){
    accountValidationService.validAccountOwner(loginMemberId, accountId);
    return ResponseEntity.ok().body(AccountResponse.from(accountService.unlockAccount(accountId)));
  }

  @DeleteMapping("/{account_id}")
  public ResponseEntity<Boolean> deleteAccountInfo(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id")Long accountId){
    accountValidationService.validAccountOwner(loginMemberId,accountId);
    accountService.deleteAccount(loginMemberId, accountId);
    return ResponseEntity.ok().body(true);
  }

  @GetMapping
  public ResponseEntity<List<AccountResponse>> getAllAccounts(
          @AuthenticationPrincipal Long loginMemberId){
    return ResponseEntity.ok().body(accountService.getAllAccounts(loginMemberId).stream()
            .map(AccountResponse::from)
            .collect(Collectors.toList()));
  }

  /**
   * 해지된 계좌는 찾지 않음
   */
  @GetMapping("/search/{account_number}")
  public ResponseEntity<SearchAccountDto> searchAccounts(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_number") String accountNumber){
    return ResponseEntity.ok().body(accountService.searchAccount(accountNumber, loginMemberId));
  }
}
