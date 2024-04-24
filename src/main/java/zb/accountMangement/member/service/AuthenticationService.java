package zb.accountMangement.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.service.RedisService;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.dto.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationService {
	private final SendMessageService sendMessageService;
	private final MemberService memberService;
	private final RedisService redisService;

	/**
	 * 비밀번호 재설정 요청
	 *
	 * @param token           - 토큰
	 * @param findUserInfoDto - 회원정보 조회 dto (이름, 핸드폰번호)
	 * @return "인증 메세지 발송 완료"
	 */
	public String requestResetPw(String token, Long userId, FindUserInfoDto findUserInfoDto) {
		Member member = memberService.getUserById(userId);
		Member dtoMember = memberService.getUserByPhoneNumber(findUserInfoDto.getPhoneNumber());

		if (!member.getId().equals(dtoMember.getId())) {
			throw new CustomException(ErrorCode.UNMATCHED_USER);
		}

		return sendMessageService.sendVerificationMessage(token, findUserInfoDto.getPhoneNumber());
	}

	/**
	 * 비밀번호 재설정
	 *
	 * @param resetPwDto - 비밀번호 재설정 dto (인증번호, 새로운 PW)
	 * @return "비밀번호 재설정 완료"
	 */
	@Transactional
	public String verifyResetPw(String token, Long userId, ResetPwDto resetPwDto) {
		Member member = memberService.getUserById(userId);

		SmsVerificationDto info = redisService.getMsgVerificationInfo(token);
		if (info == null)
			throw new CustomException(ErrorCode.USER_NOT_EXIST);

		if (!member.getPhoneNumber().equals(info.getPhoneNumber()))
			throw new CustomException(ErrorCode.UNMATCHED_USER);


		// 핸드폰 인증 번호가 같으면
		if (info.getVerificationCode().equals(resetPwDto.getInputCode())) {
			member.setPassword(resetPwDto.getNewPassword());

			// 인증 정보 삭제
			redisService.deleteMsgVerificationInfo(token);
		} else
			throw new CustomException(ErrorCode.UNMATCHED_VERIFICATION_CODE);

		return "비밀번호 재설정 완료";
	}
}
