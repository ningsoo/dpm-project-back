package dopamine.soundock.service;

import dopamine.soundock.dto.*;
import dopamine.soundock.entity.RefreshToken;
import dopamine.soundock.entity.User;
import dopamine.soundock.entity.VerificationToken;
import dopamine.soundock.enums.UserRole;
import dopamine.soundock.enums.UserStatus;
import dopamine.soundock.exceptions.*;
import dopamine.soundock.global.TokenProvider;
import dopamine.soundock.repository.RefreshTokenRepository;
import dopamine.soundock.repository.UserRepository;
import dopamine.soundock.repository.VerificationTokenRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final VerificationTokenRepository  verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenProvider tokenProvider;

    private static final long REFRESH_TOKEN_VALIDITY = 1000 * 60 * 60 * 24;


    // 이메일 중복 확인
    @Transactional(readOnly = true)
    public void validateEmail(ValidateEmailRequest validateEmailRequest) {
        // 중복 일 경우 예외 발생
        if (userRepository.existsByEmail(validateEmailRequest.getEmail())) {
            throw new DuplicateEmailException();
        }
    }

    // 닉네임 중복 확인
    @Transactional(readOnly = true)
    public void validateNickname(ValidateNicknameRequest validateNicknameRequest) {
        // 중복 일 경우 예외 발생
        if (userRepository.existsByNickname(validateNicknameRequest.getNickname())) {
            throw new DuplicateNicknameException();
        }
    }

    // 회원가입
    @Transactional
    public void signupUser(UserSignupRequest userSignupRequest, String siteURL) {
        String encodedPassword = passwordEncoder.encode(userSignupRequest.getPassword());
        // 이메일 중복 체크
        if (userRepository.existsByEmail(userSignupRequest.getEmail())) {
            throw new DuplicateEmailException();
        }

        // 닉네임 중복 체크
        if (userRepository.existsByNickname(userSignupRequest.getNickname())) {
            throw new DuplicateNicknameException();
        }

        // 숫자 이외의 모든 문자 제거
        String fixPhone = userSignupRequest.getPhoneNumber().replaceAll("[^0-9]", "");

        // DB 컬럼 길이와 일치하는지 최종 확인
        if (fixPhone.length() != 11) {
            throw new CustomException("연락처 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        // builder 패턴을 통해 좀 더 깔끔하게 수정
        User user = User
                .builder()
                .email(userSignupRequest.getEmail())
                .password(encodedPassword)
                .nickname(userSignupRequest.getNickname())
                .phoneNumber(fixPhone)
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .build();

        userRepository.save(user);

        VerificationEmailRequest emailRequest = new VerificationEmailRequest(user.getEmail());
        sendVerificationEmail(emailRequest, siteURL);
    }
    // 이메일 인증 토큰 생성
    @Transactional
    private void createVerificationToken(User user, String token) {
        // 기존 토큰이 있으면 삭제
        verificationTokenRepository.deleteByUser(user);

        VerificationToken verificationToken = VerificationToken
                .builder()
                .user(user)
                .token(token)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        verificationTokenRepository.save(verificationToken);
    }

    // 이메일 전송
    @Transactional
    public void sendVerificationEmail(VerificationEmailRequest verificationEmailRequest, String siteURL) {
        try {
            User user = userRepository.findByEmail(verificationEmailRequest.getEmail())
                    .orElseThrow(() -> new CustomException("존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND));

            String token = UUID.randomUUID().toString();
            createVerificationToken(user, token);

            String recipientAddress = verificationEmailRequest.getEmail();
            String subject = "이메일 인증 요청";
            String verificationUrl = siteURL + "/api/auth/verify?token=" + token;

            Context context = new Context();
            context.setVariable("verificationUrl", verificationUrl);
            String htmlContent = templateEngine.process("verification-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(recipientAddress);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("이메일 발송 실패 - 수신자: {}, 원인: {}"
                    , verificationEmailRequest.getEmail(), e.getMessage(), e);
            throw new MailSendingException();
        }
    }

    // 이메일 원클릭 인증
    @Transactional
    public void verifyUser(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new CustomException("유효하지 않은 토큰입니다.", HttpStatus.BAD_REQUEST));

        if (verificationToken.isVerified()) {
            throw new CustomException("이미 인증이 완료된 링크입니다.", HttpStatus.CONFLICT);
        }

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new CustomException("인증 시간이 만료되었습니다. 다시 시도해주세요.", HttpStatus.GONE);
        }

        User user = verificationToken.getUser();
        user.setStatus(UserStatus.ACTIVE);
        verificationToken.setVerified(true);
        userRepository.save(user);
        verificationTokenRepository.save(verificationToken);
    }

    public LoginResponse login(
            LoginRequest loginRequest
    ) {
        // 1. JPA를 이용해 DB에 ID를 조회해서 있는 애인지 확인한다.
        User user = userRepository.findByEmail(loginRequest.getEmail())
            .orElseThrow(() -> new ResourceNotFoundException("유저를 찾을 수 없습니다."));

        if(!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            // 실패하면 401 에러 보냄
            throw new LoginFailedException("비밀번호가 맞지 않습니다.");
        }

        if(!user.getStatus().equals(UserStatus.ACTIVE)) {
            throw new LoginFailedException("인증이 완료되지 않은 유저입니다.");
        }

        // 로그인 성공
        String accessToken = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());


        // Refresh Token을 DB에 추가
        RefreshToken refresh = RefreshToken
                .builder()
                .token(refreshToken)
                .user(user)
                .expirationAt(LocalDateTime.now().plusSeconds(REFRESH_TOKEN_VALIDITY/1000))
                .build();

        refreshTokenRepository.save(refresh);

        return new LoginResponse(accessToken, refreshToken);
    }
}
