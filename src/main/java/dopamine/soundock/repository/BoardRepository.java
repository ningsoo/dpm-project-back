package dopamine.soundock.repository;

import dopamine.soundock.entity.Board;
import dopamine.soundock.enums.CategoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@EnableJpaRepositories
public interface BoardRepository extends JpaRepository<Board, Integer> {
    // 카테고리-boardId에 해당하는 삭제되지 않은 게시글 조회
    Optional<Board> findByBoardIdAndCategoryCategoryType(Integer boardId, CategoryType categoryType);
    // boardId에 해당하는 삭제된 게시글 조회
    Optional<Board> findByBoardIdAndDeletedDateTimeIsNullAndCategoryCategoryType(Integer boardId, CategoryType categoryType);

    Optional<Board> findByBoardIdAndDeletedDateTimeIsNull(Integer boardId);
    // 카테고리의 삭제되지 않은 게시글 조회

    List<Board> findByDeletedDateTimeIsNullAndCategoryCategoryType(CategoryType categoryType, Pageable pageable);
    @Modifying
    @Query("UPDATE Board b SET b.likes = b.likes + 1 WHERE b.boardId = :boardId")
    void increaseLikes(@Param("boardId") Integer boardId);

    @Modifying
    @Query("UPDATE Board b SET b.likes = b.likes - 1 WHERE b.boardId = :boardId")
    void decreaseLikes(@Param("boardId") Integer boardId);

    // 쿼리 실행 후 영속성 컨텍스트를 비워 데이터 불일치 방지
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Board b SET b.views = b.views + 1 WHERE b.boardId = :id")
    void incrementViews(@Param("id") Integer boardId);

    @Query("SELECT b FROM Board b " +
            "WHERE b.category.categoryType = :categoryType " +
            "AND b.deletedDateTime IS NULL " +
            "AND (" +
            " (:type = 'title' AND b.title Like CONCAT('%', :keyWord, '%')) OR " +
            " (:type = 'nickname' AND b.user.nickname = :keyWord)" + ")")
    Page<Board> searchBoardsByKeywords(@Param("categoryType") CategoryType categoryType, @Param("type") String searchType, @Param("keyWord") String searchKeyWord, Pageable pageable);
}