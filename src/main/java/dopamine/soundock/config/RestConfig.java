package dopamine.soundock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 타임아웃 설정을 위한 팩토리 생성
        // 외부 API에서 어떤 에러로 인해 응답을 너무 늦게 주거나 계속 안주거나 하면 응답이 올때까지 우리서버는 기다리게 되기 때문에
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 서버와 연결이 맺어질 때까지 기다리는 시간 - 3초
        factory.setConnectTimeout(3000);

        // 연결 후 데이터를 받을 때까지 기다리는 시간 - 5초
        factory.setReadTimeout(5000);

        return new RestTemplate(factory);
    }
}
