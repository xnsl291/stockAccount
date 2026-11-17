package zb.accountMangement.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.model.entity.Transaction;
import zb.accountMangement.account.dto.DepositDto;
import zb.accountMangement.account.dto.TransactionDto;
import zb.accountMangement.account.dto.TransferDto;
import zb.accountMangement.account.dto.WithdrawalDto;
import zb.accountMangement.account.repository.TransactionRepository;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.service.MemberService;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
	private final AccountService accountService;
	private final MemberService memberService;
	private final TransactionRepository transactionRepository;

	/**
	 * 계좌 소유주 확인
	 * @param accountNumber - 계좌번호
	 * @return 사용자 이름
	 */
	public String validateRecipient(String accountNumber) {
		Account account = accountService.getAccountByNumber(accountNumber);
		if (!account.isDeletedAccount())
			return memberService.getMemberById(account.getMemberId()).getName();
		throw new CustomException(ErrorCode.DELETED_ACCOUNT);
	}

	/**
	 * 입금
	 * @param depositDto - 입금 dto (계좌 ID, 금액, 메모)
	 * @return "입금완료"
	 */
	@Transactional
	public String deposit(DepositDto depositDto) {
		changeBalance(depositDto.getAccountId(), TransactionType.DEPOSIT, depositDto.getAmount(),
				"전자입금", depositDto.getMemo());
		return "입금완료";
	}

	/**
	 * 출금
	 * @param withdrawalDto - 출금 dto (계좌 ID, 금액, 메모, 계좌 PW)
	 * @return "출금완료"
	 */
	@Transactional
	public String withdrawal(WithdrawalDto withdrawalDto) {
		changeBalance(withdrawalDto.getAccountId(), TransactionType.WITHDRAW, withdrawalDto.getAmount(),
				"전자출금", withdrawalDto.getMemo());
		return "출금완료";
	}

	/**
	 * 송금
	 * @param transferDto - 송금 dto (금액, 메모, 발신인 계좌 PW)
	 * @return "송금완료"
	 */
	@Transactional
	public String transfer(Long senderAccountId, Long receiverAccountId, TransferDto transferDto) {
		if (senderAccountId.equals(receiverAccountId))
			throw new CustomException(ErrorCode.SAME_ACCOUNT_TRANSFER);

		// 서로 반대 방향의 송금이 동시에 들어와도 교착 상태에 빠지지 않도록 항상 ID가 작은 계좌부터 잠금
		Account first = accountService.getAccountForUpdate(Math.min(senderAccountId, receiverAccountId));
		Account second = accountService.getAccountForUpdate(Math.max(senderAccountId, receiverAccountId));
		Account senderAccount = first.getId().equals(senderAccountId) ? first : second;
		Account receiverAccount = first.getId().equals(senderAccountId) ? second : first;

		Member sender = memberService.getMemberById(senderAccount.getMemberId());
		Member receiver = memberService.getMemberById(receiverAccount.getMemberId());

		apply(senderAccount, TransactionType.TRANSFER_OUT, transferDto.getAmount(), receiver.getName(), transferDto.getMemo());
		apply(receiverAccount, TransactionType.TRANSFER_IN, transferDto.getAmount(), sender.getName(), transferDto.getMemo());
		return "송금완료";
	}

	/**
	 * 계좌를 잠그고 잔액을 바꾼 뒤 거래 기록을 남김. 잔액을 바꾸는 모든 기능은 이 메서드나 송금을 거쳐야 함
	 * @param accountId - 계좌 ID
	 * @param type - 거래 종류. 잔액이 늘어나는지 줄어드는지는 거래 종류가 정함
	 * @param amount - 금액 (0보다 커야 함)
	 * @param name - 거래 대상
	 * @param memo - 메모
	 */
	@Transactional
	public void changeBalance(Long accountId, TransactionType type, long amount, String name, String memo) {
		apply(accountService.getAccountForUpdate(accountId), type, amount, name, memo);
	}

	private void apply(Account lockedAccount, TransactionType type, long amount, String name, String memo) {
		if (amount <= 0)
			throw new IllegalArgumentException("거래 금액은 0보다 커야 함: " + amount);
		if (!lockedAccount.isExistsAccount())
			throw new CustomException(ErrorCode.INVALID_ACCOUNT);

		long signedAmount = type.signed(amount);
		long balanceAfter = Math.addExact(lockedAccount.getBalance(), signedAmount);
		// 미체결 매수 주문에 묶인 금액은 출금·송금에 쓸 수 없음
		if (balanceAfter < lockedAccount.getHeldAmount())
			throw new CustomException(ErrorCode.EXCEED_BALANCE);

		lockedAccount.setBalance(balanceAfter);
		transactionRepository.save(Transaction.builder()
				.accountId(lockedAccount.getId())
				.type(type)
				.amount(signedAmount)
				.name(name)
				.memo(memo)
				.balanceAfter(balanceAfter)
				.build());
	}

	/**
	 * 거래내역 조회 - 최근 거래부터. 같은 시각의 거래는 나중에 기록된 것부터 보여 페이지를 넘겨도 순서가 흔들리지 않음
	 * @param accountId - 계좌 ID
	 * @param type - 거래 종류 (없으면 전체)
	 * @param from - 시작일, 그날 포함 (없으면 처음부터)
	 * @param to - 종료일, 그날 포함 (없으면 지금까지)
	 * @param page - 페이지 번호 (0부터)
	 * @param size - 페이지 크기
	 * @return 거래내역 한 페이지
	 */
	public Page<TransactionDto> getTransactionsByAccountId(Long accountId, TransactionType type,
			LocalDate from, LocalDate to, int page, int size) {
		if (from != null && to != null && from.isAfter(to))
			throw new CustomException(ErrorCode.INVALID_REQUEST_DATE);

		LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
		LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
		Sort sort = Sort.by(Sort.Order.desc("transactedAt"), Sort.Order.desc("id"));
		return transactionRepository.search(accountId, type, fromTime, toTime, PageRequest.of(page, size, sort))
				.map(this::mapToTransactionDto);
	}

	/**
	 * transaction 을 transactionDto로 매핑
	 * @param transaction - 거래
	 * @return TransactionDto
	 */
	private TransactionDto mapToTransactionDto(Transaction transaction) {
		return TransactionDto.builder()
				.accountId(transaction.getAccountId())
				.type(transaction.getType())
				.amount(transaction.getAmount())
				.balanceAfter(transaction.getBalanceAfter())
				.name(transaction.getName())
				.memo(transaction.getMemo())
				.transactedAt(transaction.getTransactedAt())
				.build();
	}
}
