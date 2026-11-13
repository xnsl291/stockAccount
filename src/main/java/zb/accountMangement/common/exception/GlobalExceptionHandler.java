package zb.accountMangement.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zb.accountMangement.common.type.ErrorCode;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler  {

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        return ErrorResponse.toResponseEntity(e.getErrorCode());
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
