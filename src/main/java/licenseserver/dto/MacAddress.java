package licenseserver.dto;

import java.util.Locale;

/** Формат MAC-адреса устройства: шесть пар шестнадцатеричных цифр через ':' или '-'. */
public final class MacAddress {
    public static final String REGEX = "^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$";
    public static final String MESSAGE = "MAC-адрес должен иметь вид AA:BB:CC:DD:EE:FF";

    private MacAddress() {
    }

    /** Приводит к единому виду AA:BB:CC:DD:EE:FF, чтобы один адрес не регистрировался дважды. */
    public static String normalize(String mac) {
        return mac.trim().replace('-', ':').toUpperCase(Locale.ROOT);
    }
}
