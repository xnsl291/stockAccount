package zb.accountMangement.common.type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST,"입력값이 올바르지 않습니다"),
    //jwt
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED,"토큰이 유효하지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED,"만료된 토큰입니다."),
    FAILED_VALIDATION(HttpStatus.UNAUTHORIZED,"인증에 실패하였습니다"),

    // user
    INVALID_NAME_FORMAT(HttpStatus.BAD_REQUEST,"입력값의 형식이 올바르지 않습니다."),
    MEMBER_NOT_EXIST(HttpStatus.NOT_FOUND,"해당 사용자가 존재하지 않습니다."),
    WITHDRAWN_MEMBER(HttpStatus.NOT_FOUND,"탈퇴한 사용자입니다."),
    PENDING_MEMBER(HttpStatus.NOT_FOUND,"사용이 정지된 계정입니다."),
    DUPLICATED_PHONE_NUMBER(HttpStatus.BAD_REQUEST,"이미 등록된 핸드폰 번호입니다."),

    UNMATCHED_MEMBER(HttpStatus.BAD_REQUEST,"사용자 정보가 일치하지 않습니다."),
    UNMATCHED_PASSWORD(HttpStatus.BAD_REQUEST,"비밀번호가 일치하지 않습니다."),
    UNMATCHED_VERIFICATION_CODE(HttpStatus.BAD_REQUEST,"인증 코드가 일치하지 않습니다."),
    MISMATCHED_USER_ID(HttpStatus.BAD_REQUEST,"토큰정보와 사용자의 정보가 일치하지 않습니다"),

    //account
    ACCOUNT_NOT_EXIST(HttpStatus.NOT_FOUND,"계좌 정보가 존재하지 않습니다."),
    DELETED_ACCOUNT(HttpStatus.NOT_FOUND,"삭제된 계좌입니다."),
    PENDING_ACCOUNT(HttpStatus.BAD_REQUEST,"거래정지된 계좌입니다."),
    EXCEED_BALANCE(HttpStatus.BAD_REQUEST,"잔액을 초과할 수 없습니다"),
    INVALID_ACCOUNT(HttpStatus.BAD_REQUEST,"거래할 수 없는 계좌 입니다."),

    MISMATCH_ACCOUNT_OWNER(HttpStatus.BAD_REQUEST,"계좌 소유자와 유저 정보가 일치하지 않습니다."),

    //stock
    STOCK_NOT_EXIST(HttpStatus.NOT_FOUND,"일치하는 종목이 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST,"보유량이 부족합니다"),
    NO_STOCK_BALANCE(HttpStatus.BAD_REQUEST,"보유량이 없습니다"),
    INVALID_REQUEST_DATE(HttpStatus.BAD_REQUEST,"날짜가 유효하지 않습니다."),
    ;

    private final HttpStatus status;
    private final String description;
}
