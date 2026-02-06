package dopamine.soundock.service;

import dopamine.soundock.dto.request.BoardCreateRequest;
import dopamine.soundock.dto.response.BoardResponse;
import dopamine.soundock.entity.*;
import dopamine.soundock.enums.CategoryType;
import dopamine.soundock.exceptions.AuthRejectedException;
import dopamine.soundock.exceptions.ResourceNotFoundException;
import dopamine.soundock.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class BoardService {
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BoardLikeRepository boardLikeRepository;
    private final ViewService viewService;
    private final EntityManager entityManager;

    // 게시글 작성
    @Transactional
    public int createBoard(CategoryType categoryType, BoardCreateRequest createRequest) {
        // 사용자 로그인 확인
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));
        // 카테고리 입력값 검증
       Category category = categoryRepository.findByCategoryType(categoryType)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 카테고리입니다."));

        // 프론트에서 받은 입력값 보여줌
        Board board = new Board();
        board.setTitle(createRequest.getTitle());
        board.setContent(createRequest.getContent());
        board.setCategory(category);
        board.setUser(user);

        // save는 새로운 행을 만들면서 데이터 저장
        Board newBoard = boardRepository.save(board);
        return newBoard.getBoardId();
    }

    // 게시글 상세 조회
    @Transactional
    public BoardResponse getDetailBoard(Integer boardId, String email, String clientIp) {
        // boardId에 해당하는 삭제되지 않은 게시글인지 확인
        Board board = boardRepository.findByBoardIdAndDeletedDateTimeIsNull(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 카테고리에서 게시글을 찾을 수 없거나 삭제된 게시글입니다."));

        boolean isLiked = false;

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            Optional<LikeBoard> existingLike = boardLikeRepository.findByUserAndBoard(user, board);
            isLiked = existingLike.isPresent();
        }
            if (viewService.checkView(boardId, email, clientIp)) {
                boardRepository.incrementViews(boardId);
            }

            BoardResponse boardResponse = BoardResponse.builder()
                    .userId(board.getUser().getId())
                    .boardId(board.getBoardId())
                    .title(board.getTitle())
                    .nickname(board.getUser().getNickname())
                    .content(board.getContent())
                    .views(board.getViews())
                    .likes(board.getLikes())
                    .isLiked(isLiked)
                    .countComment(board.getCountComment())
                    .createdDateTime(board.getCreatedDateTime())
                    .build();

            return boardResponse;
    }


    // 자자 페이징 처리부터 하자
    // 검색기능까지 라쓰고
    // 한 카테고리 내의 모든 게시글 조회
    public Page<BoardResponse> getBoardsByCategory(CategoryType categoryType, int page, String type, String keyWord) {
        int pageSize = 10;

        String categoryToString = categoryType.name();
        if (List.of("SHOWCASE", "PLAYLISTS", "SPOTLIGHT").contains(categoryToString)) {
            pageSize = 12;
        } else if (List.of("COMMUNITY", "REVIEWS", "NOTICE").contains(categoryToString)){
            pageSize = 15;
        }

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdDateTime").descending());

        String searchType = (type == null) ? "title" : type.toLowerCase(Locale.ROOT).trim();
        String searchKeyWord = (keyWord == null) ? "nickname" : keyWord.toLowerCase(Locale.ROOT).trim();

        Page<Board> boards = boardRepository.searchBoardsByKeywords(categoryType, searchType, searchKeyWord, pageable);
        // 보드에서 얻은 게시글 아이디로 코멘트 레포에서 게시글 id만큼 찾아야함

        if (boards.isEmpty()) {
            throw new ResourceNotFoundException("현재 카테고리에 작성된 게시글이 없습니다.");
        }

        // 게시글 목록 표시
        return boards.map(board -> BoardResponse.builder()
                .boardId(board.getBoardId())
                .userId(board.getUser().getId())
                .title(board.getTitle())
                .nickname(board.getUser().getNickname())
                .createdDateTime(board.getCreatedDateTime())
                .views(board.getViews())
                .likes(board.getLikes())
                .countComment(board.getCountComment())
                .build()
        );
    }

    // 게시글 삭제
    @Transactional
    public void deleteBoard(Integer boardId){
        // 카테고리와 boardId에 해당하는 삭제되지 않은 게시글인지 확인
        Board board = boardRepository.findByBoardIdAndDeletedDateTimeIsNull(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 카테고리에서 게시글을 찾을 수 없거나 삭제된 게시글입니다."));

        // 작성자와 현재 로그인한 유저가 같은지 검사
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        // 로그인한 유저가 작성한 게시글이 있는지 확인
        if (!board.getUser().getId().equals(user.getId())){
            throw new AuthRejectedException("게시글 작성자와 로그인 정보가 일치하지 않습니다.");
        }
        // 게시글 soft Delete
        board.setDeletedDateTime(LocalDateTime.now());
        board.setDeleted(true);
        boardRepository.save(board);

    }
    // 게시글 수정
    public void updateBoard(Integer boardId, BoardCreateRequest updateRequest){
        // 카테고리와 boardId에 해당하는 삭제되지 않은 게시글인지 확인
        Board board = boardRepository.findByBoardIdAndDeletedDateTimeIsNull(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 카테고리에서 게시글을 찾을 수 없거나 삭제된 게시글입니다."));

        // 작성자와 현재 로그인한 유저가 같은지 검사
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        // 로그인한 유저가 작성한 게시글이 있는지 확인
        if (!board.getUser().getId().equals(user.getId())){
            throw new AuthRejectedException("게시글 작성자와 로그인 정보가 일치하지 않습니다.");
        }
        
        // 수정하려는 사항
        if (updateRequest.getTitle() != null){
            board.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getContent() != null){
            board.setContent(updateRequest.getContent());
        }

        // 게시글 수정일 업데이트
        board.setUpdatedDateTime(LocalDateTime.now());
        boardRepository.save(board);
    }

    // 게시글 좋아요
    @Transactional
    public BoardResponse likeBoard(Integer boardId){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        Board board = boardRepository.findByBoardIdAndDeletedDateTimeIsNull(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 카테고리에서 게시글을 찾을 수 없거나 삭제된 게시글입니다."));

        Optional<LikeBoard> existingLike = boardLikeRepository.findByUserAndBoard(user, board);

        boolean isLiked = false;

        if (existingLike.isPresent()){
            boardLikeRepository.delete(existingLike.get());
            boardRepository.decreaseLikes(boardId);
            isLiked = false;
        } else {
            LikeBoard likeboard = new LikeBoard();
            likeboard.setUser(user);
            likeboard.setBoard(board);
            boardLikeRepository.save(likeboard);
            boardRepository.increaseLikes(boardId);
            isLiked = true;
        }

        entityManager.refresh(board);

        BoardResponse boardResponse = BoardResponse.builder()
                .likes(board.getLikes())
                .isLiked(isLiked)
                .build();

        return boardResponse;
    }
}
