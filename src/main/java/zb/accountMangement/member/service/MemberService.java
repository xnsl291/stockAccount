package zb.accountMangement.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.common.auth.JwtTokenProvider;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.dto.*;
import zb.accountMangement.member.model.PhoneNumber;
import zb.accountMangement.member.model.VerificationPurpose;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.repository.MemberRepository;
import zb.accountMangement.common.type.RoleType;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
	private final MemberRepository memberRepository;
	private final SendMessageService sendMessageService;
	private final BCryptPasswordEncoder passwordEncoder;
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
	 * 회원 정보 수정 - 현재 로그인 비밀번호가 맞아야 함. 핸드폰번호를 바꾸려면 새 번호로 문자 인증을 먼저 마쳐야 하고,
	 * 비밀번호를 바꾸면 리프레시 토큰을 지워 다른 기기에서 토큰을 재발급받지 못하게 함
	 *
	 * @param updateMemberDto - 사용자 정보수정 dto (이름, 핸드폰번호, 새 로그인 비밀번호, 현재 로그인 비밀번호)
	 * @return Member
	 */
	@Transactional
	public Member updateMemberInfo(Long loginMemberId, long memberId, UpdateMemberDto updateMemberDto) {
		Member member = checkMemberPermission(loginMemberId, memberId);
		if (!passwordEncoder.matches(updateMemberDto.getCurrentPassword(), member.getPassword()))
			throw new CustomException(ErrorCode.UNMATCHED_PASSWORD);
		String phoneNumber = PhoneNumber.normalize(updateMemberDto.getPhoneNumber());

		if (!phoneNumber.equals(member.getPhoneNumber())) {
			if (memberRepository.existsByPhoneNumber(phoneNumber))
				throw new CustomException(ErrorCode.DUPLICATED_PHONE_NUMBER);
			sendMessageService.consumeVerifiedPhone(phoneNumber, VerificationPurpose.PHONE_CHANGE);
		}

		boolean passwordChanged = !updateMemberDto.getPassword().equals(updateMemberDto.getCurrentPassword());
		member.update(updateMemberDto.getName(), phoneNumber, passwordEncoder.encode(updateMemberDto.getPassword()));
		if (passwordChanged)
			jwtTokenProvider.deleteRefreshToken(member.getId());
		return member;
	}

	/**
	 * 비밀번호 재설정 요청 - 입력한 이름·핸드폰번호가 회원 정보와 같으면 등록된 번호로 인증번호 발송
	 *
	 * @param loginMemberId     - 로그인한 사용자 ID
	 * @param findMemberInfoDto - 회원정보 조회 dto (이름, 핸드폰번호)
	 * @param clientIp          - 요청한 IP
	 * @return "인증 메세지 발송 완료"
	 */
	public String requestResetPw(Long loginMemberId, Long memberId, FindMemberInfoDto findMemberInfoDto, String clientIp) {
		Member member = checkMemberPermission(loginMemberId, memberId);

		if (!member.getName().equals(findMemberInfoDto.getName())
				|| !member.getPhoneNumber().equals(PhoneNumber.normalize(findMemberInfoDto.getPhoneNumber())))
			throw new CustomException(ErrorCode.UNMATCHED_MEMBER);

		return sendMessageService.sendVerificationMessage(member.getPhoneNumber(), clientIp);
	}

	/**
	 * 비밀번호 재설정 - 리프레시 토큰을 지워 다른 기기에서 토큰을 재발급받지 못하게 함
	 *
	 * @param resetPwDto - 비밀번호 재설정 dto (인증번호, 새로운 PW)
	 * @return "비밀번호 재설정 완료"
	 */
	@Transactional
	public String verifyResetPw(Long loginMemberId, Long memberId, ResetPwDto resetPwDto) {
		Member member = checkMemberPermission(loginMemberId, memberId);
		sendMessageService.confirmCode(member.getPhoneNumber(), resetPwDto.getInputCode());
		member.setPassword(passwordEncoder.encode(resetPwDto.getNewPassword()));
		jwtTokenProvider.deleteRefreshToken(member.getId());
		return "비밀번호 재설정 완료";
	}


	public Member checkMemberPermission(Long loginMemberId, long memberId){
		Member member = getMemberById(memberId);

		if(!member.getId().equals(loginMemberId))
			throw new CustomException(ErrorCode.UNMATCHED_MEMBER);

		checkActive(member);
		return member;
	}

	/**
	 * 탈퇴·정지 회원이면 예외
	 *
	 * @param member - 회원
	 */
	public void checkActive(Member member) {
		if (member.getRole().equals(RoleType.WITHDRAWN))
			throw new CustomException(ErrorCode.WITHDRAWN_MEMBER);

		if (member.getRole().equals(RoleType.PENDING))
			throw new CustomException(ErrorCode.PENDING_MEMBER);
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
