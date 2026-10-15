package licenseserver.service;

import licenseserver.dto.*;
import licenseserver.exception.ApiException;
import licenseserver.model.*;
import licenseserver.repository.*;
import licenseserver.ticket.TicketFactory;
import licenseserver.ticket.TicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** Операции с лицензиями: создание, активация, проверка, продление. */
@Service
@RequiredArgsConstructor
public class LicenseService {

    private static final String KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int KEY_GROUPS = 5;
    private static final int KEY_GROUP_LENGTH = 5;

    private final LicenseRepository licenseRepo;
    private final ProductRepository productRepo;
    private final LicenseTypeRepository typeRepo;
    private final DeviceRepository deviceRepo;
    private final DeviceLicenseRepository deviceLicenseRepo;
    private final LicenseHistoryRepository historyRepo;
    private final UserService userService;
    private final TicketFactory ticketFactory;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Создание лицензии администратором. Лицензия не активна, пока клиент не активирует её ключом. */
    @Transactional
    public LicenseResponse create(CreateLicenseRequest request, String adminUsername) {
        User admin = currentUser(adminUsername);
        Product product = productRepo.findById(request.productId())
                .orElseThrow(() -> ApiException.notFound("Продукт не найден: " + request.productId()));
        if (product.isBlocked()) {
            throw ApiException.conflict("Продукт заблокирован: лицензии на него не выдаются");
        }
        LicenseType type = typeRepo.findById(request.typeId())
                .orElseThrow(() -> ApiException.notFound("Тип лицензии не найден: " + request.typeId()));
        User owner = userService.findById(request.ownerId())
                .orElseThrow(() -> ApiException.notFound("Владелец не найден: " + request.ownerId()));

        License license = new License();
        license.setCode(generateUniqueKey());
        license.setProduct(product);
        license.setType(type);
        license.setOwner(owner);
        license.setDeviceCount(request.deviceCount());
        license.setDescription(request.description());
        licenseRepo.save(license);

        record(license, admin, LicenseStatus.CREATED, "Лицензия создана");
        return LicenseResponse.of(license);
    }

    /** Активация лицензии на устройстве пользователя. Первая активация запускает срок действия. */
    @Transactional
    public TicketResponse activate(ActivateLicenseRequest request, String username) {
        User user = currentUser(username);
        License license = licenseRepo.findByCodeForUpdate(request.activationKey().trim())
                .orElseThrow(() -> ApiException.notFound("Лицензия не найдена"));

        if (license.getUser() != null && !Objects.equals(license.getUser().getId(), user.getId())) {
            throw ApiException.forbidden("Лицензия активирована другим пользователем");
        }
        requireNotBlocked(license);
        Instant now = Instant.now(clock);
        if (license.getEndingDate() != null && !license.getEndingDate().isAfter(now)) {
            throw ApiException.conflict("Срок действия лицензии истёк, требуется продление");
        }

        Device device = findOrRegisterDevice(user, request.deviceMac(), request.deviceName().trim());
        if (deviceLicenseRepo.existsByLicenseIdAndDeviceId(license.getId(), device.getId())) {
            throw ApiException.conflict("Лицензия уже активирована на этом устройстве");
        }
        if (deviceLicenseRepo.countByLicenseId(license.getId()) >= license.getDeviceCount()) {
            throw ApiException.conflict("Достигнут лимит устройств для лицензии: " + license.getDeviceCount());
        }

        if (license.getUser() == null) {
            license.setUser(user);
            license.setFirstActivationDate(now);
            license.setEndingDate(now.plus(license.getType().getDefaultDurationInDays(), ChronoUnit.DAYS));
        }

        DeviceLicense link = new DeviceLicense();
        link.setLicense(license);
        link.setDevice(device);
        link.setActivationDate(now);
        deviceLicenseRepo.save(link);

        record(license, user, LicenseStatus.ACTIVATED, "Устройство " + device.getMacAddress());
        return ticketFactory.issue(license, device);
    }

    /** Проверка: есть ли у устройства пользователя действующая лицензия на продукт. */
    @Transactional(readOnly = true)
    public TicketResponse check(CheckLicenseRequest request, String username) {
        User user = currentUser(username);
        Device device = deviceRepo.findByMacAddress(MacAddress.normalize(request.deviceMac()))
                .filter(d -> Objects.equals(d.getUser().getId(), user.getId()))
                .orElseThrow(() -> ApiException.notFound("Устройство не найдено"));

        Instant now = Instant.now(clock);
        List<DeviceLicense> links = deviceLicenseRepo.findByDeviceAndProduct(device.getId(), request.productId());
        License license = links.stream()
                .map(DeviceLicense::getLicense)
                .filter(l -> l.getUser() != null && Objects.equals(l.getUser().getId(), user.getId()))
                .filter(l -> l.getEndingDate() != null && l.getEndingDate().isAfter(now))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Действующая лицензия для устройства не найдена"));

        // Заблокированная лицензия возвращается с флагом blocked = true: решение принимает клиент
        return ticketFactory.issue(license, device);
    }

    /** Продление: срок увеличивается на длительность типа лицензии от даты окончания (или от текущей даты, если истекла). */
    @Transactional
    public TicketResponse renew(RenewLicenseRequest request, String username) {
        User user = currentUser(username);
        License license = licenseRepo.findByCodeForUpdate(request.activationKey().trim())
                .orElseThrow(() -> ApiException.notFound("Лицензия не найдена"));

        boolean isOwner = Objects.equals(license.getOwner().getId(), user.getId());
        boolean isUser = license.getUser() != null && Objects.equals(license.getUser().getId(), user.getId());
        if (!isOwner && !isUser) {
            throw ApiException.forbidden("Продлить лицензию может только её владелец или пользователь");
        }
        if (license.getEndingDate() == null) {
            throw ApiException.conflict("Лицензия ещё не активирована");
        }
        requireNotBlocked(license);

        Instant now = Instant.now(clock);
        Instant base = license.getEndingDate().isAfter(now) ? license.getEndingDate() : now;
        license.setEndingDate(base.plus(license.getType().getDefaultDurationInDays(), ChronoUnit.DAYS));

        record(license, user, LicenseStatus.RENEWED, "Срок продлён до " + license.getEndingDate());
        return ticketFactory.issue(license, null);
    }

    /** Блокировка или разблокировка лицензии администратором; изменение попадёт в флаг blocked тикета. */
    @Transactional
    public LicenseResponse setBlocked(Long licenseId, boolean blocked) {
        License license = licenseRepo.findById(licenseId)
                .orElseThrow(() -> ApiException.notFound("Лицензия не найдена: " + licenseId));
        license.setBlocked(blocked);
        return LicenseResponse.of(license);
    }

    @Transactional(readOnly = true)
    public List<LicenseHistoryResponse> history(Long licenseId) {
        if (!licenseRepo.existsById(licenseId)) {
            throw ApiException.notFound("Лицензия не найдена: " + licenseId);
        }
        return historyRepo.findByLicenseIdOrderByChangeDateAsc(licenseId).stream()
                .map(LicenseHistoryResponse::of)
                .toList();
    }

    private void requireNotBlocked(License license) {
        if (license.isBlocked()) {
            throw ApiException.forbidden("Лицензия заблокирована");
        }
        if (license.getProduct().isBlocked()) {
            throw ApiException.forbidden("Продукт заблокирован");
        }
    }

    private Device findOrRegisterDevice(User user, String rawMac, String name) {
        String mac = MacAddress.normalize(rawMac);
        Device device = deviceRepo.findByMacAddress(mac).orElse(null);
        if (device == null) {
            device = new Device();
            device.setMacAddress(mac);
            device.setName(name);
            device.setUser(user);
            return deviceRepo.save(device);
        }
        if (!Objects.equals(device.getUser().getId(), user.getId())) {
            throw ApiException.conflict("Устройство зарегистрировано другим пользователем");
        }
        return device;
    }

    private void record(License license, User user, LicenseStatus status, String description) {
        LicenseHistory entry = new LicenseHistory();
        entry.setLicense(license);
        entry.setUser(user);
        entry.setStatus(status);
        entry.setChangeDate(Instant.now(clock));
        entry.setDescription(description);
        historyRepo.save(entry);
    }

    private User currentUser(String username) {
        return userService.findByUsername(username)
                .orElseThrow(() -> ApiException.forbidden("Пользователь не найден"));
    }

    /** Ключ вида XXXXX-XXXXX-XXXXX-XXXXX-XXXXX из криптостойкого генератора (около 125 бит). */
    private String generateUniqueKey() {
        String key;
        do {
            StringBuilder sb = new StringBuilder();
            for (int g = 0; g < KEY_GROUPS; g++) {
                if (g > 0) sb.append('-');
                for (int i = 0; i < KEY_GROUP_LENGTH; i++) {
                    sb.append(KEY_ALPHABET.charAt(random.nextInt(KEY_ALPHABET.length())));
                }
            }
            key = sb.toString();
        } while (licenseRepo.existsByCode(key));
        return key;
    }
}
