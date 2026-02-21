package dopamine.soundock.service;

import dopamine.soundock.dto.request.BoardCreateRequest;
import dopamine.soundock.dto.response.BoardResponse;
import dopamine.soundock.dto.response.FileAttachmentResponse;
import dopamine.soundock.dto.response.FileUploadResponse;
import dopamine.soundock.dto.response.PlaylistItemResponse;
import dopamine.soundock.entity.*;
import dopamine.soundock.enums.CategoryType;
import dopamine.soundock.enums.FileType;
import dopamine.soundock.enums.NotificationType;
import dopamine.soundock.exceptions.AuthRejectedException;
import dopamine.soundock.exceptions.CustomException;
import dopamine.soundock.exceptions.ResourceNotFoundException;
import dopamine.soundock.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BoardService {
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BoardLikeRepository boardLikeRepository;
    private final BoardAttachmentsRepository boardAttachmentsRepository;
    private final ViewService viewService;
    private final S3Service s3Service;
    private final EntityManager entityManager;
    private final Validatorservice attachmentValidator;
    private final PlaylistRepository playlistRepository;
    private final NotificationService notificationService;
    private final RankingService rankingService;

    // 게시글 작성
    @Transactional
    public int createBoard(CategoryType categoryType,
                           BoardCreateRequest createRequest,
                           List<MultipartFile> files) throws IOException {
        // 사용자 로그인 확인
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));


        // 카테고리 입력값 검증
        Category category = categoryRepository.findByCategoryType(categoryType)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 카테고리입니다."));


        // 카테고리별 검증 (Validatorservice 생성)
        attachmentValidator.validateFilesForCategory(categoryType, files, createRequest.getYoutubeUrl());


        // 게시글 생성
        Board board = new Board();
        board.setTitle(createRequest.getTitle());
        board.setContent(createRequest.getContent());
        board.setCategory(category);
        board.setUser(user);
        Board newBoard = boardRepository.save(board);

        // 카테고리별 파일/링크 처리
        if (categoryType == CategoryType.SHOWCASE) {
            // SHOWCASE: YouTube URL만 저장
            if (createRequest.getYoutubeUrl() != null && !createRequest.getYoutubeUrl().isEmpty()) {
                newBoard.setLinkUrl(createRequest.getYoutubeUrl());
                boardRepository.save(newBoard);
            }
        } else if (categoryType == CategoryType.PLAYLISTS) {
            if (createRequest.getPlaylistId() == null) {
                throw new IllegalArgumentException("플레이리스트를 선택해주세요.");
            }

            Playlist playlist = playlistRepository.findById(createRequest.getPlaylistId())
                    .orElseThrow(() -> new ResourceNotFoundException("플레이리스트를 찾을 수 없습니다."));

            if (!playlist.getUser().getId().equals(user.getId())) {
                throw new CustomException("자신의 플레이리스트만 게시할 수 있습니다.", HttpStatus.FORBIDDEN);
            }

            newBoard.setPlaylist(playlist);
            boardRepository.save(newBoard);


        } else if (files != null && !files.isEmpty()) {
            List<FileUploadResponse> uploadedFiles = s3Service.uploadFiles(files);

            // 파일 순서대로 DB에 sequence 부여하며 저장
            for (int i = 0; i < uploadedFiles.size(); i++) {
                FileUploadResponse fileResponse = uploadedFiles.get(i);
                FileType fileType = fileResponse.getIsImage() ? FileType.IMAGE : FileType.FILE;

                BoardAttachments attachment = BoardAttachments.builder()
                        .board(newBoard)
                        .fileUrl(fileResponse.getFileUrl())
                        .fileKey(fileResponse.getFileKey())
                        .fileType(fileType)
                        .originalFilename(fileResponse.getOriginalFilename())
                        .sequence(i)
                        .build();
                boardAttachmentsRepository.save(attachment);
            }
        }

        return newBoard.getBoardId();
    }

    // 게시글 상세 조회
    @Transactional
    public BoardResponse getDetailBoard(Integer boardId, String email, String clientIp) {
        // boardId에 해당하는 삭제되지 않은 게시글인지 확인
        Board board = boardRepository.findByIdWithPlaylist(boardId)
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
            rankingService.incrementHotViewCount(board);
        }

        // 게시글에 연결된 첨부파일 조회
        List<BoardAttachments> attachments = boardAttachmentsRepository.findByBoardOrderBySequenceAsc(board);

        // FileType별로 분리, imageIds 는 게시글 수정시 사용
        List<String> imageUrls = new ArrayList<>();
        List<Integer> imageIds = new ArrayList<>();
        FileAttachmentResponse fileAttachment = null;


        // type이 FILE일 경우 다운로드용 key와 원문제목 내려줌
        for (BoardAttachments att : attachments) {
            switch (att.getFileType()) {
                case IMAGE:
                    imageUrls.add(att.getFileUrl());
                    imageIds.add(att.getBoardAttachmentId());
                    break;
                case FILE:
                    fileAttachment = FileAttachmentResponse.builder()
                            .attachmentId(att.getBoardAttachmentId())
                            .filekey(att.getFileKey())
                            .originalFilename(att.getOriginalFilename())
                            .build();
            }
        }

        Integer playlistId = null;
        String playlistTitle = null;
        List<PlaylistItemResponse> playlistItems = null;

        if (board.getCategory().getCategoryType() == CategoryType.PLAYLISTS && board.getPlaylist() != null) {

            Playlist playlist = board.getPlaylist();
            playlistId = playlist.getPlaylistId();
            playlistTitle = playlist.getTitle();

            playlistItems = playlist.getItems().stream()
                    .map(PlaylistItemResponse::fromEntity)
                    .collect(Collectors.toList());
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
                .imageUrls(imageUrls)
                .imageIds(imageIds)
                .attachment(fileAttachment)
                .linkUrl(board.getLinkUrl())
                .createdDateTime(board.getCreatedDateTime())
                .categoryType(board.getCategory().getCategoryType())
                .playlistId(playlistId)
                .playlistTitle(playlistTitle)
                .playlistItems(playlistItems)
                .isDeleted(board.getUser().isDeleted())
                .build();

        return boardResponse;
    }

    // 한 카테고리 내의 모든 게시글 조회
    public Page<BoardResponse> getBoardsByCategory(CategoryType categoryType, Integer page) {

        int pageSize = switch (categoryType) {
            case SHOWCASE, PLAYLISTS, SPOTLIGHT -> 12;
            case COMMUNITY, REVIEWS, NOTICE -> 15;
            default -> 10;
        };
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdDateTime").descending());



        Page<Board> boards = boardRepository.findByDeletedDateTimeIsNullAndCategoryCategoryType(categoryType, pageable);
        if (boards.getTotalElements() == 0) {
            throw new ResourceNotFoundException("현재 카테고리에 작성된 게시글이 없습니다.");
        }

        // 게시글 목록 표시
        List<BoardResponse> boardResponses = new ArrayList<>();
        for (Board board : boards) {
            // 각 게시글의 첨부파일을 sequence 순으로 조회하여 첫 번째를 배너로 사용
            List<BoardAttachments> attachments = board.getAttachments();
            String imageUrl = null;
            if (!attachments.isEmpty()) {
                imageUrl = attachments.getFirst().getFileUrl();
            }

            // PLAYLIST 썸네일 넣어주기
            if (board.getPlaylist() != null
                    && board.getPlaylist().getThumbnailUrl() != null) {
                imageUrl = board.getPlaylist().getThumbnailUrl();
            }

            // Board.linkUrl이 있으면 우선 사용 (SHOWCASE 썸네일,자동재생용)
            if (board.getLinkUrl() != null && !board.getLinkUrl().isEmpty()) {
                imageUrl = board.getLinkUrl();
            }

            BoardResponse newResponse = BoardResponse.builder()
                    .boardId(board.getBoardId())
                    .title(board.getTitle())
                    .nickname(board.getUser().getNickname())
                    .createdDateTime(board.getCreatedDateTime())
                    .views(board.getViews())
                    .likes(board.getLikes())
                    .countComment(board.getCountComment())
                    .imageUrl(imageUrl)
                    .categoryType(board.getCategory().getCategoryType())
                    .isDeleted(board.getUser().isDeleted())
                    .build();
            boardResponses.add(newResponse);
        }
        return new PageImpl<>(
                boardResponses,
                pageable,
                boards.getTotalElements()
        );
    }

    // 게시글 삭제
    @Transactional
    public void deleteBoard(Integer boardId) {
        // boardId에 해당하는 삭제되지 않은 게시글인지 확인
        Board board = boardRepository.findByBoardIdAndDeletedDateTimeIsNull(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 카테고리에서 게시글을 찾을 수 없거나 삭제된 게시글입니다."));

        // 작성자와 현재 로그인한 유저가 같은지 검사
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        // 로그인한 유저가 작성한 게시글이 있는지 확인
        if (!board.getUser().getId().equals(user.getId())) {
            throw new AuthRejectedException("게시글 작성자와 로그인 정보가 일치하지 않습니다.");
        }

        // 게시글 soft Delete
        board.setDeletedDateTime(LocalDateTime.now());
        board.setDeleted(true);
        boardRepository.save(board);
    }

    // 게시글 수정
    @Transactional
    public void updateBoard(Integer boardId,
                            BoardCreateRequest updateRequest,
                            List<MultipartFile> newFiles,
                            List<Integer> deleteAttachmentIds,
                            List<String> imageOrder) throws IOException {
        // 카테고리와 boardId에 해당하는 삭제되지 않은 게시글인지 확인
        Board board = boardRepository.findByBoardIdAndDeletedDateTimeIsNull(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 카테고리에서 게시글을 찾을 수 없거나 삭제된 게시글입니다."));

        // 작성자와 현재 로그인한 유저가 같은지 검사
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        // 로그인한 유저가 작성한 게시글이 있는지 확인
        if (!board.getUser().getId().equals(user.getId())) {
            throw new AuthRejectedException("게시글 작성자와 로그인 정보가 일치하지 않습니다.");
        }


        // 게시글 본문 수정
        if (updateRequest.getTitle() != null) {
            board.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getContent() != null) {
            board.setContent(updateRequest.getContent());
        }

        if (updateRequest.getYoutubeUrl() != null) {
            board.setLinkUrl(updateRequest.getYoutubeUrl());
        }

        if (updateRequest.getPlaylistId() != null) {
            Playlist playlist = playlistRepository.findById(updateRequest.getPlaylistId())
                    .orElseThrow(() -> new ResourceNotFoundException("플레이리스트를 찾을 수 없습니다."));

            // 플레이리스트 소유자 확인
            if (!playlist.getUser().getId().equals(user.getId())) {
                throw new CustomException("자신의 플레이리스트만 게시할 수 있습니다.", HttpStatus.FORBIDDEN);
            }

            board.setPlaylist(playlist);
        }


        // 1. 삭제 처리: 삭제 요청된 첨부파일 처리
        if (deleteAttachmentIds != null && !deleteAttachmentIds.isEmpty()) {
            List<BoardAttachments> toDelete = boardAttachmentsRepository.findAllById(deleteAttachmentIds);
            for (BoardAttachments attachment : toDelete) {

                if (!attachment.getBoard().getBoardId().equals(boardId)) {
                    throw new AuthRejectedException("다른 게시글의 첨부파일은 삭제할 수 없습니다.");
                }

                s3Service.deleteFile(attachment.getFileKey());
                boardAttachmentsRepository.delete(attachment);
            }
        }

        // 2. 신규 파일 업로드
        Map<String, BoardAttachments> newAttachmentMap = new HashMap<>();
        if (newFiles != null && !newFiles.isEmpty()) {
            List<FileUploadResponse> uploadedFiles = s3Service.uploadFiles(newFiles);
            for (int i = 0; i < uploadedFiles.size(); i++) {
                FileUploadResponse fileResponse = uploadedFiles.get(i);
                FileType fileType = fileResponse.getIsImage() ? FileType.IMAGE : FileType.FILE;
                BoardAttachments attachment = BoardAttachments.builder()
                        .board(board)
                        .fileUrl(fileResponse.getFileUrl())
                        .fileKey(fileResponse.getFileKey())
                        .fileType(fileType)
                        .build();
                BoardAttachments saved = boardAttachmentsRepository.save(attachment);
                // "new_0", "new_1" 형태로 매핑
                newAttachmentMap.put("new_" + i, saved);
            }
        }

        // 3. 순서 재정렬 (imageOrder가 있는 경우만)
        if (imageOrder != null && !imageOrder.isEmpty()) {
            for (int i = 0; i < imageOrder.size(); i++) {
                String orderItem = imageOrder.get(i);
                BoardAttachments attachment;
                if (orderItem.startsWith("existing_")) {
                    // 기존 이미지: "existing_10" -> ID 10번
                    Integer attachmentId = Integer.parseInt(orderItem.replace("existing_", ""));
                    attachment = boardAttachmentsRepository.findById(attachmentId)
                            .orElseThrow(() -> new ResourceNotFoundException("첨부파일을 찾을 수 없습니다."));
                } else if (orderItem.startsWith("new_")) {
                    // 새 이미지: "new_0" -> 방금 업로드한 0번째
                    attachment = newAttachmentMap.get(orderItem);
                    if (attachment == null) {
                        throw new ResourceNotFoundException("새로 업로드된 첨부파일을 찾을 수 없습니다: " + orderItem);
                    }
                } else {
                    throw new IllegalArgumentException("잘못된 imageOrder 형식입니다: " + orderItem);
                }
                // sequence 업데이트
                attachment.setSequence(i);
                boardAttachmentsRepository.save(attachment);
            }
        } else {
            // 4. imageOrder가 없고 삭제/추가가 있었다면 자동으로 sequence 재정렬
            if ((deleteAttachmentIds != null && !deleteAttachmentIds.isEmpty()) ||
                    (newFiles != null && !newFiles.isEmpty())) {

                // 현재 남아있는 모든 첨부파일을 sequence 순으로 조회
                List<BoardAttachments> allAttachments =
                        boardAttachmentsRepository.findByBoardOrderBySequenceAsc(board);

                // 0부터 순차적으로 sequence 재부여
                for (int i = 0; i < allAttachments.size(); i++) {
                    allAttachments.get(i).setSequence(i);
                    boardAttachmentsRepository.save(allAttachments.get(i));
                }
            }
        }

        // 게시글 수정일 업데이트
        board.setUpdatedDateTime(LocalDateTime.now());
        boardRepository.save(board);
    }

    // 게시글 좋아요
    @Transactional
    public BoardResponse likeBoard(Integer boardId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 사용자입니다."));

        Board board = boardRepository.findByBoardIdAndDeletedDateTimeIsNull(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 카테고리에서 게시글을 찾을 수 없거나 삭제된 게시글입니다."));

        Optional<LikeBoard> existingLike = boardLikeRepository.findByUserAndBoard(user, board);
        boolean isLiked;

        if (existingLike.isPresent()) {
            boardLikeRepository.delete(existingLike.get());
            boardRepository.decreaseLikes(boardId);
            rankingService.decrementHotLikeCount(board);
            isLiked = false;
        } else {
            LikeBoard likeboard = new LikeBoard();
            likeboard.setUser(user);
            likeboard.setBoard(board);
            boardLikeRepository.save(likeboard);
            boardRepository.increaseLikes(boardId);
            rankingService.incrementHotLikeCount(board);
            isLiked = true;
        }

        if (!board.getUser().getId().equals(user.getId())) {
            notificationService.createNotification(
                    board.getUser(),
                    user,
                    NotificationType.LIKE,
                    board
            );
        }
        entityManager.refresh(board);

        BoardResponse boardResponse = BoardResponse.builder()
                .likes(board.getLikes())
                .isLiked(isLiked)
                .build();

        return boardResponse;
    }
}