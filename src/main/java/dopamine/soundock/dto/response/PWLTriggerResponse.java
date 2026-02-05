package dopamine.soundock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "패스워드리스 로그인 트리거 응답 데이터")
public class PWLTriggerResponse {

    @Schema(description = "인증 유효 기간 (초)")
    private Integer term;

    @Schema(description = "푸시 알림 서버 URL")
    private String pushConnectorUrl;

    @Schema(description = "푸시 알림 토큰")
    private String pushConnectorToken;

    @Schema(description = "서비스 비밀번호 (인증 검증용)")
    private String servicePassword;

    @Schema(description = "사용자 ID (이메일)")
    private String userId;

    @Schema(description = "세션 ID (결과 확인용)")
    private String sessionId;
}
