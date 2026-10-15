package licenseserver.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Каноническое представление JSON по RFC 8785 (JSON Canonicalization Scheme, JCS):
 * без пробелов, ключи объектов отсортированы по кодовым единицам UTF-16,
 * строки экранируются как в ECMAScript JSON.stringify, числа — в формате ECMAScript Number.toString,
 * результат кодируется в UTF-8. Даты сериализуются строками ISO-8601.
 * Клиент восстанавливает те же байты из полученного JSON и проверяет подпись.
 */
@Component
public class CanonicalJson {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public byte[] toBytes(Object value) {
        JsonNode node = value instanceof JsonNode n ? n : mapper.valueToTree(value);
        StringBuilder out = new StringBuilder();
        write(node, out);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void write(JsonNode node, StringBuilder out) {
        switch (node.getNodeType()) {
            case OBJECT -> writeObject(node, out);
            case ARRAY -> {
                out.append('[');
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) out.append(',');
                    write(node.get(i), out);
                }
                out.append(']');
            }
            case STRING -> writeString(node.textValue(), out);
            case NUMBER -> out.append(formatNumber(node));
            case BOOLEAN -> out.append(node.booleanValue());
            case NULL -> out.append("null");
            default -> throw new IllegalArgumentException("Тип не поддерживается в JCS: " + node.getNodeType());
        }
    }

    private static void writeObject(JsonNode node, StringBuilder out) {
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
            fields.add(it.next());
        }
        // String.compareTo сравнивает кодовые единицы UTF-16 — именно этого требует RFC 8785, раздел 3.2.3
        fields.sort(Map.Entry.comparingByKey());
        out.append('{');
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) out.append(',');
            writeString(fields.get(i).getKey(), out);
            out.append(':');
            write(fields.get(i).getValue(), out);
        }
        out.append('}');
    }

    /** Экранирование строк по RFC 8785, раздел 3.2.2.2. */
    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Числа в формате ECMAScript Number.prototype.toString (RFC 8785, раздел 3.2.2.3). */
    static String formatNumber(JsonNode node) {
        double d = node.isIntegralNumber() ? node.bigIntegerValue().doubleValue() : node.doubleValue();
        if (node.isIntegralNumber() && node.bigIntegerValue().abs().bitLength() > 53) {
            throw new IllegalArgumentException("Целое вне диапазона IEEE 754 double: " + node);
        }
        return formatDouble(d);
    }

    static String formatDouble(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("NaN и Infinity недопустимы в JSON");
        }
        if (d == 0) {
            return "0"; // и -0 тоже
        }
        // Кратчайшее десятичное представление (JDK 19+), разложенное на цифры и порядок
        BigDecimal bd = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
        if (bd.precision() == 2) {
            // Double.toString выдаёт минимум две значащие цифры; ECMAScript требует кратчайшую запись
            BigDecimal one = bd.round(new java.math.MathContext(1, java.math.RoundingMode.HALF_EVEN));
            if (one.doubleValue() == Math.abs(d)) {
                bd = one.stripTrailingZeros();
            }
        }
        String digits = bd.unscaledValue().toString();
        int k = digits.length();
        int n = k - bd.scale(); // значение = 0.digits * 10^n
        StringBuilder sb = new StringBuilder(d < 0 ? "-" : "");
        if (k <= n && n <= 21) {
            sb.append(digits).append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            sb.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            sb.append("0.").append("0".repeat(-n)).append(digits);
        } else {
            int e = n - 1;
            sb.append(digits.charAt(0));
            if (k > 1) sb.append('.').append(digits, 1, k);
            sb.append('e').append(e >= 0 ? "+" : "-").append(Math.abs(e));
        }
        return sb.toString();
    }
}
