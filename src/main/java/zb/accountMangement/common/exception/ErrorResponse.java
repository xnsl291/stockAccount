package zb.accountMangement.common.exception;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import zb.accountMangement.common.type.ErrorCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private final LocalDateTime timestamp = LocalDateTime.now();
    private final int status;
    private final String code;
    private final String message;
    private final Map<String, String> errors;

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode e){
        return ResponseEntity
                .status(e.getStatus())
                .body(ErrorResponse.builder()
                        .status(e.getStatus().value())
                        .code(e.name())
                        .message(e.getDescription())
                        .build()
                );
    }
}