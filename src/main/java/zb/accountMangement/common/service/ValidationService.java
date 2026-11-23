package zb.accountMangement.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;

@Service
@RequiredArgsConstructor

public class ValidationService {
    private final AccountService accountService;
    private final BCryptPasswordEncoder passwordEncoder;

    public void validAccountOwner(Long loginMemberId, Long accountId){
        Account account = accountService.getAccountInfo(accountId);
        if (!account.getMemberId().equals(loginMemberId))
            throw new CustomException(ErrorCode.MISMATCH_ACCOUNT_OWNER);
    }

    /**
     * 계좌 소유자와 계좌 비밀번호 확인 - 연속으로 틀린 횟수가 한도에 이르면 계좌가 거래정지됨
     * @param loginMemberId - 로그인한 사용자 ID
     * @param accountId - 계좌 ID
     * @param password - 입력한 계좌 비밀번호
     */
    // 비밀번호가 틀려 예외가 나도 틀린 횟수는 저장되어야 하므로 롤백하지 않음
    @Transactional(noRollbackFor = CustomException.class)
    public void validAccountPassword(Long loginMemberId, Long accountId, String password){
        Account account = accountService.getAccountForUpdate(accountId);
        if (!account.getMemberId().equals(loginMemberId))
            throw new CustomException(ErrorCode.MISMATCH_ACCOUNT_OWNER);
        if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.DELETED_ACCOUNT);
        if (!account.isExistsAccount())
            throw new CustomException(ErrorCode.PENDING_ACCOUNT);

        if (!passwordEncoder.matches(password, account.getPassword())) {
            account.recordPasswordFailure();
            throw new CustomException(ErrorCode.UNMATCHED_PASSWORD);
        }
        account.resetPasswordFailure();
    }

    public void validMemberId(Long loginMemberId, Long memberId){
        if (!loginMemberId.equals(memberId))
            throw new CustomException(ErrorCode.MISMATCHED_USER_ID);
    }
}
