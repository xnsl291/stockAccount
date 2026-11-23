package zb.accountMangement.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.dto.AccountManagementDto;
import zb.accountMangement.account.dto.SearchAccountDto;
import zb.accountMangement.account.dto.UpdateAccountDto;
import zb.accountMangement.account.repository.AccountRepository;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.exception.UniqueConstraint;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.VerificationPurpose;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.service.MemberService;
import zb.accountMangement.member.service.SendMessageService;
import zb.accountMangement.stock.repository.StockBalanceRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StockBalanceRepository stockBalanceRepository;
    private final SendMessageService sendMessageService;
    private final MemberService memberService;
    private final AccountNumberGenerator accountNumberGenerator;

    public Account getAccountById(Long accountId){
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_EXIST));
    }

    /**
     * 트랜잭션 안에서 호출해야 함
     */
    public Account getAccountForUpdate(Long accountId){
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_EXIST));
    }

    /**
     * 계좌를 잠근 뒤 상태를 다시 봄.
     * 이미 거래정지면 횟수를 올리지 않고 거래정지로 거절하므로, 틀린 요청이 동시에 몇 건 몰려도 "틀림" 응답은 한도만큼만 나감
     */
    // 틀린 횟수는 예외로 끝나도 저장되어야 하므로 롤백하지 않음
    @Transactional(noRollbackFor = CustomException.class)
    public void recordPasswordFailure(Long accountId, String comparedHash, String password) {
        Account account = getAccountForUpdate(accountId);
        checkTradable(account);
        // 비교한 뒤에 비밀번호가 바뀌었으면 새 비밀번호와 다시 비교함. 바꾼 직후 새 비밀번호로 보낸 요청을 틀렸다고 세지 않기 위함
        if (!account.getPassword().equals(comparedHash) && passwordEncoder.matches(password, account.getPassword())) {
            account.resetPasswordFailure();
            return;
        }
        account.recordPasswordFailure();
        throw new CustomException(ErrorCode.UNMATCHED_PASSWORD);
    }

    /**
     * 잠금 밖에서 맞았다고 비교된 계좌 비밀번호를 확정함.
     * 그사이 비밀번호가 바뀌었거나 남은 틀린 횟수를 0으로 되돌리지 못했으면 false이고, 잠금 안에서 다시 확인해야 함.
     * 그사이 오른 틀린 횟수나 거래정지는 여기서 보지 않음. 이 요청을 그보다 먼저 처리한 것과 결과가 같고,
     * 거래정지는 실행 단계가 계좌를 잠근 뒤 틀린 요청과 같은 오류 코드로 막음
     */
    @Transactional
    public boolean confirmPasswordMatch(Long accountId, Account compared) {
        // 비밀번호 변경이 커밋된 뒤에 옛 비밀번호로 비교를 마친 요청을 통과시키지 않기 위함
        if (!getAccountById(accountId).getPassword().equals(compared.getPassword()))
            return false;
        // 평소(횟수 0)에는 쓰지 않음. 0으로 되돌리지 못했으면 다시 읽은 뒤에 틀린 요청이 커밋된 것이라 잠금 안에서 다시 확인함.
        // 그냥 통과시키면 틀린 횟수가 남아, 어느 순서로 처리해도 나올 수 없는 값이 됨
        if (compared.getPasswordFailCount() != 0)
            return accountRepository.resetPasswordFailCount(accountId, compared.getPasswordFailCount(), AccountStatus.EXISTS) == 1;
        return true;
    }

    /**
     * 잠금 밖 비교 뒤에 계좌 값이 바뀌었을 때만 씀
     */
    // 틀린 횟수는 예외로 끝나도 저장되어야 하므로 롤백하지 않음
    @Transactional(noRollbackFor = CustomException.class)
    public void checkPasswordLocked(Long accountId, String password) {
        Account account = getAccountForUpdate(accountId);
        checkTradable(account);
        if (!passwordEncoder.matches(password, account.getPassword())) {
            account.recordPasswordFailure();
            throw new CustomException(ErrorCode.UNMATCHED_PASSWORD);
        }
        account.resetPasswordFailure();
    }

    public void checkTradable(Account account) {
        if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.DELETED_ACCOUNT);
        if (!account.isExistsAccount())
            throw new CustomException(ErrorCode.PENDING_ACCOUNT);
    }

    /**
     * 실행 단계에서 계좌를 잠근 뒤 부름.
     * 틀린 요청과 같은 오류 코드로 거절해야, 동시에 여러 비밀번호를 넣어 본 쪽이 정답 요청만 다른 응답을 받아 비밀번호를 알아내지 못함.
     * 해지 등 다른 상태는 부르는 쪽의 기존 오류 코드를 그대로 씀
     */
    public void checkNotSuspended(Account lockedAccount) {
        if (lockedAccount.isSuspendedAccount())
            throw new CustomException(ErrorCode.PENDING_ACCOUNT);
    }

    public Account getAccountByNumber(String accountNumber){
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_EXIST));
    }

    /**
     * 탈퇴·정지 회원은 개설할 수 없음
     */
    @Transactional
    public Account openAccount(Long memberId, AccountManagementDto accountManagementDto) {
        // 탈퇴와 겹치면 탈퇴가 읽은 계좌 목록에 새 계좌가 빠져 살아 남으므로, 탈퇴와 같은 회원 행 잠금을 먼저 잡음
        memberService.checkActive(memberService.getMemberForUpdate(memberId));

        String accountNumber = accountNumberGenerator.generate();

        while(accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            accountNumber = accountNumberGenerator.generate();
        }

        Account account = Account.builder()
              .accountNumber(accountNumber)
              .memberId(memberId)
              .nickname(accountManagementDto.getNickname())
              .password(passwordEncoder.encode(accountManagementDto.getPassword()))
              .status(AccountStatus.EXISTS)
              .build();

        // 위의 조회는 아직 커밋되지 않은 같은 번호의 계좌를 보지 못함. 그런 계좌가 먼저 커밋되면 저장이 유니크 제약에 걸리므로
        // 여기서 잡아 다시 시도하면 되는 오류로 알림. 같은 트랜잭션 안에서 새 번호로 다시 저장하지 않는 것은,
        // 유니크 위반이 난 트랜잭션은 롤백만 할 수 있어 이어서 저장할 수 없기 때문임
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            if (UniqueConstraint.isViolated(e, "uk_account_account_number"))
                throw new CustomException(ErrorCode.DUPLICATED_ACCOUNT_NUMBER);
            throw e;
        }
        return account;
    }

    public Account getAccountInfo(Long accountId) {
        Account account = getAccountById(accountId);
        if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.DELETED_ACCOUNT);
        return account;
    }

    /**
     * 현재 비밀번호 확인은 호출하는 쪽에서 먼저 해야 함
     */
    @Transactional
    public Account updateAccount(Long accountId, UpdateAccountDto updateAccountDto) {
        // 저장할 때 잔액·상태까지 모든 칸을 다시 쓰므로, 잠그지 않고 읽으면 그사이 커밋된 입금·해지를 읽은 시점 값으로 되돌림
        Account account = getAccountForUpdate(accountId);

        if (account.isExistsAccount()) {
            if (updateAccountDto.getNickname() != null)
                account.setNickname(updateAccountDto.getNickname());
            if (updateAccountDto.getNewPassword() != null)
                account.setPassword(passwordEncoder.encode(updateAccountDto.getNewPassword()));
        }
        else if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.DELETED_ACCOUNT);
        else
            throw new CustomException(ErrorCode.PENDING_ACCOUNT);

        return account;
    }

    /**
     * 비밀번호를 연속으로 틀려 거래정지된 계좌의 잠금 해제.
     * 회원에 등록된 핸드폰번호로 문자 인증(/api/auth/send-code, /api/auth/verify-phone)을 마친 뒤에만 풀 수 있음
     */
    @Transactional
    public Account unlockAccount(Long accountId) {
        Account account = getAccountForUpdate(accountId);
        if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.DELETED_ACCOUNT);

        Member owner = memberService.getMemberById(account.getMemberId());
        sendMessageService.consumeVerifiedPhone(owner.getPhoneNumber(), VerificationPurpose.ACCOUNT_UNLOCK);
        account.unlock();
        return account;
    }

    /**
     * 거래정지된 계좌, 마지막 남은 계좌, 잔액이나 보유 주식이 남은 계좌는 해지할 수 없음
     */
    @Transactional
    public void deleteAccount(Long memberId, Long accountId) {
        // 같은 회원의 계좌 두 개를 동시에 해지하면 둘 다 "2개 이상"을 보고 해지할 수 있어, 회원 행을 먼저 잠가 한 줄로 세움.
        // 잠금 순서는 탈퇴·계좌 개설과 같은 회원 → 계좌이고, 계좌 수는 회원 잠금을 얻은 뒤에 세어야 앞선 해지가 보임
        memberService.getMemberForUpdate(memberId);
        Account account = getAccountForUpdate(accountId);
        if (!account.getMemberId().equals(memberId))
            throw new CustomException(ErrorCode.MISMATCH_ACCOUNT_OWNER);
        if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.DELETED_ACCOUNT);
        if (!account.isExistsAccount())
            throw new CustomException(ErrorCode.PENDING_ACCOUNT);
        // 해지된 계좌는 빼고 세어야 마지막 남은 계좌를 지킬 수 있음
        if (getAllAccounts(memberId).size() < 2)
            throw new CustomException(ErrorCode.LAST_ACCOUNT);

        checkNoAssets(account);
        account.close();
    }

    /**
     * 잔액이나 보유 주식이 남은 계좌가 하나라도 있으면 해지하지 않음
     */
    @Transactional
    public void closeAllAccounts(Long memberId) {
        // 송금과 같은 순서(ID 오름차순)로 잠가야 동시에 실행돼도 교착 상태에 빠지지 않음
        for (Long accountId : accountRepository.findIdsByMemberId(memberId)) {
            Account account = getAccountForUpdate(accountId);
            if (account.isDeletedAccount())
                continue;
            checkNoAssets(account);
            account.close();
        }
    }

    // 주식 서비스가 이미 이 서비스를 주입받고 있어 주식 저장소를 직접 부름. 계좌 모듈이 주식 모듈을 부르는 유일한 자리임
    private void checkNoAssets(Account account) {
        if (account.getBalance() != 0 || !stockBalanceRepository.findByAccountIdForUpdate(account.getId()).isEmpty())
            throw new CustomException(ErrorCode.REMAINING_ASSET);
    }

    public List<Account> getAllAccounts(Long memberId){
        return accountRepository.findByMemberIdAndStatusNot(memberId, AccountStatus.DELETED);
    }

    /**
     * 해지된 계좌는 없는 계좌로 봄
     */
    public SearchAccountDto searchAccount(String accountNumber, Long requesterId) {
        Account account = getAccountByNumber(accountNumber);
        if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.ACCOUNT_NOT_EXIST);
        String username = memberService.getMemberById(account.getMemberId()).getName();

        if (account.getMemberId().equals(requesterId)) {
            return SearchAccountDto.builder()
                    .accountId(account.getId())
                    .accountNumber(account.getAccountNumber())
                    .ownerName(username)
                    .accountNickname(account.getNickname())
                    .balance(account.getBalance())
                    .status(account.getStatus())
                    .build();
        }
        else {
            return SearchAccountDto.builder()
                    .accountId(account.getId())
                    .accountNumber(account.getAccountNumber())
                    .ownerName(username)
                    .build();
        }
    }
}
