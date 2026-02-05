package dopamine.soundock.service;

import dopamine.soundock.dto.response.PWLTokenResponse;

// 인터페이스를 이용해 실제 구현체(X1280ServiceImpl)와 테스트용(MockImpl)을 교체하기 쉽게 설계
public interface X1280Service {

    String isAp(String userId);

    String joinAp(String userId);

    String getToken(String userId);

    String getSp(String userId, String clientIp, String sessionId);

    String checkResult(String userId, String sessionId);

    String cancel(String userId, String sessionId);

    String withdrawalAp(String userId);

    PWLTokenResponse verifyAndGenerateTokens(String email, String sessionId);
}
