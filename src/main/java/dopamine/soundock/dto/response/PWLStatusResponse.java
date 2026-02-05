package dopamine.soundock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 가입 확인 응답 데이터
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "사용자 패스워드리스 등록 여부 응답")
public class PWLStatusResponse {

    @Schema(description = "등록 여부 (true: 등록됨, false: 미등록)")
    private boolean exist;
}
