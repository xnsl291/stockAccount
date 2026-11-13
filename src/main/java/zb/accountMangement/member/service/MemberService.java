package zb.accountMangement.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.common.auth.JwtTokenProvider;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.service.RedisService;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.dto.*;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.repository.MemberRepository;
import zb.accountMangement.member.model.RoleType;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
	private final MemberRepository memberRepository;
	private final SendMessageService sendMessageService;
	private final RedisService redisService;
	private final JwtTokenProvider jwtTokenProvider;

	/**
	 * id를 이용한 회원 정보 열람
	 *
	 * @param memberId - id
	 * @return Member
	 */
	public Member getMemberById(long memberId) {
		return memberRepository.findById(memberId).orElseThrow(
				() -> new CustomException(ErrorCode.MEMBER_NOT_EXIST));
	}



	/**
	 * 회원 정보 수정
	 *
	 * @param updateMemberDto - 사용자 정보수정 dto (이름, 핸드폰번호, 로그인 비밀번호)
	 * @return Member
	 */
	@Transactional
	public Member updateMemberInfo(String token, long memberId, UpdateMemberDto updateMemberDto) {
		Member member = checkMemberPermission(token, memberId);
		member.update(updateMemberDto);
		return member;
	}

	/**
	 * 비밀번호 재설정 요청
	 *
	 * @param token           - 토큰
	 * @param findMemberInfoDto - 회원정보 조회 dto (이름, 핸드폰번호)
	 * @return "인증 메세지 발송 완료"
	 */
	public String requestResetPw(String token, Long memberId, FindMemberInfoDto findMemberInfoDto) {
		checkMemberPermission(token, memberId);
		return sendMessageService.sendVerificationMessage(token, findMemberInfoDto.getPhoneNumber());
	}

	/**
	 * 비밀번호 재설정
	 *
	 * @param resetPwDto - 비밀번호 재설정 dto (인증번호, 새로운 PW)
	 * @return "비밀번호 재설정 완료"
	 */
	@Transactional
	public String verifyResetPw(String token, Long memberId, ResetPwDto resetPwDto) {
		Member member = getMemberById(memberId);
		SmsVerificationDto info = redisService.getMsgVerificationInfo(token);

		if (info == null)
			throw new CustomException(ErrorCode.MEMBER_NOT_EXIST);

		if (!member.getPhoneNumber().equals(info.getPhoneNumber()))
			throw new CustomException(ErrorCode.UNMATCHED_MEMBER);

		// 핸드폰 인증 번호가 같으면
		if (info.getVerificationCode().equals(resetPwDto.getInputCode())) {
			member.setPassword(resetPwDto.getNewPassword());

			// 인증 정보 삭제
			redisService.deleteMsgVerificationInfo(token);
		} else
			throw new CustomException(ErrorCode.UNMATCHED_VERIFICATION_CODE);

		return "비밀번호 재설정 완료";
	}


	private boolean isMemberTokenMatch(String token, Member member){
		return jwtTokenProvider.getId(token).equals(member.getId());
	}

	public Member checkMemberPermission(String token, long memberId){
		Member member = getMemberById(memberId);

		if(!isMemberTokenMatch(token, member))
			throw new CustomException(ErrorCode.UNMATCHED_MEMBER);

		if (member.getRole().equals(RoleType.WITHDRAWN))
			throw new CustomException(ErrorCode.WITHDRAWN_MEMBER);

		if (member.getRole().equals(RoleType.PENDING))
			throw new CustomException(ErrorCode.PENDING_MEMBER);

		return member;
	}



	/**
	 * 핸드폰번호를 이용한 회원 정보 열람
	 *
	 * @param phoneNumber - 핸드폰번호
	 * @return Member
	 */
	public Member getMemberByPhoneNumber(String phoneNumber) {
		return memberRepository.findByPhoneNumber(phoneNumber).orElseThrow(
				() -> new CustomException(ErrorCode.MEMBER_NOT_EXIST));
	}
}
