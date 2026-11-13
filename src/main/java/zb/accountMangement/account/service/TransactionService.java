package zb.accountMangement.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.model.entity.Transaction;
import zb.accountMangement.account.dto.TransactionDto;
import zb.accountMangement.account.repository.TransactionRepository;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.service.MemberService;

import java.util.List;
import java.util.stream.Collectors;

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
	 * @param depositDto - 계좌거래 dto (계좌 ID, 이름, 금액, 메모, 거래일시)
	 * @return "입금완료"
	 */
	@Transactional
	public String deposit(TransactionDto depositDto) {
		Account account = accountService.getAccountById(depositDto.getAccountId());
		double recentBalance = account.getBalance() + depositDto.getAmount();

		if (account.isExistsAccount()) {
			Transaction transaction = Transaction.builder()
					.accountId(depositDto.getAccountId())
					.type(TransactionType.DEPOSIT)
					.amount(depositDto.getAmount())
					.name("전자입금")
					.memo(depositDto.getMemo())
					.balance(recentBalance)  //거래 후 잔액
					.build();

			transactionRepository.save(transaction);
			account.setBalance(recentBalance);
			return "입금완료";
		}
		throw new CustomException(ErrorCode.INVALID_ACCOUNT);
	}

	/**
	 * 출금
	 * @param withdrawalDto - 계좌거래 dto (계좌 ID, 이름, 금액, 메모, 거래일시)
	 * @return "출금완료"
	 */
	@Transactional
	public String withdrawal(TransactionDto withdrawalDto) {
		Account account = accountService.getAccountById(withdrawalDto.getAccountId());
		double recentBalance = account.getBalance() - withdrawalDto.getAmount();

		if (account.isExistsAccount()) {
			Transaction transaction = Transaction.builder()
					.accountId(withdrawalDto.getAccountId())
					.type(TransactionType.WITHDRAWN)
					.amount(withdrawalDto.getAmount())
					.name("전자출금")
					.memo(withdrawalDto.getMemo())
					.balance(recentBalance)
					.build();
			transactionRepository.save(transaction);
			account.setBalance(recentBalance);
			return "출금완료";
		}
		throw new CustomException(ErrorCode.INVALID_ACCOUNT);
	}

	/**
	 * 송금
	 * @param transferDto - 계좌거래 dto (계좌 ID, 이름, 금액, 메모, 거래일시)
	 * @return "송금완료"
	 */
	@Transactional
	public String transfer(Long senderAccountId, Long receiverAccountId, TransactionDto transferDto) {
		Account senderAccount = accountService.getAccountById(senderAccountId);
		Account recipientAccount = accountService.getAccountById(receiverAccountId);

		Member sender = memberService.getMemberById(senderAccount.getMemberId());
		Member receiver = memberService.getMemberById(recipientAccount.getMemberId());

		double recentSenderBalance = senderAccount.getBalance() - transferDto.getAmount();
		double recentRecipientBalance = recipientAccount.getBalance() + transferDto.getAmount();

		if (senderAccount.getBalance() < transferDto.getAmount())
			throw new CustomException(ErrorCode.EXCEED_BALANCE);

		if (senderAccount.isExistsAccount() && recipientAccount.isExistsAccount()) {
			Transaction senderTransaction = Transaction.builder()
					.accountId(senderAccountId)
					.type(TransactionType.TRANSFER)
					.amount(-transferDto.getAmount())
					.name(receiver.getName()) // 수신자 이름
					.memo(transferDto.getMemo())
					.balance(recentSenderBalance)
					.build();
			transactionRepository.save(senderTransaction);

			Transaction recipientTransaction = Transaction.builder()
					.accountId(receiverAccountId)
					.type(TransactionType.TRANSFER)
					.amount(transferDto.getAmount())
					.name(sender.getName()) // 발신자 이름
					.memo(transferDto.getMemo())
					.balance(recentRecipientBalance)
					.build();
			transactionRepository.save(recipientTransaction);

			senderAccount.setBalance(recentSenderBalance);
			recipientAccount.setBalance(recentRecipientBalance);
			return "송금완료";
		}
		throw new CustomException(ErrorCode.INVALID_ACCOUNT);
	}

	/**
	 * 거래내역 조회
	 * @param accountId - 계좌 ID
	 * @return 거래내역 리스트
	 */
	public List<TransactionDto> getTransactionsByAccountId(Long accountId) {
		List<Transaction> transactions = transactionRepository.findByAccountIdOrderByTransactedAtDesc(accountId);
		return transactions.stream()
				.map(this::mapToTransactionDto)
				.collect(Collectors.toList());
	}

	/**
	 * transaction 을 transactionDto로 매핑
	 * @param transaction - 거래
	 * @return TransactionDto
	 */
	private TransactionDto mapToTransactionDto(Transaction transaction) {
		return TransactionDto.builder()
				.accountId(transaction.getAccountId())
				.amount(transaction.getAmount())
				.name(transaction.getName())
				.memo(transaction.getMemo())
				.transactedAt(transaction.getTransactedAt())
				.build();
	}
}
