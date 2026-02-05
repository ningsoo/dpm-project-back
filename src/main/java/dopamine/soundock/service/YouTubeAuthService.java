package dopamine.soundock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dopamine.soundock.entity.Oauth;
import dopamine.soundock.entity.User;
import dopamine.soundock.exceptions.CustomException;
import dopamine.soundock.repository.OauthRepository;
import dopamine.soundock.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class YouTubeAuthService {

    private final OauthRepository oauthRepository;
    private final UserRepository userRepository;
    private final OauthService oauthService;
    private final RestTemplate restTemplate =  new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Transactional
    public void saveOrUpdateGoogleTokens(String email, String accessToken, String refreshToken, LocalDateTime expiresAt) {
        // 이메일로 우리 서비스 유저 찾기
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND));

        // 기존 OAuth 정보가 있는지 확인
        Oauth oauth = oauthRepository.findByUser(user)
                .orElse(Oauth.builder()
                        .user(user)
                        .provider("GOOGLE")
                        .build());

        // 토큰 정보 업데이트
        oauth.updateTokens(accessToken, refreshToken, expiresAt);

        // 저장
        oauthRepository.save(oauth);
    }

    // Access Token이 만료되었는지 확인하고, 만료되었다면 Refresh Token으로 갱신
    @Transactional
    public String getValidAccessToken(User user) {
        Oauth oauth = oauthRepository.findByUser(user)
                .orElseThrow(() -> new CustomException("구글 연동 정보가 없습니다.", HttpStatus.NOT_FOUND));

        // 토큰 만료 여부 확인 (여유시간 3분 추가)
        if (oauth.getExpiresAt().minusMinutes(3).isBefore(LocalDateTime.now())) {
            return refreshAccessToken(oauth);
        }
        return oauth.getAccessToken();
    }

    /** 구글 리프레시 토큰을 이용한 액세스토큰 재발급 메서드 */
    @Transactional
    public String refreshAccessToken(Oauth oauth) {
        if (oauth.getRefreshToken() == null) {
            throw new CustomException("Refresh Token이 없습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);
        }

        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("refresh_token", oauth.getRefreshToken());
            params.add("grant_type", "refresh_token");

            // 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // Google API 호출
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                String newAccessToken = jsonNode.get("access_token").asText();
                int expiresIn = jsonNode.get("expires_in").asInt();

                // 새로운 만료 시간 계산
                LocalDateTime newExpiresAt = LocalDateTime.now().plusSeconds(expiresIn);

                // DB 업데이트 (Refresh Token은 변경되지 않을 수 있음)
                oauth.updateTokens(newAccessToken, oauth.getRefreshToken(), newExpiresAt);
                oauthRepository.save(oauth);

                log.info("Access Token 갱신 성공: {}", oauth.getUser().getEmail());
                return newAccessToken;
            }
            throw new CustomException("토큰 갱신에 실패했습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);

        } catch (HttpClientErrorException ex) {
            if (ex.getResponseBodyAsString().contains("invalid_grant")) {
                log.info("사용자가 구글 연동을 취소함. 유저: {}", oauth.getUser().getEmail());
                oauthService.deleteConnection(oauth.getUser());
                return null;
            }
            throw ex;
        } catch (Exception e) {
            log.error("Access Token 갱신 중 오류 발생", e);
            throw new CustomException("토큰 갱신 중 오류가 발생했습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);
        }
    }


    /** 토큰 유효 검사 메서드*/
    // 이 메서드를 독립적인 트랜잭션으로 설정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean validateAndCleanupOAuth(User user) {
        Optional<Oauth> oauthOpt = oauthRepository.findByUser(user);
        if (oauthOpt.isEmpty()) {
            return false;
        }
        Oauth oauth = oauthOpt.get();
        try {
            // 구글에 토큰 상태 확인 (GET + url -> POST + body 으로 변경 (민감 정보의 노출 방지때문에)
            String verifyUrl = "https://oauth2.googleapis.com/tokeninfo";

            // 1. 요청 파라미터 설정 (Body에 담길 내용)
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("access_token", oauth.getAccessToken());

            // 2. 헤더 설정 (Form 데이터 형식임을 명시)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // 3. 요청 엔티티 생성
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // 4. POST 방식으로 호출
            restTemplate.postForEntity(verifyUrl, request, String.class);
            return true;
        } catch (Exception e) {
            // 액세스토큰이 무효하다면 리프레시 토큰을 통해 갱신 시도
            String newToken = refreshAccessToken(oauth);

            if (newToken != null) {
                return true;
            } else {
                return false;
            }
        }
    }
}
