package dopamine.soundock.dto.request;

import dopamine.soundock.global.constants.AppConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "회원 가입 요청 정보")
public class UserSignupRequest {

    @Schema(description = "사용자 이메일 주소")
    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;

    @Schema(description = "비밀번호 (대문자, 숫자, 특수문자 포함 10자 이상, 공백 불가")
    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Pattern(
            regexp = AppConstants.ValidationPattern.PASSWORD_PATTERN,
            message = AppConstants.ErrorMessage.PASSWORD_FORMAT_ERROR
    )
    private String password;

    @Schema(description = "실명 이름")
    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    private String name;

    @Schema(description = "커뮤니티 활동 닉네임 (한글/영문/숫자 10자 이내")
    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    @Pattern(
            regexp = AppConstants.ValidationPattern.NICKNAME_PATTERN,
            message = AppConstants.ErrorMessage.NICKNAME_FORMAT_ERROR
    )
    private String nickname;

    @Schema(description = "연락처 (숫자만 11자리")
    @NotBlank(message = "연락처는 필수 입력 항목입니다.")
    @Pattern(
            regexp = AppConstants.ValidationPattern.PHONE_PATTERN,
            message = AppConstants.ErrorMessage.PHONE_FORMAT_ERROR
    )
    private String phoneNumber;
}
