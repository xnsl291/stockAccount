package zb.accountMangement.auth.service;

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
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.auth.dto.*;
import zb.accountMangement.member.service.MemberService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationService {

	private final BCryptPasswordEncoder passwordEncoder;
	private final AccountService accountService;
	private final MemberService memberService;
	private final JwtTokenProvider jwtTokenProvider;

	/**
	 * 회원 가입 - 가입 전에 핸드폰 인증을 마쳐야 함
	 * @param signUpDto - 회원 가입 dto (이름, 핸드폰 번호, 로그인 비밀번호, 초기 계좌 비밀번호)
	 */
	@Transactional
	public Boolean signUp(SignUpDto signUpDto) {
		Member member = memberService.createMember(
				signUpDto.getName(), signUpDto.getPhoneNumber(), signUpDto.getLoginPassword());

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
		// 회원 행을 먼저 잠가, 계좌 목록을 읽은 뒤에 같은 회원의 계좌 개설이 끼어들지 못하게 함
		Member member = memberService.getMemberForUpdate(loginMemberId);
		memberService.checkActive(member);
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
