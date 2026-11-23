package zb.accountMangement.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.common.auth.JwtTokenProvider;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.exception.UniqueConstraint;
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
	 * 다른 요청이 같은 회원의 탈퇴·계좌 개설·계좌 해지를 동시에 하지 못하게 회원 행을 잠그고 읽음.
	 * 트랜잭션 안에서, 그 트랜잭션의 첫 조회로 불러야 함. 잠금 순서는 회원 → 계좌임
	 *
	 * @param memberId - id
	 * @return Member
	 */
	public Member getMemberForUpdate(long memberId) {
		return memberRepository.findByIdForUpdate(memberId).orElseThrow(
				() -> new CustomException(ErrorCode.MEMBER_NOT_EXIST));
	}

	/**
	 * 가입용 회원 생성 - 가입 용도의 핸드폰 인증을 마친 번호여야 하고, 인증 표시는 이때 지움
	 *
	 * @param name          - 이름 (한글·영문·숫자 2~10자)
	 * @param phoneNumber   - 핸드폰번호 (하이픈 등이 섞여 있어도 됨)
	 * @param loginPassword - 로그인 비밀번호 (암호화 전)
	 * @return 저장한 회원
	 */
	@Transactional
	public Member createMember(String name, String phoneNumber, String loginPassword) {
		String normalized = PhoneNumber.normalize(phoneNumber);

		// 이름 유효성 검사
		if (!name.matches("[가-힣a-zA-Z0-9]{2,10}")) {
			throw new CustomException(ErrorCode.INVALID_NAME_FORMAT);
		}

		// 핸드폰번호 중복 검사
		if (memberRepository.existsByPhoneNumber(normalized))
			throw new CustomException(ErrorCode.DUPLICATED_PHONE_NUMBER);

		sendMessageService.consumeVerifiedPhone(normalized, VerificationPurpose.SIGN_UP);

		// 위의 중복 검사는 아직 커밋되지 않은 같은 번호의 가입을 보지 못함. 그런 가입이 먼저 커밋되면 저장이 유니크 제약에 걸리므로
		// 여기서 잡아 같은 오류 코드로 바꿈. 다른 저장 오류는 중복이 아니므로 그대로 올려 보냄
		try {
			return memberRepository.saveAndFlush(Member.builder()
					.name(name)
					.password(passwordEncoder.encode(loginPassword))
					.phoneNumber(normalized)
					.role(RoleType.USER)
					.build());
		} catch (DataIntegrityViolationException e) {
			if (UniqueConstraint.isViolated(e, "uk_member_phone_number"))
				throw new CustomException(ErrorCode.DUPLICATED_PHONE_NUMBER);
			throw e;
		}
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
		// 저장할 때 역할까지 모든 칸을 다시 쓰므로, 잠그지 않고 읽으면 그사이 커밋된 탈퇴를 읽은 시점 값으로 되돌림
		Member member = checkMemberPermission(loginMemberId, getMemberForUpdate(memberId));
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
		// 변경은 원래 트랜잭션이 끝날 때 DB에 반영돼 유니크 위반이 이 메서드 밖에서 500으로 나감.
		// 바로 반영해 커밋 전인 같은 번호의 변경·가입과 겹친 경우를 여기서 잡음
		try {
			memberRepository.saveAndFlush(member);
		} catch (DataIntegrityViolationException e) {
			if (UniqueConstraint.isViolated(e, "uk_member_phone_number"))
				throw new CustomException(ErrorCode.DUPLICATED_PHONE_NUMBER);
			throw e;
		}
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
		Member member = checkMemberPermission(loginMemberId, getMemberById(memberId));

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
		// 회원정보 수정과 같은 이유로 잠그고 읽음
		Member member = checkMemberPermission(loginMemberId, getMemberForUpdate(memberId));
		sendMessageService.confirmCode(member.getPhoneNumber(), resetPwDto.getInputCode());
		member.setPassword(passwordEncoder.encode(resetPwDto.getNewPassword()));
		jwtTokenProvider.deleteRefreshToken(member.getId());
		return "비밀번호 재설정 완료";
	}


	public Member checkMemberPermission(Long loginMemberId, Member member){
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
