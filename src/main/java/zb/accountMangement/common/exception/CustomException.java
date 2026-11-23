package zb.accountMangement.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import zb.accountMangement.common.type.ErrorCode;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CustomException extends RuntimeException {

    private ErrorCode errorCode;
    private String description;

    public CustomException (ErrorCode errorCode) {
        this.errorCode = errorCode;
        this.description = errorCode.getDescription();
    }
}