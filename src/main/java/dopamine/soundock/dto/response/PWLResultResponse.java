package dopamine.soundock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "패스워드리스 인증 결과 응답 데이터")
public class PWLResultResponse {
    @Schema(description = "인증 상태 (Y: 완료, N: 대기 중, C: 취소)")
    private String auth;

    @Schema(description = "사용자 ID (이메일)")
    private String userId;

    @Schema(description = "인증 성공 시 발급되는 해시값 (인증 완료 시에만 존재)")
    private String hash;

    @Schema(description = "로그인 인증 완료시 발급받은 우리 서비스 전용 AccessToken")
    private String accessToken;

    @Schema(description = "로그인 인증 완료시 발급받은 우리 서비스 전용 RefreshToken")
    private String refreshToken;
}
