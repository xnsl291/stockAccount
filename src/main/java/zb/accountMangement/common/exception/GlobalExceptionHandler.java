package zb.accountMangement.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zb.accountMangement.common.type.ErrorCode;

import javax.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler  {

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    @ExceptionHandler({MissingRequestHeaderException.class, ConstraintViolationException.class})
    protected ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_ARGUMENT);
    }

    // 교착 상태나 잠금 대기 시간 초과. 요청 자체는 정상이라 다시 보내면 처리되므로 409로 알림
    @ExceptionHandler(PessimisticLockingFailureException.class)
    protected ResponseEntity<ErrorResponse> handleLockFailure(PessimisticLockingFailureException e) {
        log.warn("잠금 충돌로 처리하지 못한 요청", e);
        return ErrorResponse.toResponseEntity(ErrorCode.CONCURRENT_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException e) {
        Map<String, String> error = new HashMap<>();
        e.getBindingResult().getAllErrors()
                .forEach(c -> error.put( ((FieldError) c).getField(), c.getDefaultMessage()));
        ErrorCode code = ErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                        .status(code.getStatus().value())
                        .code(code.toString())
                        .errors(error)
                .build());
    }
}
