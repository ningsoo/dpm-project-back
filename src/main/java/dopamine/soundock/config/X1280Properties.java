package dopamine.soundock.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
// appliaction.properties 파일에서 'x1280'으로 시작하는 설정값들을 매핑
@ConfigurationProperties(prefix = "x1280")
@Getter
@Setter
public class X1280Properties {
    private String baseUrl; // 외부 API 기본 주소
    private String serverKey; // 인증에 필요한 서버키
    private boolean useMock = false; // 테스트용 가짜 데이터를 사용할지 여부
}
