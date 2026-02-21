package dopamine.soundock.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import dopamine.soundock.entity.Category;
import dopamine.soundock.entity.PlaylistItem;
import dopamine.soundock.enums.CategoryType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardResponse{
    private Integer userId;
    private Integer boardId;
    private String title;
    private String content;
    private String nickname;
    private int views;
    private int likes;
    private LocalDateTime createdDateTime;
    private boolean isLiked;
    private int countComment;
    private CategoryType categoryType;
    private String linkUrl;
    private boolean isDeleted;


    // s3 이미지/파일url, id
    private String imageUrl;
    private List<String> imageUrls;
    private List<Integer> imageIds;
    private FileAttachmentResponse attachment;

    // playlist
    private Integer playlistId;
    private String playlistTitle;
    private List<PlaylistItemResponse> playlistItems;

}
