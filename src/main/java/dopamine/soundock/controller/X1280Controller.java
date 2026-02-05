package dopamine.soundock.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dopamine.soundock.dto.RestResponse;
import dopamine.soundock.dto.request.PWLRequest;
import dopamine.soundock.dto.response.PWLTokenResponse;
import dopamine.soundock.service.X1280Service;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class X1280Controller {
    private final X1280Service service;
    private final ObjectMapper objectMapper; // JSON 문자열 파싱용


    // React calls: GET /api/v1/auth/status?user=...
    @GetMapping("/status")
    public ResponseEntity<RestResponse<JsonNode>> checkUserStatus(@RequestParam("userId") String email) throws JsonProcessingException {
        String resultString = service.isAp(email);
        return processResponse(resultString, "패스워드리스 가입 여부 확인이 성공했습니다.");
    }

    // React calls: POST /api/v1/auth/register
    @PostMapping("/register")
    public ResponseEntity<RestResponse<JsonNode>> registerUser(@RequestBody PWLRequest pwlRequest) throws JsonProcessingException {
        String resultString = service.joinAp(pwlRequest.getEmail());
        return processResponse(resultString, "QR 생성이 완료되었습니다.");
    }

    // React calls: POST /api/v1/auth/login-trigger
    @PostMapping("/login-trigger")
    public ResponseEntity<RestResponse<JsonNode>> triggerLogin(@RequestParam("userId") String email, HttpServletRequest request) throws JsonProcessingException {
        // This handles the internal getToken -> AES -> getSp sequence privately
        String clientIp = request.getRemoteAddr();
        String sessionId = UUID.randomUUID().toString();
        String resultString = service.getSp(email, clientIp, sessionId);
        return processGetApResponse(resultString, "인증 푸시 전송이 성공했습니다.", sessionId);
    }

    // New: Cancel auth
    @PostMapping("/cancel")
    public ResponseEntity<RestResponse<JsonNode>> cancel(@RequestParam("userId") String email, @RequestParam String sessionId) throws JsonProcessingException {
        String resultString = service.cancel(email, sessionId);
        return processResponse(resultString, "인증 요청이 성공적으로 취소되었습니다.");
    }

    // New: Withdrawal
    @PostMapping("/withdrawal")
    public ResponseEntity<RestResponse<JsonNode>> withdrawal(@RequestParam("userId") String email) throws JsonProcessingException {
        String resultString = service.withdrawalAp(email);
        return processResponse(resultString, "서비스 탈퇴가 정상적으로 처리되었습니다.");
    }

    // 패스워드리스 로그인
    @PostMapping("/login-pwl")
    public ResponseEntity<RestResponse<PWLTokenResponse>> confirmLogin(@RequestBody PWLRequest pwlRequest) {
        PWLTokenResponse response = service.verifyAndGenerateTokens(pwlRequest.getEmail(), pwlRequest.getSessionId());
        return ResponseEntity.ok(RestResponse.success("로그인이 성공했습니다.", response));
    }

    // 공용 로직
    private ResponseEntity<RestResponse<JsonNode>> processResponse(String resultString, String successMsg) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(resultString);

        if (root.path("result").asBoolean()) {
            JsonNode dataNode = root.path("data");
            return ResponseEntity.ok(RestResponse.success(successMsg, dataNode));
        } else {
            String errorMsg = root.path("msg").asText();
            return ResponseEntity.ok(RestResponse.fail(errorMsg));
        }
    }

    private ResponseEntity<RestResponse<JsonNode>> processGetApResponse(String resultString, String successMsg, String sessionId) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(resultString);

        if (root.path("result").asBoolean()) {
            JsonNode dataNode = root.path("data");

            // sessionId가 있을 때 추가로 넣음
            if (dataNode.isObject() && sessionId != null) {
                ((ObjectNode) dataNode).put("sessionId", sessionId);
            }

            return ResponseEntity.ok(RestResponse.success(successMsg, dataNode));
        } else {
            String errorMsg = root.path("msg").asText();
            return ResponseEntity.ok(RestResponse.fail(errorMsg));
        }
    }
}
