package dopamine.soundock.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PWLTokenResponse {
    private String accessToken;
    private String refreshToken;
}
