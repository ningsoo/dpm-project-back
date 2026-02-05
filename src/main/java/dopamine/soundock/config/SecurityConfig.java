package dopamine.soundock.config;

import dopamine.soundock.global.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://192.168.200.*:5173",
                "http://192.168.56.1:3000",
                "http://192.168.200.*:3000"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")); // 허용할 HTTP 메서드들
        configuration.addAllowedHeader("*"); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 쿠키 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http
                // OAuth2 로그인 기능 활성화
                .oauth2Login(oauth2 -> oauth2
                        // [권한 부여 엔드포인트 설정]
                        // 사용자가 '유튜브 연동' 버튼을 눌러 구글 서버로 이동하는 시점의 설정을 담당
                        .authorizationEndpoint(authorization -> authorization
                                // [커스텀 리졸버 등록]
                                // 작성한 CustomAuthorizationRequestResolver를 등록
                                // 구글에게 "유튜브 API 사용을 위해 리프레시 토큰을 달라"고 요청하는 역할
                                .authorizationRequestResolver(
                                        new CustomAuthorizationRequestResolver(clientRegistrationRepository))
                                // [인증 요청 저장소 등록]
                                // 쿠키를 사용하는 HttpCookieOAuth2AuthorizationRequestRepository를 등록합니다.
                                // 구글에 갔다 오기 전까지 필요한 임시 정보들을 브라우저 쿠키에 임시저장하는 역할
                                .authorizationRequestRepository(httpCookieOAuth2AuthorizationRequestRepository)
                        )
                        // [로그인 성공 핸들러 등록]
                        // 구글 인증이 성공하면 실행될 로직
                        .successHandler(oAuth2AuthenticationSuccessHandler) // 유튜브 토큰을 DB에 저장, 우리 서비스 전용 JWT를 발급
                        // [로그인 실패 핸들러 등록]
                        // 구글 인증 도중 취소하거나 에러가 나면 실행될 로직
                        .failureHandler(oAuth2AuthenticationFailureHandler) // 에러 메시지를 담아 사용자를 다시 리액트(React) 화면으로 리다이렉트
                )

                // CORS 설정 (프론트엔드 React와 통신을 위해 필수)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // csrf는 Jwt 토큰으로 인해 어차피 막히기 때문에 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // 서버가 사용자의 상태를 세션에 저장하지 않도록 설정
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/payments/**","/payment/**").permitAll() // ** : 테스트용 /api/payments/, /payment/ 뒤의 모든 것들 허용
                        .requestMatchers("/api/auth/**").permitAll() // ** : /api/auth/ 뒤의 모든 것들 허용
                        .requestMatchers(HttpMethod.GET,"/api/boards/**").permitAll() // ** : 테스트용 /api/boards/ 뒤의 모든 것들 허용
                        .requestMatchers(HttpMethod.GET,"/uploads/profiles/**", "/uploads/boards/**").permitAll() // 프로필 및 게시글 파일은 모두 허용
                        .requestMatchers(HttpMethod.GET,"/uploads/music/**").hasAuthority("USER") // 음악 파일은 회원만 확인 가능
                        .requestMatchers("/api/passwordless/login-trigger").permitAll() // 패스워드리스 로그인 트리거 허용
                        .requestMatchers("/api/passwordless/result").permitAll() // 패스워드리스 로그인 결과 확인 허용
                        .requestMatchers("/api/passwordless/cancel").permitAll() // 패스워드리스 인증 취소 허용
                        .requestMatchers(
                                "/payment/*.html",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"

                        ).permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .anyRequest().authenticated() // 그 외의 요청은 인증 필요
                )
                // 직접만든 jwtAuthenticationFilter를 UsernamePasswordAuthenticationFilter 보다 먼저 실행되도록 설정
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 예외 처리 (인증 실패 시 401 에러를 더 명확하게 반환)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 1. 응답 타입을 JSON, 한글(UTF-8)로 설정
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401

                            // 2. RestResponse 규격에 맞는 JSON 문자열 직접 생성
                            String jsonResponse = "{" +
                                    "\"success\": false," +
                                    "\"message\": \"로그인이 필요한 서비스입니다.\"," +
                                    "\"data\": null" +
                                    "}";

                            response.getWriter().write(jsonResponse);
                        })
                );
        return http.build();
    }

    /**
     * OAuth2 인증 요청(Authorization Request)이 생성될 때
     * 우리가 원하는 추가 파라미터(access_type, prompt 등)를 끼워 넣는 역할
     */
    public static class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
        // 스프링 시큐리티가 기본으로 제공하는 리졸버(기본적인 틀)
        private final OAuth2AuthorizationRequestResolver defaultResolver;

        @Value("${oauth.request.dev}")
        private boolean dev;

        /**
         * 생성자: 기본 리졸버를 초기화
         * /oauth2/authorization 주소로 들어오는 요청을 처리하도록 설정
         */
        public CustomAuthorizationRequestResolver(ClientRegistrationRepository repo) {
            this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        }

        /**
         * 인증 요청을 해결(Resolve)하는 메서드(주소창을 보고 어떤 서비스(google 등)인지 스프링이 스스로 판단해야 할 때 사용)
         */
        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            // 1. 기본 리졸버를 통해 표준적인 인증 요청 객체를 만듬
            OAuth2AuthorizationRequest authRequest = defaultResolver.resolve(request);
            // 2. 그 객체에 우리만의 설정을 붙임
            return customize(authRequest);
        }

        /**
         * 인증 요청을 해결하는 메서드2(이미 어떤 클라이언트(google인지 kakao인지 등)인지 명확히 알고 있는 상태일때 사용)
         */
        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
            OAuth2AuthorizationRequest authRequest = defaultResolver.resolve(request, clientRegistrationId);
            return customize(authRequest);
        }

        /**
         * (커스터마이징) 구글 서버에 보낼 파라미터를 수정
         */
        private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest authRequest) {
            // 인증 요청이 없으면 아무것도 하지 않는다
            if (authRequest == null) return null;

            // 기존에 설정된 파라미터들을 복사(수정하기 위해)
            Map<String, Object> extraParams = new HashMap<>(authRequest.getAdditionalParameters());
             // 구글에게 "이 사용자가 로그아웃 상태일 때도 우리가 데이터에 접근할 수 있게
             // '리프레시 토큰'을 같이 보내줘!"라고 요청하는 설정
            extraParams.put("access_type", "offline");

            // 사용자가 구글 로그인 화면에서 어떤 경험을 할지 결정
            // 개발 중일 때는 (true) prompt=consent
            // 배포 환경에서는 (false) prompt=select_account 로 변경
            if (dev) {
                extraParams.put("prompt", "consent");    // 매번 동의 화면을 띄워 리프레시 토큰 재발급 강제
            } else {
                extraParams.put("prompt", "select_account"); // 계정 선택 창만 띄우고, 동의 화면은 최초 1회만 표시
            }

            // 수정한 파라미터들을 다시 담아서 새로운 인증 요청 객체를 빌드하여 반환
            return OAuth2AuthorizationRequest.from(authRequest)
                    .additionalParameters(extraParams)
                    .build();
        }
    }
}
