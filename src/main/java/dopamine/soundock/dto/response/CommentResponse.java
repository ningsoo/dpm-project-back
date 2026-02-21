package dopamine.soundock.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopamine.soundock.entity.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {
    private Integer commentId;
    private Integer userId;
    private String nickname;
    private String content;
    private Integer likeCount;
    private boolean toggledLike;
    private Integer countComment;
    private LocalDateTime createdDatetime;
    private LocalDateTime updatedDatetime;
    private boolean isDeleted;

    public static CommentResponse from(Comment comment){
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .userId(comment.getUser().getId())
                .nickname(comment.getUser().getNickname())
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .createdDatetime(comment.getCreatedDateTime())
                .updatedDatetime(comment.getUpdatedDateTime())
                .build();
    }

    public static CommentResponse fromForLoginUser(Comment comment, boolean toggledLike){
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .userId(comment.getUser().getId())
                .nickname(comment.getUser().getNickname())
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .toggledLike(toggledLike)
                .createdDatetime(comment.getCreatedDateTime())
                .updatedDatetime(comment.getUpdatedDateTime())
                .isDeleted(comment.getUser().isDeleted())
                .build();
    }

    public static CommentResponse of(Comment comment, Integer countComment){
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .userId(comment.getUser().getId())
                .nickname(comment.getUser().getNickname())
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .countComment(countComment)
                .createdDatetime(comment.getCreatedDateTime())
                .updatedDatetime(comment.getUpdatedDateTime())
                .isDeleted(comment.getUser().isDeleted())
                .build();
    }
}
