package zb.accountMangement.account.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Getter
@Setter
public class UpdateAccountDto {

    // 없으면 그대로 둠. DB 칸(VARCHAR(255))보다 길면 저장에서 실패하므로 요청 단계에서 막음
    @Size(max = 255)
    private String nickname;

    @NotBlank
    private String currentPassword;

    // 없으면 그대로 둠. 넣을 때는 개설할 때와 같이 빈 값일 수 없음
    @Pattern(regexp = ".*\\S.*", message = "새 계좌 비밀번호는 비워 둘 수 없습니다")
    private String newPassword;
}
