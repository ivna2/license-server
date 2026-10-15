package licenseserver.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "license")
@Getter
@Setter
@NoArgsConstructor
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ключ активации, который получает клиент. */
    @Column(nullable = false, unique = true)
    private String code;

    /** Пользователь, активировавший лицензию (пусто до первой активации). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Владелец (покупатель) лицензии. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id")
    private LicenseType type;

    @Column(name = "first_activation_date")
    private Instant firstActivationDate;

    @Column(name = "ending_date")
    private Instant endingDate;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "device_count", nullable = false)
    private int deviceCount;

    @Column(columnDefinition = "TEXT")
    private String description;
}
