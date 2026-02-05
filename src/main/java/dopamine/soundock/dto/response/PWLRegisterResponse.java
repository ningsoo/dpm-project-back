package dopamine.soundock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 패스워드리스 등록 응답
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "패스워드리스 가입 등록 응답 데이터")
public class PWLRegisterResponse {
    @Schema(description = "QR 코드 이미지 (Base64 인코딩)")
    private String qr;

    @Schema(description = "패스워드리스 서비스 ID")
    private String corpId;

    @Schema(description = "등록 키")
    private String registerKey;

    @Schema(description = "유효 기간 (초)")
    private Integer terms;

    @Schema(description = "인증 서버 URL")
    private String serverUrl;

    @Schema(description = "푸시 알림 서버 URL")
    private String pushConnectorUrl;

    @Schema(description = "푸시 알림 토큰")
    private String pushConnectorToken;

    @Schema(description = "사용자 아이디(이메일)")
    private String userId;
}
