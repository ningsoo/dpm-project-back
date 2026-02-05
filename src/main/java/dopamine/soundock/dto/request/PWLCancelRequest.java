package dopamine.soundock.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "패스워드리스 인증 취소 요청 데이터")
public class PWLCancelRequest {

    @Schema(description = "인증을 취소할 아이디")
    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;

    @Schema(description = "현재 인증 중인 세션 아이디")
    @NotBlank(message = "세션 ID는 필수입니다.")
    private String sessionId;
}
