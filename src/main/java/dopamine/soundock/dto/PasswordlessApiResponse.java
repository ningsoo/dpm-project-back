package dopamine.soundock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공통 응답 클래스
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "패스워드리스 서비스 공통 응답 래퍼")
public class PasswordlessApiResponse<T> {

    @Schema(description = "요청 성공 여부", example = "true")
    private boolean result;

    @Schema(description = "응답 메시지")
    private String msg;

    @Schema(description = "X1280 서버 응답 코드", example = "000.0")
    private String code;

    @Schema(description = "API별 상세 데이터")
    private T data;
}
