package dopamine.soundock.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PWLRequest {
    private String email;
    private String sessionId;
}
