package dopamine.soundock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "이메일 찾기 응답")
public class EmailSearchResponse {

    @Schema(description = "찾은 이메일 주소")
    private String email;
}
