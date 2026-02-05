package dopamine.soundock.service;

import dopamine.soundock.dto.request.PlaylistRegisterRequest;
import dopamine.soundock.dto.response.YouTubeApiResponse;
import dopamine.soundock.dto.response.YouTubePlaylistResponse;
import dopamine.soundock.entity.Playlist;
import dopamine.soundock.entity.User;
import dopamine.soundock.exceptions.CustomException;
import dopamine.soundock.exceptions.ResourceNotFoundException;
import dopamine.soundock.repository.PlaylistRepository;
import dopamine.soundock.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeService {

    private final UserRepository userRepository;
    private final YouTubeAuthService youTubeAuthService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final PlaylistRepository playlistRepository;

    /**
     * 구글 연동 체크 메시드
     */
    private void checkYouTubeLinkage(User user) {
        if (!youTubeAuthService.validateAndCleanupOAuth(user)) {
            throw new CustomException("유튜브 연동이 필요한 서비스 입니다.", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 유튜브 플레이리스트 조회
     */
    @Transactional
    public List<YouTubePlaylistResponse> getUserYouTubePlaylists(String email) {
        // DB에서 해당 유저의 구글 액세스 토큰 가져오기
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("유저를 찾을 수 없습니다."));

        checkYouTubeLinkage(user);

        // 유효한 AccessToken 가져오기 (만료시 자동 갱신)
        String accessToken = youTubeAuthService.getValidAccessToken(user);

        // 유튜브 API 호출
        return fetchYouTubePlaylists(accessToken);
    }

    /**
     * 유튜브 API 호출하여 플레이리스트 목록 가져오는 메서드
     */
    private List<YouTubePlaylistResponse> fetchYouTubePlaylists(String accessToken) {
        // 유튜브 API 호출 설정 (playlists.list 엔드 포인트)
        String url = "https://www.googleapis.com/youtube/v3/playlists";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // URI 생성 (fromHttpUrl 대신 fromUriString 사용)
        // 최대 50개 까지만 결과 보기 가능(시간 되면 페이징 처리 구현 예정)
        URI uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("part", "snippet,contentDetails")
                .queryParam("mine", true)
                .queryParam("maxResults", 50)
                .build()
                .toUri();

        try {
            // Youtube API 호출
            ResponseEntity<YouTubeApiResponse> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    entity,
                    YouTubeApiResponse.class
            );

            // 응답 데이터 파싱
            return convertToPlaylistResponse(response.getBody());

        } catch (HttpClientErrorException.Unauthorized e) {
            // 401 에러 - 토큰이 여전히 유효하지 않음
            log.error("YouTube API 인증 실패: {}", e.getMessage());
            throw new CustomException("YouTube 인증에 실패했습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);

        } catch (HttpClientErrorException.Forbidden e) {
            // 403 에러 - 권한 부족
            log.error("YouTube API 권한 부족: {}", e.getMessage());
            throw new CustomException("YouTube API 접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

        } catch (Exception e) {
            // 기타 오류
            log.error("YouTube API 호출 중 오류 발생", e);
            throw new CustomException("YouTube 플레이리스트를 불러오는 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * YouTube API 응답을 dto로 반환
     */
    private List<YouTubePlaylistResponse> convertToPlaylistResponse(YouTubeApiResponse apiResponse) {
        if (apiResponse == null || apiResponse.getItems() == null) {
            return new ArrayList<>();
        }

        return apiResponse.getItems().stream()
                .filter(item -> item != null && item.getSnippet() != null)
                .map(item -> {
                    try {
                        String thumbnailUrl = extractThumbnailUrl(item.getSnippet().getThumbnails());

                        return YouTubePlaylistResponse.builder()
                                .youtubeListId(item.getId())
                                .title(item.getSnippet().getTitle())
                                .thumbnailUrl(thumbnailUrl)
                                .itemCount(item.getContentDetails().getItemCount())
                                .build();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(response -> response != null)
                .collect(Collectors.toList());
    }
    /**
     * 썸네일 URL 추출
     */
    private String extractThumbnailUrl(YouTubeApiResponse.Thumbnails thumbnails) {
        if (thumbnails == null) {
            return null;
        }

        // medium 우선
        if (thumbnails.getMedium() != null && thumbnails.getMedium().getUrl() != null) {
            return thumbnails.getMedium().getUrl();
        }

        // medium이 없으면 high
        if (thumbnails.getHigh() != null && thumbnails.getHigh().getUrl() != null) {
            return thumbnails.getHigh().getUrl();
        }

        // 둘 다 없으면 default
        if (thumbnails.getDefaultThumbnail() != null && thumbnails.getDefaultThumbnail().getUrl() != null) {
            return thumbnails.getDefaultThumbnail().getUrl();
        }

        return null;
    }

    /**
     * 플레이리스트 등록 API
     */
    @Transactional
    public void registerPlaylist(String email, PlaylistRegisterRequest request) {
        // 유저 확인
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("해당 유저를 찾을 수 없습니다."));

        checkYouTubeLinkage(user);

        // 중복 등록 방지
        if (playlistRepository.existsByUserAndYoutubeListId(user, request.getYoutubeListId())) {
            throw new CustomException("이미 보관함에 등록된 플레이리스트 입니다.", HttpStatus.CONFLICT);
        }

        // 엔티티 생성 및 저장
        Playlist playlist = Playlist
                .builder()
                .user(user)
                .youtubeListId(request.getYoutubeListId())
                .title(request.getTitle())
                .thumbnailUrl(request.getThumbnailUrl())
                .itemCount(request.getItemCount())
                .build();

        playlistRepository.save(playlist);
    }


    /**
     * 내가 등록한 플레이리스트 조회 API
     */
    @Transactional(readOnly = true)
    public List<YouTubePlaylistResponse> getMyRegisterPlaylist(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("유저를 찾을 수 없습니다."));

        checkYouTubeLinkage(user);

        List<Playlist> playlists = playlistRepository.findAllByUser(user);

        return playlists.stream()
                .map(YouTubePlaylistResponse::fromEntity)
                .collect(Collectors.toList());
    }


    /**
     * 내가 등록한 플레이리스트 삭제
     */
    @Transactional
    public void deleteMyPlaylist(Integer playlistId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("유저를 찾을 수 없습니다."));

        checkYouTubeLinkage(user);

        Playlist playlist = playlistRepository.findByPlaylistId(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 플레이리스트를 찾을 수 없습니다."));

        // 해당 플레이리스트의 검증(현재 로그인한 유저가 플레이리스트 등록 유저와 같은지 확인)
        if (!playlist.getUser().equals(user)) {
            throw new CustomException("이 플레이리스트를 삭제할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        playlistRepository.delete(playlist);
    }
}
