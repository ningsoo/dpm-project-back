package dopamine.soundock.entity;

import dopamine.soundock.enums.UserRole;
import dopamine.soundock.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(name = "password", nullable = false)
    private String password;

    @NotBlank
    @Column(name = "nickname", nullable = false, unique = true)
    private String nickname;

    @NotBlank
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Min(0)
    @Column(name = "points", nullable = false)
    private int points;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status",  nullable = false)
    private UserStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createDatetime;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Min(0)
    @Column(name = "pop_balance")
    private Integer popBalance;

    @OneToOne
    @JoinColumn(name = "grade_id")
    private UserGrade userGrade;

    @Column(name = "profile_url")
    private String profileUrl;

    @Column(name = "name")
    private String name;
}
