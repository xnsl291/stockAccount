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

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {
  private final AccountService accountService;
  private final AccountValidationService accountValidationService;

  /**
   * 계좌개설
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountManagementDto - 계좌 정보 dto (계좌별명, 계좌 PW)
   * @return 개설된 계좌
   */
  @PostMapping("/open")
  public ResponseEntity<AccountResponse> openAccount(
          @AuthenticationPrincipal Long loginMemberId,
          @Valid @RequestBody AccountManagementDto accountManagementDto){
    return ResponseEntity.ok().body(AccountResponse.from(accountService.openAccount(loginMemberId, accountManagementDto)));
  }

  /**
   * 계좌 정보 조회
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountId - 계좌 ID
   * @return 계좌
   */
  @GetMapping("/{account_id}")
  public ResponseEntity<AccountResponse> getAccountInfo(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id") Long accountId){
    accountValidationService.validAccountOwner(loginMemberId,accountId);
    return ResponseEntity.ok().body(AccountResponse.from(accountService.getAccountInfo(accountId)));
  }

  /**
   * 계좌 수정 - 현재 계좌 비밀번호가 맞아야 하고, 틀리면 틀린 횟수에 더해짐
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountId - 계좌 ID
   * @param updateAccountDto - 계좌 수정 dto (계좌별명, 현재 계좌 PW, 새 계좌 PW)
   * @return 수정된 계좌
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
   * 거래정지 계좌 잠금 해제 - 등록된 핸드폰번호로 문자 인증을 먼저 마쳐야 함
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountId - 계좌 ID
   * @return 잠금이 풀린 계좌
   */
  @PostMapping("/{account_id}/unlock")
  public ResponseEntity<AccountResponse> unlockAccount(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_id") Long accountId){
    accountValidationService.validAccountOwner(loginMemberId, accountId);
    return ResponseEntity.ok().body(AccountResponse.from(accountService.unlockAccount(accountId)));
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
    accountValidationService.validAccountOwner(loginMemberId,accountId);
    return ResponseEntity.ok().body(accountService.deleteAccount(loginMemberId, accountId));
  }

  /**
   * 로그인한 사용자가 소유한 전체계좌조회
   * @param loginMemberId - 로그인한 사용자 ID
   * @return 사용자가 소유한 계좌 리스트
   */
  @GetMapping
  public ResponseEntity<List<AccountResponse>> getAllAccounts(
          @AuthenticationPrincipal Long loginMemberId){
    return ResponseEntity.ok().body(accountService.getAllAccounts(loginMemberId).stream()
            .map(AccountResponse::from)
            .collect(Collectors.toList()));
  }

  /**
   * 계좌번호로 계좌 검색 - 해지된 계좌는 찾지 않음
   * @param loginMemberId - 로그인한 사용자 ID
   * @param accountNumber - 검색하고자 하는 계좌번호
   * @return 계좌정보
   */
  @GetMapping("/search/{account_number}")
  public ResponseEntity<SearchAccountDto> searchAccounts(
          @AuthenticationPrincipal Long loginMemberId,
          @PathVariable("account_number") String accountNumber){
    return ResponseEntity.ok().body(accountService.searchAccount(accountNumber, loginMemberId));
  }
}
