package dopamine.soundock.controller;

import dopamine.soundock.dto.*;
import dopamine.soundock.exceptions.CustomException;
import dopamine.soundock.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Validated
public class AuthController {
    private final AuthService authService;

    @Value("${app.frontend.success-url}")
    private String successUrl;

    @Value("${app.frontend.fail-url}")
    private String failUrl;

    // 이메일 중복 체크
    @GetMapping("/email")
    public ResponseEntity<ApiResponse<Void>> checkEmail(@Valid @ModelAttribute ValidateEmailRequest validateEmailRequest) {
        authService.validateEmail(validateEmailRequest);

        // 이메일 중복 체크 통과시 로직 실행
        return ResponseEntity.ok(ApiResponse.success("사용 가능한 이메일 입니다."));
    }

    // 닉네임 중복 체크
    @GetMapping("/nickname")
    public ResponseEntity<ApiResponse<Void>> checkNickname(@Valid @ModelAttribute ValidateNicknameRequest validateNicknameRequest) {
        authService.validateNickname(validateNicknameRequest);

        // 닉네임 중복 체크 통과시 로직 실행
        return ResponseEntity.ok(ApiResponse.success("사용 가능한 닉네임 입니다."));
    }

    // 회원 가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody UserSignupRequest userSignupRequest
    ) {

        // ----------테스트 단계에서는 현재 주소를 자동으로 추적하는 이 코드를 사용하지만 배포환경에서는 변경이 필요함-------------------
        String siteURL = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        // ------------------------------------------------------------------------------------------------------------
        // 유효성 검사 통과시 로직 실행
        authService.signupUser(userSignupRequest, siteURL);
        return ResponseEntity.ok(ApiResponse.success("인증 이메일 전송이 완료되었습니다. 이메일을 확인해주세요."));
    }

    // 이메일 전송 (재전송시 사용)
    @PostMapping("/verification")
    public ResponseEntity<ApiResponse<Void>> verification(
            @Valid @RequestBody VerificationEmailRequest verificationEmailRequest) {
        // ----------테스트 단계에서는 현재 주소를 자동으로 추적하는 이 코드를 사용하지만 배포환경에서는 변경이 필요함-------------------
        String siteURL = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        // ------------------------------------------------------------------------------------------------------------
        authService.sendVerificationEmail(verificationEmailRequest, siteURL);
        return ResponseEntity.ok(ApiResponse.success("이메일 인증 전송이 완료되었습니다."));
    }


    // 이메일 인증
    @GetMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestParam("token") String token) {
        try {
            authService.verifyUser(token);
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create(successUrl))
                    .build();
        } catch (CustomException e) {
            String encodedMessage = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);

            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create(failUrl + "?message=" + encodedMessage))
                    .build();
        }
    }

    // 유저 로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest LoginRequest
    ) {
        LoginResponse response = authService.login(LoginRequest);
        return ResponseEntity.ok(ApiResponse.success("로그인에 성공 했습니다.", response));
    }
}
