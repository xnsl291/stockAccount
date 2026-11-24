package zb.accountMangement.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.model.entity.SystemEntry;
import zb.accountMangement.account.model.entity.Transaction;
import zb.accountMangement.account.dto.DepositDto;
import zb.accountMangement.account.dto.TransactionDto;
import zb.accountMangement.account.dto.TransferDto;
import zb.accountMangement.account.dto.WithdrawalDto;
import zb.accountMangement.account.repository.SystemEntryRepository;
import zb.accountMangement.account.repository.TransactionRepository;
import zb.accountMangement.account.model.TransactionCause;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.service.MemberService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
	private final AccountService accountService;
	private final MemberService memberService;
	private final TransactionRepository transactionRepository;
	private final SystemEntryRepository systemEntryRepository;

	public String validateRecipient(String accountNumber) {
		Account account = accountService.getAccountByNumber(accountNumber);
		if (!account.isDeletedAccount())
			return memberService.getMemberById(account.getMemberId()).getName();
		throw new CustomException(ErrorCode.DELETED_ACCOUNT);
	}

	@Transactional
	public String deposit(DepositDto depositDto) {
		changeBalance(accountService.getAccountForUpdate(depositDto.getAccountId()), TransactionType.DEPOSIT, depositDto.getAmount(),
				"전자입금", depositDto.getMemo(), TransactionCause.NONE);
		return "입금완료";
	}

	@Transactional
	public String withdrawal(WithdrawalDto withdrawalDto) {
		Account account = accountService.getAccountForUpdate(withdrawalDto.getAccountId());
		accountService.checkNotSuspended(account);
		changeBalance(account, TransactionType.WITHDRAW, withdrawalDto.getAmount(), "전자출금", withdrawalDto.getMemo(), TransactionCause.NONE);
		return "출금완료";
	}

	@Transactional
	public String transfer(Long senderAccountId, Long receiverAccountId, TransferDto transferDto) {
		if (senderAccountId.equals(receiverAccountId))
			throw new CustomException(ErrorCode.SAME_ACCOUNT_TRANSFER);

		// 서로 반대 방향의 송금이 동시에 들어와도 교착 상태에 빠지지 않도록 항상 ID가 작은 계좌부터 잠금
		Account first = accountService.getAccountForUpdate(Math.min(senderAccountId, receiverAccountId));
		Account second = accountService.getAccountForUpdate(Math.max(senderAccountId, receiverAccountId));
		Account senderAccount = first.getId().equals(senderAccountId) ? first : second;
		Account receiverAccount = first.getId().equals(senderAccountId) ? second : first;
		// 받는 계좌는 비밀번호를 확인한 계좌가 아니므로 거래정지여도 기존 코드로 거절함. 남의 계좌가 정지됐는지 알려 주지 않기 위함
		accountService.checkNotSuspended(senderAccount);

		Member sender = memberService.getMemberById(senderAccount.getMemberId());
		Member receiver = memberService.getMemberById(receiverAccount.getMemberId());

		UUID transferId = UUID.randomUUID();
		changeBalance(senderAccount, TransactionType.TRANSFER_OUT, transferDto.getAmount(), receiver.getName(), transferDto.getMemo(),
				new TransactionCause.CashTransfer(transferId, receiverAccountId));
		changeBalance(receiverAccount, TransactionType.TRANSFER_IN, transferDto.getAmount(), sender.getName(), transferDto.getMemo(),
				new TransactionCause.CashTransfer(transferId, senderAccountId));
		return "송금완료";
	}

	/**
	 * 잔액을 바꾸고 거래 기록과 시스템 계정의 반대 기록을 남김. 잔액을 바꾸는 모든 기능은 이 메서드를 거쳐야 함.
	 * 저장한 거래 기록의 ID를 돌려줌.
	 * 계좌는 이 트랜잭션에서 이미 잠가 둔 것을 받음. 잠금 순서를 부르는 쪽이 정하고, 같은 계좌를 다시 잠가 읽지 않기 위함
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Long changeBalance(Account lockedAccount, TransactionType type, long amount, String name, String memo,
			TransactionCause cause) {
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
		Transaction transaction = transactionRepository.save(Transaction.builder()
				.accountId(lockedAccount.getId())
				.type(type)
				.amount(signedAmount)
				.name(name)
				.memo(memo)
				.balanceAfter(balanceAfter)
				.orderId(cause.orderId())
				.stockTransferId(cause.stockTransferId())
				.cashTransferId(cause.cashTransferId())
				.counterpartyAccountId(cause.counterpartyAccountId())
				.build());
		type.systemAccount().ifPresent(systemAccount ->
				systemEntryRepository.save(new SystemEntry(transaction, systemAccount)));
		return transaction.getId();
	}

	/**
	 * 최근 거래부터. 같은 시각의 거래는 나중에 기록된 것부터 보여 페이지를 넘겨도 순서가 흔들리지 않음
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
