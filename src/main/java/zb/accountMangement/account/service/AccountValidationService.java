package zb.accountMangement.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;

@Service
@RequiredArgsConstructor

public class AccountValidationService {
    private final AccountService accountService;
    private final BCryptPasswordEncoder passwordEncoder;

    public void validAccountOwner(Long loginMemberId, Long accountId){
        Account account = accountService.getAccountInfo(accountId);
        if (!account.getMemberId().equals(loginMemberId))
            throw new CustomException(ErrorCode.MISMATCH_ACCOUNT_OWNER);
    }

    /**
     * 계좌 소유자와 계좌 비밀번호 확인 - 연속으로 틀린 횟수가 한도에 이르면 계좌가 거래정지됨.
     * 일부러 느리게 만든 비밀번호 해시 비교를 계좌 잠금 밖에서 해, 같은 계좌 요청이 이 비교를 한 줄로 기다리지 않게 함.
     * 잠금은 틀린 횟수를 올릴 때와, 비교한 뒤 계좌 값이 바뀌어 다시 확인해야 할 때만 잡음
     * @param loginMemberId - 로그인한 사용자 ID
     * @param accountId - 계좌 ID
     * @param password - 입력한 계좌 비밀번호
     */
    // 이 메서드는 트랜잭션을 열지 않음. 아래에서 부르는 계좌 서비스의 메서드가 각자 짧은 트랜잭션을 엶
    public void validAccountPassword(Long loginMemberId, Long accountId, String password){
        Account account = accountService.getAccountById(accountId);
        if (!account.getMemberId().equals(loginMemberId))
            throw new CustomException(ErrorCode.MISMATCH_ACCOUNT_OWNER);
        accountService.checkTradable(account);

        if (!passwordEncoder.matches(password, account.getPassword())) {
            accountService.recordPasswordFailure(accountId, account.getPassword(), password);
            return;
        }
        if (!accountService.confirmPasswordMatch(accountId, account))
            accountService.checkPasswordLocked(accountId, password);
    }
}
