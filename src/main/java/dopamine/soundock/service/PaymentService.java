package dopamine.soundock.service;

import dopamine.soundock.dto.request.CancelPaymentRequest;
import dopamine.soundock.dto.request.ConfirmPaymentRequest;
import dopamine.soundock.dto.response.ConfirmPaymentResponse;
import dopamine.soundock.dto.request.PreparePaymentRequest;
import dopamine.soundock.entity.PopHistory;
import dopamine.soundock.entity.TossPayment;
import dopamine.soundock.entity.User;
import dopamine.soundock.enums.PopStatus;
import dopamine.soundock.enums.PopTarget;
import dopamine.soundock.exceptions.ResourceNotFoundException;
import dopamine.soundock.repository.PopHistoryRepository;
import dopamine.soundock.repository.TossPaymentRepository;
import dopamine.soundock.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class PaymentService {
    private final WebClient tossWebClient;
    private final PopHistoryRepository popHistoryRepository;
    private final TossPaymentRepository tossPaymentRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    // 결제 주문 정보 생성
    @Transactional
    public PreparePaymentRequest preparePayment(PreparePaymentRequest prepareRequest){

         // 결제 시도자가 로그인한 유저인지 검증
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        // 토스에게 줄 orderId 생성
        String orderId = UUID.randomUUID().toString();

        // 우리 DB에 기록할 주문 정보
        PopHistory popHistory = PopHistory.createPendingHistory(user, orderId, prepareRequest);
        popHistoryRepository.save(popHistory);

        return PreparePaymentRequest.builder()
                .orderId(orderId)
                .amount(prepareRequest.getAmount())
                .build();
    }

    // 결제 승인 요청
    public ConfirmPaymentResponse confirmPayment(ConfirmPaymentRequest confirmRequest) {

        // 1. // 결제 시도자가 로그인한 유저인지 검증
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        PopHistory popHistory = popHistoryRepository.findByOrderIdAndPopStatus(confirmRequest.getOrderId(), PopStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("주문 Id가 일치하지 않는 결제 요청입니다."));

        if (!Objects.equals(popHistory.getActualAmount(), confirmRequest.getAmount())) {
            throw new ResourceNotFoundException("결제 요청 금액과 일치하지 않습니다.");
        }

        // 2. Toss API 호출
        ConfirmPaymentResponse confirmPaymentResponse =
                tossWebClient.post()
                        .uri("/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(confirmRequest)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(
                                                new ResourceNotFoundException("요청을 처리할 수 없습니다.")
                                        ))
                        )
                        .onStatus(HttpStatusCode::is5xxServerError,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(
                                                new IllegalArgumentException("토스 서버 내부에서 오류가 발생했습니다."))
                                        ))
                        .bodyToMono(ConfirmPaymentResponse.class)
                        .block();

        if (confirmPaymentResponse == null) {
            throw new IllegalArgumentException("토스 결제 승인 응답이 비어 있습니다.");
        }
        if (!"DONE".equals(confirmPaymentResponse.getStatus())) {
            throw new IllegalArgumentException("결제가 완료되지 않았습니다. 상태: " + confirmPaymentResponse.getStatus());
        }

        // 3. Toss 결제 완료 후 DB 업데이트
        try {
            return transactionTemplate.execute(status -> {
                // 1. popHistory ID 가져오기
                Integer historyId = popHistory.getPopHistoryId();

                // 2. popHistory 업데이트
                int updatedCount = popHistoryRepository.updateStatusAfterTossPay(
                        historyId,
                        PopStatus.COMPLETED,
                        LocalDateTime.now(),
                        PopTarget.CHARGE
                );

                if (updatedCount == 0) {
                    throw new RuntimeException("DB 업데이트 실패: ID를 찾을 수 없음");
                }

                // 3. TossPayment 저장
                TossPayment tossPayment = confirmPaymentResponse.toEntity(popHistory);
                tossPaymentRepository.save(tossPayment);

                // 4. 유저 pop Balance 증가
                userRepository.increasePopBalance(user.getEmail(), popHistory.getChangeAmount());

                return confirmPaymentResponse;
            });
        } catch (Exception e) {
            log.error("에러발생: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    // PaymentKey를 통한 승인된 결제 조회
    public ConfirmPaymentResponse getPaymentByKey(String paymentKey){

        // 결제 내역 조회자가 로그인한 유저인지 검증
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));


        TossPayment tossPayment = tossPaymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 요청입니다. PaymentKey를 확인해주세요."));

        // 승인된 결제에 대해서만 조회 가능
        if (tossPayment.getApprovedDatetime() == null){
            throw new IllegalArgumentException("승인 완료되지 않은 결제입니다.");
        }

        return tossWebClient.get()
                .uri("/payments/{paymentKey}", paymentKey)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ResourceNotFoundException("요청을 처리할 수 없습니다.")
                                ))
                )
                .onStatus(HttpStatusCode::is5xxServerError,
                        clientResponse -> Mono.error(
                                new IllegalArgumentException("토스 서버 내부에서 오류가 발생했습니다.")
                        ))
                .bodyToMono(ConfirmPaymentResponse.class)
                .block();
    }

    // orderId를 통한 승인된 결제 조회
    public ConfirmPaymentResponse getPaymentById(String orderId) {

        // 결제 내역 조회자가 로그인한 유저인지 검증
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        TossPayment tossPayment = tossPaymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문번호와 일치하는 주문 내역이 없습니다."));

        if (tossPayment.getApprovedDatetime() == null){
            throw new IllegalArgumentException(("승인 완료되지 않은 결제입니다."));
        }

        return tossWebClient.get()
                .uri("/payments/orders/{orderId}", orderId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new ResourceNotFoundException("요청을 처리할 수 없습니다."))
                                ))
                .onStatus(HttpStatusCode::is5xxServerError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IllegalArgumentException("토스 서버 내부에서 오류가 발생했습니다."))
                                ))
                .bodyToMono(ConfirmPaymentResponse.class)
                .block();
    }
    // 결제 취소
    public ConfirmPaymentResponse cancelPayment(
            String paymentKey,
            CancelPaymentRequest cancelPaymentRequest
    ){
        // 결제 취소 시도자가 로그인한 유저인지 검증
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        if (cancelPaymentRequest == null){
            throw new IllegalArgumentException("결제 취소 사유를 입력해주세요.");
        }

        // [트랜잭션 A] 유저 포인트 선 차감
        String idempotencyKey = transactionTemplate.execute(status -> {
            // 유효한 paymentKey 값인지 확인
            TossPayment tossPayment = tossPaymentRepository.findByPaymentKey(paymentKey)
                    .orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 PaymentKey입니다."));

            // 토스 페이먼츠 객체 결제 상태 확인(취소 상태면 예외처리)
            if ("CANCELED".equals(tossPayment.getTossPaymentStatus())) {
                throw new IllegalArgumentException("이미 취소된 결제 내역입니다.");
            }

            // toss orderId와 일치하는 결제 완료 상태인 PopHistory 조회
            PopHistory popHistory = popHistoryRepository.findByOrderIdAndPopStatus(tossPayment.getOrderId(), PopStatus.COMPLETED)
                    .orElseThrow(() -> new ResourceNotFoundException("취소할 주문 내역이 존재하지 않습니다."));

            // 사용자 재화 잔여량 차감 시도
            // DB 업데이트 완료 결과값이 0으로 반환될 경우 재화가 0인 상태임. 예외처리(재화 업데이트 처리하지않음)
            int updatedRow = userRepository.decreasePopBalance(user.getEmail(), popHistory.getChangeAmount());
            if (updatedRow == 0){
                throw new IllegalArgumentException("이미 모두 소모된 재화입니다. 현재 차감할 재화가 없습니다.");
            }

            // 멱등키 존재 여부 확인
            if (tossPayment.getCancelId() != null) {
                // 이미 생성된 멱등키 사용
                log.info("기존 멱등키 사용 : {}", tossPayment.getCancelId());
                return tossPayment.getCancelId();
            } else {
                // 새로운 멱등키 생성
                String newKey = UUID.randomUUID().toString();
                // 멱등키 DB로 저장
                tossPayment.setCancelId(newKey);
                tossPaymentRepository.save(tossPayment);
                log.info("새 멱등키 생성 및 저장 : {}", newKey);
                return newKey;
            }
        });

        // 토스 api를 통해 토스 객체 받음
        ConfirmPaymentResponse cancelPaymentResponse;
        try {
            cancelPaymentResponse =
                    tossWebClient.post()
                            .uri("/payments/{paymentKey}/cancel", paymentKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", idempotencyKey)
                            .bodyValue(cancelPaymentRequest)
                            .retrieve()
                            .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                                    clientResponse.bodyToMono(String.class)
                                            .flatMap(body -> Mono.error(new IllegalArgumentException("토스 에러 : " + body))
                                            ))
                            .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                                    clientResponse.bodyToMono(String.class)
                                            .flatMap(body -> Mono.error(new IllegalArgumentException("토스 에러 : " + body))
                                            ))
                            .bodyToMono(ConfirmPaymentResponse.class)
                            .block();
        } catch (Exception e) {
            // 토스 요청 실패의 경우
            transactionTemplate.execute(status -> {
                TossPayment tossPayment = tossPaymentRepository.findByPaymentKey(paymentKey).orElseThrow();
                PopHistory popHistory = popHistoryRepository.findByOrderIdAndPopStatus(tossPayment.getOrderId(), PopStatus.COMPLETED).orElseThrow();
                // 유저 pop Balance 복구
                userRepository.increasePopBalance(user.getEmail(), popHistory.getChangeAmount());
                return null;
            });
            throw e;
        }

        // paymentKey 값을 통해 찾은 tossPayment
        if (cancelPaymentResponse == null){
            throw new NullPointerException("결제 취소 내역에 대한 정보를 전달 받지 못했습니다.");
        }

        // [트랙잭션 B] 위의 트랜잭션 결과 반영 (취소 성공). PopHistory 취소 내역 생성
        return transactionTemplate.execute(status -> {
            // 새로운 트랜잭션에 진입해서 다시 tossPaymentKey로 조회
            TossPayment tossPayment = tossPaymentRepository.findByPaymentKey(paymentKey)
                    .orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 PaymentKey입니다."));

            // 취소 내역 DB 업데이트 후 저장
            tossPayment.cancelUpdatePayment(cancelPaymentResponse);
            tossPaymentRepository.save(tossPayment);

            // 결제 완료되어있는 PopHistory 조회
            PopHistory originHistory = popHistoryRepository.findByOrderIdAndPopStatus(tossPayment.getOrderId(), PopStatus.COMPLETED).orElseThrow();

            // cancel PopHistory 내역 새로 생성
            PopHistory cancelPopHistory = PopHistory.createCancelHistory(user, originHistory);
            popHistoryRepository.save(cancelPopHistory);

            return cancelPaymentResponse;
        });

    }
}
