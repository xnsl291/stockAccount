package zb.accountMangement.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import zb.accountMangement.account.model.entity.IdempotencyRecord;
import zb.accountMangement.account.repository.IdempotencyRecordRepository;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;

import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
	private final IdempotencyRecordRepository idempotencyRecordRepository;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 같은 회원이 같은 키로 보낸 요청은 한 번만 실행하고, 다시 오면 처음 결과를 그대로 돌려줌.
	 * 실행이 실패하면 키도 함께 롤백되므로 같은 키로 다시 시도할 수 있음
	 * @param memberId - 요청한 사용자 ID
	 * @param idempotencyKey - 요청마다 클라이언트가 정해 보내는 키
	 * @param request - 요청 내용. 같은 키를 다른 내용의 요청에 다시 쓰면 거절함
	 * @param action - 실제로 실행할 작업
	 * @return 작업 결과
	 */
	public String execute(Long memberId, String idempotencyKey, String request, Supplier<String> action) {
		Optional<IdempotencyRecord> processed = idempotencyRecordRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
		if (processed.isPresent())
			return replay(processed.get(), request);

		try {
			return transactionTemplate.execute(status -> {
				// 작업보다 키를 먼저 저장함. 같은 키의 요청이 동시에 오면 뒤에 온 요청은 유니크 제약 때문에
				// 앞선 요청이 끝날 때까지 여기서 기다렸다가 중복 오류로 끝나므로 작업이 두 번 실행되지 않음
				IdempotencyRecord record = idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.builder()
						.memberId(memberId)
						.idempotencyKey(idempotencyKey)
						.request(request)
						.build());
				String response = action.get();
				record.setResponse(response);
				return response;
			});
		} catch (DataIntegrityViolationException e) {
			return idempotencyRecordRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey)
					.map(record -> replay(record, request))
					.orElseThrow(() -> e);
		}
	}

	private String replay(IdempotencyRecord processed, String request) {
		if (!processed.getRequest().equals(request))
			throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
		return processed.getResponse();
	}
}
