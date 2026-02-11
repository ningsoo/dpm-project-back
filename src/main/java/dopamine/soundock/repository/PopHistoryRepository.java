package dopamine.soundock.repository;

import dopamine.soundock.dto.response.PopHistoryResponse;
import dopamine.soundock.entity.PopHistory;
import dopamine.soundock.entity.TossPayment;
import dopamine.soundock.entity.User;
import dopamine.soundock.enums.PopStatus;
import dopamine.soundock.enums.PopTarget;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@EnableJpaRepositories
public interface PopHistoryRepository extends JpaRepository<PopHistory, Integer> {
    Optional<PopHistory> findByOrderIdAndPopStatus(String orderId, PopStatus popStatus);
    // 사용자 구매 내역 조회
    List<PopHistory> findByUserAndPopTargetOrderByCreatedDatetimeDesc(User user, PopTarget popTarget);
    // 사용자 재화 사용 내역 조회
    @EntityGraph(attributePaths = {"board", "relatedUser"})
    List<PopHistory> findByUserAndRequestedDatetimeIsNotNullAndPopTargetInOrderByRequestedDatetimeDescPopHistoryIdDesc(User user, List<PopTarget> targets);

    // 후원 내역 조회(후원자, 수혜자 모두 사용)
    @EntityGraph(attributePaths = {"board", "relatedUser"})
    List<PopHistory> findByUserAndCreatedDatetimeIsNotNullAndPopTargetOrderByCreatedDatetimeDesc(User user, PopTarget targets);

    // 수혜자의 id와 후원 popHistory의 transactionId와 일치하는 내역 조회
    Optional<PopHistory> findByTransactionIdAndPopTarget(String transactionId, PopTarget popTarget);

    // popTarget이 FEATURED_BOARD, boardId와 일치하는 내역 조회
    Optional<PopHistory> findByPopTargetAndBoardBoardId(PopTarget popTarget, Integer boardId);

    //        얘네의 related user가 approvedDatetime이 채워져있고 cancelDatetime이 null인 애들의 approvedDate을 전달해야함
   List<PopHistory> findByTransactionIdIn(List<String> transactionIds);

    String SETTLEMENT_QUERY = "SELECT p FROM PopHistory p " +
            "WHERE p.user.id = :userId " +
            "AND p.popTarget = :popTarget " +
            "AND p.popStatus = :popStatus " +
            "AND p.requestedDatetime IS NULL " +
            "AND p.approvedDatetime IS NULL " +
            "AND p.createdDatetime >= :availableDay " +
            "ORDER BY p.createdDatetime DESC ";

    // 단순 정산 가능 내역 조회 시 사용
    @Query(SETTLEMENT_QUERY)
    List<PopHistory> findAvailableSettlement(
            @Param("userId") Integer userId,
            @Param("popTarget") PopTarget popTarget,
            @Param("popStatus") PopStatus popStatus,
            @Param("availableDay") LocalDateTime availableDay);

    // 정산 가능 내역 조회 후 pop 기록 업데이트 시 사용, 락 설정
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(SETTLEMENT_QUERY)
    List<PopHistory> findAvailableSettlementForUpdate(
            @Param("userId") Integer userId,
            @Param("popTarget") PopTarget popTarget,
            @Param("popStatus") PopStatus popStatus,
            @Param("availableDay") LocalDateTime availableDay);

    // 정산 취소하지 않은 정산 요청 중이거나 정산 완료된 내역 조회
    @Query("SELECT p FROM PopHistory p " +
            "WHERE p.user.id = :userId " +
            "AND p.popStatus IN :status " +
            "AND p.canceledDatetime IS NULL ")
    List<PopHistory> findMySettlementList(
            @Param("userId") Integer userId,
            @Param("status") List<PopStatus> popStatuses);

    // 정산 요청 내역 일괄 업데이트 메서드
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PopHistory p SET p.popStatus = :popStatus, " +
            "p.requestedDatetime = :now " +
            "WHERE p.popHistoryId IN :ids")
    void updateSettlementPopHistory(
            @Param("popStatus") PopStatus popStatus,
            @Param("now") LocalDateTime now,
            @Param("ids") List<Integer> ids);


    // DONATION : 3일 경과 시 PENDING -> COMPLETED 변경
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PopHistory p SET p.popStatus = 'COMPLETED', p.approvedDatetime = :now " +
            "WHERE p.popStatus = 'PENDING' AND p.popTarget = 'DONATION' " +
            "AND p.createdDatetime <= :updateTime")
    int updateDonation(@Param("now")LocalDateTime now, @Param("updateTime")LocalDateTime updateTime);


    // FEATURED_BOARD : 10분 경과 시 PENDING -> COMPLETED 변경
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PopHistory p SET p.popStatus = 'COMPLETED', p.approvedDatetime = :now " +
            "WHERE p.popStatus = 'PENDING' AND p.popTarget = 'FEATURED_BOARD' " +
            "AND p.createdDatetime <= :updateTime")
    int updateBoard(@Param("now") LocalDateTime now, @Param("updateTime") LocalDateTime updateTime);


    // RECEIVED : 3일 경과 된 RECEIVED 조회 (얘는 리스트 조회 후 스케쥴러 내부에서 상태변경 진행)
    @Query("SELECT p FROM PopHistory p JOIN FETCH p.user " +
            "WHERE p.popStatus = 'PENDING' AND p.popTarget = 'RECEIVED' " +
            "AND p.createdDatetime <= :updateTime")
    List<PopHistory> findPendingReceived(@Param("updateTime") LocalDateTime updateTime);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PopHistory p SET p.popStatus = :status, p.approvedDatetime = :now, p.popTarget = :target WHERE p.popHistoryId = :id")
    int updateStatusAfterTossPay(
            @Param("id") Integer id,
            @Param("status") PopStatus status,
            @Param("now") LocalDateTime now,
            @Param("target") PopTarget target
    );
}

