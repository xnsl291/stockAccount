package zb.accountMangement.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;

@Service
@RequiredArgsConstructor

public class ValidationService {
    private final AccountService accountService;

    public void validAccountOwner(Long loginMemberId, Long accountId){
        Account account = accountService.getAccountInfo(accountId);
        if (!account.getMemberId().equals(loginMemberId))
            throw new CustomException(ErrorCode.MISMATCH_ACCOUNT_OWNER);
    }

    public void validMemberId(Long loginMemberId, Long memberId){
        if (!loginMemberId.equals(memberId))
            throw new CustomException(ErrorCode.MISMATCHED_USER_ID);
    }
}
