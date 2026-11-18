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

    /**
     * id를 이용한 계좌정보 열람
     * @param accountId - 계좌 ID
     * @return Account
     */
    public Account getAccountById(Long accountId){
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_EXIST));
    }

    /**
     * 다른 요청이 같은 계좌를 동시에 바꾸지 못하게 잠그고 계좌정보 열람 - 트랜잭션 안에서 호출해야 함
     * @param accountId - 계좌 ID
     * @return Account
     */
    public Account getAccountForUpdate(Long accountId){
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_EXIST));
    }

    /**
     * 계좌번호를 이용한 계좌정보 열람
     * @param accountNumber - 계좌번호
     * @return Account
     */
    public Account getAccountByNumber(String accountNumber){
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_EXIST));
    }

    /**
     * 계좌 개설 - 탈퇴·정지 회원은 개설할 수 없음
     * @param memberId - 사용자 ID
     * @param accountManagementDto - 계좌 정보 dto (계좌별명, 계좌 PW)
     * @return Account
     */
    @Transactional
    public Account openAccount(Long memberId, AccountManagementDto accountManagementDto) {
        // 탈퇴와 겹치면 탈퇴가 읽은 계좌 목록에 새 계좌가 빠져 살아 남으므로, 탈퇴와 같은 회원 행 잠금을 먼저 잡음
        memberService.checkActive(memberService.getMemberForUpdate(memberId));

        // 계좌번호 랜덤생성 - 중복 생성 x
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

    /**
     * 계좌 정보 조회 - accountStatus가 EXISTS, PENDING인 계좌 조회 가능
     * @param accountId - 계좌 ID
     * @return Account
     */
    public Account getAccountInfo(Long accountId) {
        Account account = getAccountById(accountId);
        if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.DELETED_ACCOUNT);
        return account;
    }

    /**
     * 계좌 정보 수정 - 현재 비밀번호 확인은 호출하는 쪽에서 먼저 해야 함
     * @param accountId - 계좌 ID
     * @param updateAccountDto - 계좌 수정 dto (계좌별명, 새 계좌 PW). 비어 있는 항목은 그대로 둠
     * @return Account
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
     * @param accountId - 계좌 ID
     * @return Account
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
     * 계좌 해지 - 거래정지된 계좌, 마지막 남은 계좌, 잔액이나 보유 주식이 남은 계좌는 해지할 수 없음
     * @param memberId - 계좌 소유자 ID. 호출하는 쪽에서 소유자인지 먼저 확인해야 함
     * @param accountId - 계좌 ID
     * @return true
     */
    @Transactional
    public Boolean deleteAccount(Long memberId, Long accountId) {
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
        return true;
    }

    /**
     * 회원의 모든 계좌 해지 - 잔액이나 보유 주식이 남은 계좌가 하나라도 있으면 해지하지 않음
     * @param memberId - 사용자 ID
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

    // 계좌 모듈이 주식 모듈의 저장소를 직접 부르는 유일한 자리임
    // StockService·OrderService가 이미 AccountService를 주입받고 있어, 여기서 주식 서비스를 주입받으면 빈이 서로 물림
    // 해지·탈퇴는 계좌를 잠근 같은 트랜잭션 안에서 보유 주식 행까지 잠가 확인해야 하므로 위 모듈로 올리지도 않음
    // 저장소 호출 한 줄을 감싸는 조회 전용 서비스는 경계 방향을 바꾸지 못해 두지 않음
    // 계좌 모듈이 주식 모듈을 부르는 곳이 하나 더 생기면 이 구조를 다시 정해야 함
    private void checkNoAssets(Account account) {
        if (account.getBalance() != 0 || !stockBalanceRepository.findByAccountIdForUpdate(account.getId()).isEmpty())
            throw new CustomException(ErrorCode.REMAINING_ASSET);
    }

    /**
     * 회원의 계좌 목록 - 해지된 계좌는 뺌
     * @param memberId - 사용자 ID
     * @return 계좌 목록
     */
    public List<Account> getAllAccounts(Long memberId){
        return accountRepository.findByMemberIdAndStatusNot(memberId, AccountStatus.DELETED);
    }

    /**
     * 계좌번호로 계좌 검색 - 해지된 계좌는 없는 계좌로 봄
     * @param accountNumber - 계좌번호
     * @param requesterId - 요구자 ID
     * @return 계좌정보. 남의 계좌면 계좌 ID·계좌번호·소유주 이름만
     */
    public SearchAccountDto searchAccount(String accountNumber, Long requesterId) {
        Account account = getAccountByNumber(accountNumber);
        if (account.isDeletedAccount())
            throw new CustomException(ErrorCode.ACCOUNT_NOT_EXIST);
        String username = memberService.getMemberById(account.getMemberId()).getName();

        // 본인 계좌
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
        // 타인의 계좌
        else {
            return SearchAccountDto.builder()
                    .accountId(account.getId())
                    .accountNumber(account.getAccountNumber())
                    .ownerName(username)
                    .build();
        }
    }
}
