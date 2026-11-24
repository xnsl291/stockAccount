package zb.accountMangement.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import zb.accountMangement.common.type.ErrorCode;

import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 모든 오류를 ErrorResponse 한 가지 형식으로 응답.
 * 헤더 누락, 타입 불일치, 읽을 수 없는 본문 같은 스프링 MVC 예외는 부모 클래스가 상태 코드를 정하고 여기서 형식만 맞춤
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_ARGUMENT);
    }

    // 금액·수량이 너무 커서 Math.*Exact 계산이 넘친 경우
    @ExceptionHandler(ArithmeticException.class)
    protected ResponseEntity<ErrorResponse> handleArithmetic(ArithmeticException e) {
        return ErrorResponse.toResponseEntity(ErrorCode.AMOUNT_OUT_OF_RANGE);
    }

    // 교착 상태나 잠금 대기 시간 초과. 요청 자체는 정상이라 다시 보내면 처리되므로 409로 알림
    @ExceptionHandler(PessimisticLockingFailureException.class)
    protected ResponseEntity<ErrorResponse> handleLockFailure(PessimisticLockingFailureException e) {
        log.warn("잠금 충돌로 처리하지 못한 요청", e);
        return ErrorResponse.toResponseEntity(ErrorCode.CONCURRENT_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 없는 주소는 Spring 6부터 예외로 올라옴. 응답 본문이 바뀌지 않게 전처럼 서블릿의 404 오류로 넘김
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        try {
            ((ServletWebRequest) request).getResponse().sendError(status.value());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // 부모 클래스가 넘기는 값은 모두 표준 상태 코드라 이름과 설명 문구를 꺼낼 수 있음
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        if (status.is5xxServerError())
            log.error("처리하지 못한 예외", ex);

        Map<String, String> errors = null;
        if (ex instanceof BindException) {
            errors = new HashMap<>();
            for (FieldError fieldError : ((BindException) ex).getFieldErrors())
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        boolean badRequest = status == HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.builder()
                .status(status.value())
                .code(badRequest ? ErrorCode.INVALID_ARGUMENT.name() : status.name())
                .message(badRequest ? ErrorCode.INVALID_ARGUMENT.getDescription() : status.getReasonPhrase())
                .errors(errors)
                .build());
    }
}
