package zb.accountMangement.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.dto.AccountManagementDto;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.common.auth.JwtToken;
import zb.accountMangement.common.auth.JwtTokenProvider;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.member.model.PhoneNumber;
import zb.accountMangement.common.type.RoleType;
import zb.accountMangement.member.model.VerificationPurpose;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.dto.*;
import zb.accountMangement.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationService {

	private final MemberRepository memberRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	private final AccountService accountService;
	private final MemberService memberService;
	private final JwtTokenProvider jwtTokenProvider;
	private final SendMessageService sendMessageService;

	/**
	 * 회원 가입 - 가입 전에 핸드폰 인증을 마쳐야 함
	 * @param signUpDto - 회원 가입 dto (이름, 핸드폰 번호, 로그인 비밀번호, 초기 계좌 비밀번호)
	 */
	@Transactional
	public Boolean signUp(SignUpDto signUpDto) {
		String phoneNumber = PhoneNumber.normalize(signUpDto.getPhoneNumber());

		// 이름 유효성 검사
		if (!signUpDto.getName().matches("[가-힣a-zA-Z0-9]{2,10}")) {
			throw new CustomException(ErrorCode.INVALID_NAME_FORMAT);
		}

		// 핸드폰번호 중복 검사
		if (memberRepository.existsByPhoneNumber(phoneNumber))
			throw new CustomException(ErrorCode.DUPLICATED_PHONE_NUMBER);

		sendMessageService.consumeVerifiedPhone(phoneNumber, VerificationPurpose.SIGN_UP);

		Member member = memberRepository.save(Member.builder()
				.name(signUpDto.getName())
				.password(passwordEncoder.encode(signUpDto.getLoginPassword()))
				.phoneNumber(phoneNumber)
				.role(RoleType.USER)
				.build());

		// 초기 계좌 생성 - 계좌 비밀번호는 openAccount에서 암호화함
		AccountManagementDto accountManagementDto = AccountManagementDto.builder()
				.nickname(null)
				.password(signUpDto.getInitialAccountPassword())
				.build();
		accountService.openAccount(member.getId(), accountManagementDto);
		return true;
	}

	/**
	 * 회원탈퇴 - 계좌를 모두 해지하고 탈퇴 처리 후 현재 토큰을 무효화함. 잔액이나 보유 주식이 남아 있으면 탈퇴할 수 없음
	 */
	@Transactional
	public Boolean deleteMember(Long loginMemberId, String accessToken) {
		Member member = memberService.checkMemberPermission(loginMemberId, loginMemberId);
		accountService.closeAllAccounts(loginMemberId);
		member.delete();
		jwtTokenProvider.invalidateTokens(loginMemberId, accessToken);
		return true;
	}

	/**
	 * 로그인 기능 - 탈퇴·정지 회원은 로그인할 수 없음
	 *
	 * @param signInDto - 로그인 dto (핸드폰번호, 로그인 PW)
	 * @return token - 토큰
	 */
	public JwtToken signIn(SignInDto signInDto) {
		Member member = memberService.getMemberByPhoneNumber(PhoneNumber.normalize(signInDto.getPhoneNumber()));

		// 비밀번호 일치여부 확인
		if (!passwordEncoder.matches(signInDto.getPassword(), member.getPassword()))
			throw new CustomException(ErrorCode.UNMATCHED_PASSWORD);

		memberService.checkActive(member);
		return jwtTokenProvider.generateToken(member.getId(), member.getRole());
	}

	/**
	 * 로그아웃
	 *
	 * @param loginMemberId - 로그인한 사용자 ID
	 * @param accessToken - 액세스 토큰
	 * @return true
	 */
	public Boolean signOut(Long loginMemberId, String accessToken) {
		jwtTokenProvider.invalidateTokens(loginMemberId, accessToken);
		return true;
	}

	/**
	 * 토큰 재발급 - 사용한 리프레시 토큰은 새 토큰으로 교체되어 다시 쓸 수 없음
	 *
	 * @param refreshToken - 리프레시 토큰
	 * @return token - 새 토큰
	 */
	public JwtToken reissue(String refreshToken) {
		Member member = memberService.getMemberById(jwtTokenProvider.consumeRefreshToken(refreshToken));
		memberService.checkActive(member);
		return jwtTokenProvider.generateToken(member.getId(), member.getRole());
	}

}
