package dopamine.soundock.service;

import dopamine.soundock.dto.request.CancelUsedPopRequest;
import dopamine.soundock.dto.response.PaymentHistoryResponse;
import dopamine.soundock.dto.response.PopHistoryResponse;
import dopamine.soundock.entity.Board;
import dopamine.soundock.entity.PopHistory;
import dopamine.soundock.entity.TossPayment;
import dopamine.soundock.entity.User;
import dopamine.soundock.enums.CategoryType;
import dopamine.soundock.enums.PopStatus;
import dopamine.soundock.enums.PopTarget;
import dopamine.soundock.exceptions.InvalidCancelFeaturedBoardException;
import dopamine.soundock.exceptions.ResourceNotFoundException;
import dopamine.soundock.global.constants.AppConstants;
import dopamine.soundock.repository.BoardRepository;
import dopamine.soundock.repository.PopHistoryRepository;
import dopamine.soundock.repository.TossPaymentRepository;
import dopamine.soundock.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopService {
    private final UserRepository userRepository;
    private final PopHistoryRepository popHistoryRepository;
    private final BoardRepository boardRepository;
    private final TossPaymentRepository tossPaymentRepository;

    // 재화 구매(충전) 내역 조회
    public List<PaymentHistoryResponse> getPaymentHistory(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        // popHistory 내역에서 사용자에 대한 정보 조회
        List<PopHistory> results = popHistoryRepository.findByUserAndPopTargetOrderByCreatedDatetimeDesc(user, PopTarget.CHARGE);

        // 재화 구매(충전) 내역 유무 확인
        if (results.isEmpty()){
            return new ArrayList<>();
        }

        List<String> orderIds = new ArrayList<>();
        for (PopHistory history : results) {
            orderIds.add(history.getOrderId());
        }

        List<TossPayment> payments = tossPaymentRepository.findByOrderIdIn(orderIds);

        Map<String, String> paymentMap = new HashMap<>();
        for (TossPayment payment : payments) {
            paymentMap.put(payment.getOrderId(), payment.getPaymentKey());
        }

        // 엔티티 정보를 받을 response 배열 생성
        List<PaymentHistoryResponse> paymentHistoryResponses = new ArrayList<>();

        for (PopHistory popHistory : results){
            LocalDateTime expiredDatetime =
                    popHistory.getCreatedDatetime().plusYears(AppConstants.Time.POP_HISTORY_EXPIRATION_YEARS);
            // 구매 취소 여부
            boolean isCanceled = popHistory.getCanceledDatetime() != null;
            String key = paymentMap.get(popHistory.getOrderId());

            // popHistory 내역들 dto로 전환
            PaymentHistoryResponse response = PaymentHistoryResponse.builder()
                    .popHistoryId(popHistory.getPopHistoryId())
                    .orderId(popHistory.getOrderId())
                    .paymentKey(key)
                    .target(popHistory.getPopTarget())
                    .isCanceled(isCanceled)
                    .changeAmount(popHistory.getChangeAmount())
                    .actualAmount(popHistory.getActualAmount())
                    .createdDatetime(popHistory.getCreatedDatetime())
                    .expiredDatetime(expiredDatetime)
                    .build();
            paymentHistoryResponses.add(response);
        }
        return paymentHistoryResponses;
    }

    // 재화 사용 내역 조회
    public List<PopHistoryResponse> getPopUsageHistory(){
        // 로그인한 유저 확인
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        // popHistory 내역에서 사용자에 대한 정보 조회
        // requested_at이 채워져있으면 사용한다고 요청이 들어온 상태
        // target이 DONATION, FEATURED BOARD인 경우만 보여주기 위한 리스트
        List<PopTarget> targets = List.of(PopTarget.DONATION, PopTarget.FEATURED_BOARD);
        List<PopHistory> results = popHistoryRepository.findByUserAndRequestedDatetimeIsNotNullAndPopTargetInOrderByRequestedDatetimeDescPopHistoryIdDesc(user, targets);

        // 재화 사용 내역 조회시 내역이 없으면 예외 처리 대신 빈값 전달(throw new Resource~Exception 제거 코드 제거)

        // 2. 최신 내역만 담을 Map (LinkedHashMap은 정렬 순서를 보존)
        Map<String, PopHistory> filteredMap = new LinkedHashMap<>();

        for (PopHistory popHistory : results) {
            // 홍보는 boardId, 후원은 transactionId를 키로 사용(같은 값 구분)
            String key = (popHistory.getPopTarget() == PopTarget.FEATURED_BOARD)
                    ? "BOARD_" + popHistory.getBoard().getBoardId()
                    : "TX_" + popHistory.getTransactionId();

            // 최신순으로 정렬되어 있으므로, 처음 발견된 키가 가장 최신 상태의 데이터
            if (!filteredMap.containsKey(key)) {
                filteredMap.put(key, popHistory);
            }
        }

        List<PopHistoryResponse> responses = new ArrayList<>();

        for (PopHistory popHistory : filteredMap.values()) {
            PopHistoryResponse.RelatedInfo related = PopHistoryResponse.createRelatedInfo(popHistory);

            // 재화 사용 내역 popHistory dto로 전환
            // 사용일시, 사용수량, 사용내용(target), 사용대상(boardId, related_user)
            PopHistoryResponse popHistoryResponse = PopHistoryResponse.builder()
                    .userId(user.getId())
                    .popHistoryId(popHistory.getPopHistoryId())
                    .createdDatetime(popHistory.getCreatedDatetime())
                    .popStatus(popHistory.getPopStatus())
                    .requestedDatetime(popHistory.getRequestedDatetime())
                    .approvedDatetime(popHistory.getApprovedDatetime())
                    .cancelDatetime(popHistory.getCanceledDatetime())
                    // Math.abs 사용으로 DB는 그대로 "- 저장" 하고 프론트에 주는 DTO 값만 양수(절대값)로 수정 후 전달
                    .changeAmount(Math.abs(popHistory.getChangeAmount()))
                    .popTarget(popHistory.getPopTarget())
                    .related(related)
                    .build();
            responses.add(popHistoryResponse);
        }
        return responses;
    }

    // 재화(홍보 게시글 등록에 사용한) 사용 취소
    @Transactional
    public void cancelUsedPop(CancelUsedPopRequest cancelRequest) {
        // 로그인한 유저 확인
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        // 취소 요청자 id와 현재 로그인 유저가 동일한지 검증
        if (!user.getId().equals(cancelRequest.getUserId())){
            throw new IllegalArgumentException("현재 사용자의 재화 사용 내역과 일치하지 않습니다.");
        }
        // 홍보 카테고리에 등록된 게시글이 맞는지 검증
        Board board = boardRepository.findByBoardIdAndCategoryCategoryType(cancelRequest.getBoardId(), CategoryType.SPOTLIGHT)
                .orElseThrow(() -> new ResourceNotFoundException("SPOTLIGHT에 등록된 게시글이 아닙니다. 해당 게시글에 대해 재화 사용 취소를 할 수 없습니다."));

        // 게시글 작성자와 로그인한 유저가 일치하는지 검증
        if (!board.getUser().getId().equals(user.getId())){
            throw new IllegalArgumentException("게시글 작성자와 사용자 정보와 일치하지 않습니다.");
        }

        // 이미 홍보 만료 기간이 지난 게시글인지 검증
        if (board.getFeaturedExpiredDateTime().isBefore(LocalDateTime.now())){
            throw new IllegalArgumentException("이미 홍보가 완료된 게시글입니다. 취소 요청이 불가합니다.");
        }

        // popHistory 중 popTarget이 FEATURED_BOARD와 일치하는 boardId 내역 조회
        PopHistory usedPop = popHistoryRepository.findByPopTargetAndBoardBoardId(PopTarget.FEATURED_BOARD, board.getBoardId())
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글에 대한 게시글 등록 상품 구매 내역을 찾을 수 없습니다."));

        // 게시글 작성 시각 10분 이내인 경우만 환불
        if (LocalDateTime.now()
                .isAfter(board.getCreatedDateTime().plusMinutes(AppConstants.Time.AVAILABLE_REQUEST_CANCEL_MINUTES))){
            throw new InvalidCancelFeaturedBoardException("게시글 등록 후 10분 이내인 경우만 재화 환불이 가능합니다.");
        }
        // 유저 popBalance 업데이트
        userRepository.increasePopBalance(user.getEmail(), Math.abs(usedPop.getChangeAmount()));

        LocalDateTime now = LocalDateTime.now();

        // 소모한 재화 반환, popHistory 내역 생성
        PopHistory popHistory = PopHistory.builder()
                .changeAmount(usedPop.getChangeAmount())
                .popStatus(PopStatus.CANCELED)
                .popTarget(PopTarget.FEATURED_BOARD)
                .requestedDatetime(usedPop.getRequestedDatetime())
                .createdDatetime(now)
                .canceledDatetime(now)
                .board(board)
                .user(user)
                .transactionId(usedPop.getTransactionId())
                .build();

        popHistoryRepository.save(popHistory);
    }
}
