package zb.accountMangement.common.exception;

import lombok.Getter;
import zb.accountMangement.common.type.ErrorCode;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }
}
