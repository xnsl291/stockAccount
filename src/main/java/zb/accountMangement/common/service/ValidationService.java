package zb.accountMangement.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.common.auth.JwtTokenProvider;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor

public class ValidationService {
    private final AccountService accountService;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider tokenProvider;

    public void validTokenNAccountOwner(String token, Long accountId){
        Account account = accountService.getAccountInfo(accountId);
        Long userId = tokenProvider.getId(token);
        if (!account.getMemberId().equals(userId))
            throw new CustomException(ErrorCode.MISMATCH_ACCOUNT_OWNER);
    }
    public void validTokenNMemberId(String token, Long userId){
        Long tokenProviderId = tokenProvider.getId(token);
        if (!tokenProviderId.equals(userId))
            throw new CustomException(ErrorCode.MISMATCHED_USER_ID);
    }

    public void validTokenNMemberPhoneNumber(String token, String phoneNum){
        Long userId = memberRepository.findByPhoneNumber(phoneNum)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_EXIST)).getId();
        validTokenNMemberId(token, userId);
    }
}
