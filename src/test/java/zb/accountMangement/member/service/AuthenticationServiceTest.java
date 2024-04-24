package zb.accountMangement.member.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import zb.accountMangement.member.dto.SignUpDto;
import zb.accountMangement.member.repository.MemberRepository;

@SpringBootTest
class AuthenticationServiceTest {
  @Mock
  private MemberRepository memberRepository;
  @InjectMocks
  private MemberService memberService;

  private final String token = "TMPTOKEN";
  @Test
  void signUp() {
    SignUpDto dto = SignUpDto.builder()
        .name("test")
        .password("password123")
        .phoneNumber("1234567890")
        .build();

//    String encodedPassword = "encodedPassword";
//    when(passwordEncoder.encode(dto.getPassword())).thenReturn(encodedPassword);
    memberService.signUp(token,dto);

//    verify(memberRepository).save(any(Member.class));
  }
}