package dopamine.soundock.controller;

import dopamine.soundock.dto.RestResponse;
import dopamine.soundock.dto.request.BoardCreateRequest;
import dopamine.soundock.dto.response.BoardResponse;
import dopamine.soundock.enums.CategoryType;
import dopamine.soundock.global.IPUtils;
import dopamine.soundock.service.BoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
@RequestMapping("/api/boards")
@RestController
@RequiredArgsConstructor
public class BoardController {
    private final BoardService boardService;

    @Operation(
            summary = "특정 카테고리 내 게시글 작성",
            description = "categoryType에 해당하는 카테고리 이동 후 게시글을 작성합니다." +
                    "작성이 완료되었다면 완료 메시지를 반환합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "게시글 등록 성공", content = @Content(schema = @Schema(implementation = RestResponse.class))),
            @ApiResponse(responseCode = "403", description = "로그인 필요", content = @Content(schema = @Schema(implementation = RestResponse.class))),
            @ApiResponse(responseCode = "404", description = "미등록 카테고리", content = @Content(schema = @Schema(implementation = RestResponse.class))),
    })
    // 게시글 작성
    @PostMapping("/category/{categoryType}")
    public ResponseEntity<RestResponse<?>> createNewBoard(
            @PathVariable(required = true) CategoryType categoryType,
            @Valid @RequestBody BoardCreateRequest createRequest
    ){
        int newBoardId = boardService.createBoard(categoryType, createRequest);
        URI location = URI.create("/getDetailBoard/" + newBoardId);
        return ResponseEntity.created(location).body(RestResponse.success("게시글 등록이 완료되었습니다."));
    }
    @Operation(
            summary = "특정 카테고리 내 게시글 상세 조회",
            description = "categoryType 내 boarId와 일치하는 게시글의 상세한 내용을 조회."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "게시글 상세 조회", content = @Content(schema = @Schema(implementation = BoardResponse.class))),
            @ApiResponse(responseCode = "404", description = "미등록 카테고리", content = @Content(schema = @Schema(implementation = RestResponse.class))),
            @ApiResponse(responseCode = "404", description = "미등록 게시글", content = @Content(schema = @Schema(implementation = RestResponse.class))),
    })
    // 게시글 상세 조회
    @GetMapping("/{boardId}")
    public ResponseEntity<RestResponse<BoardResponse>> getDetailBoard(
            @PathVariable(required = true) Integer boardId,
            // 로그인 유저는 username -> email로, 비로그인 유저는 anonymousUser -> null로
            @AuthenticationPrincipal(expression = "#this == 'anonymousUser' ? null : username") String email,
            HttpServletRequest request
    ){
        // IP 뽑아오기
        String clientIp = IPUtils.getClientIp(request);

        BoardResponse boardResponse = boardService.getDetailBoard(boardId, email, clientIp);
        return ResponseEntity.ok(RestResponse.success(boardResponse));
    }

    @Operation(
            summary = "특정 카테고리 게시글 목록 조회",
            description = "categoryType 작성되어있는 게시글들을 목록을 형태로 조회."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "게시판 목록 조회", content = @Content(schema = @Schema(implementation = BoardResponse.class))),
            @ApiResponse(responseCode = "404", description = "미등록 카테고리", content = @Content(schema = @Schema(implementation = RestResponse.class))),
    })

    // 게시판 카테고리별 목록 조회
    @GetMapping("/category/{categoryType}")
    public ResponseEntity<RestResponse<?>> getBoards(
            @RequestParam String keyword,
            @PathVariable(required = true) CategoryType categoryType
    ){
        // subCategory와 일치하는 게시글 목록 조회
        List<BoardResponse> boardResponses = boardService.getBoardsByCategory(categoryType);
        return ResponseEntity.ok(RestResponse.success(boardResponses));
        }

    @Operation(
            summary = "로그인한 유저가 작성한 게시글 삭제",
            description = "로그인한 유저가 categoryType 내 작성한 게시글에 대하여 삭제 가능."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "게시판 삭제", content = @Content(schema = @Schema(implementation = RestResponse.class))),
            @ApiResponse(responseCode = "403", description = "작성자 불일치", content = @Content(schema = @Schema(implementation = RestResponse.class))),
            @ApiResponse(responseCode = "404", description = "미등록 카테고리, 게시글", content = @Content(schema = @Schema(implementation = RestResponse.class))),
    })
    // 게시글 삭제
    @DeleteMapping("/{boardId}")
    public ResponseEntity<RestResponse<?>> deleteBoard(
            @PathVariable(required = true) Integer boardId
    ){
        boardService.deleteBoard(boardId);
        return ResponseEntity.ok(RestResponse.success());
    }

    @Operation(
            summary = "로그인한 유저가 작성한 게시글 수정",
            description = "로그인한 유저가 categoryType 내 작성한 게시글에 대하여 수정 가능."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "게시판 수정", content = @Content(schema = @Schema(implementation = RestResponse.class))),
            @ApiResponse(responseCode = "403", description = "작성자 불일치", content = @Content(schema = @Schema(implementation = RestResponse.class))),
            @ApiResponse(responseCode = "404", description = "미등록 카테고리, 게시글", content = @Content(schema = @Schema(implementation = RestResponse.class))),
    })
    // 게시글 수정
    @PatchMapping("/{boardId}")
    public ResponseEntity<RestResponse<?>> updateBoard(
            @PathVariable(required = true) Integer boardId,
            @Valid @RequestBody BoardCreateRequest updaterequest
    ){
        boardService.updateBoard(boardId, updaterequest);
        return ResponseEntity.ok(RestResponse.success("게시글 수정이 완료되었습니다."));
    }

    @Operation(
            summary = "로그인한 유저가 게시글 좋아요",
            description = "로그인한 유저가 게시글에 좋아요와 좋아요 취소."
    )

    // 게시글 좋아요
    @PostMapping("/{boardId}/like")
    public ResponseEntity<RestResponse<BoardResponse>> likeBoard(@PathVariable Integer boardId){
        BoardResponse boardResponse = boardService.likeBoard(boardId);
        return ResponseEntity.ok(RestResponse.success(boardResponse));
    }

}
