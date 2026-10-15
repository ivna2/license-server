package licenseserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import licenseserver.signature.CanonicalJson;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Примеры из RFC 8785 (JSON Canonicalization Scheme). */
class CanonicalJsonTests {

    private final CanonicalJson jcs = new CanonicalJson();
    private final ObjectMapper mapper = new ObjectMapper();

    private String canon(String json) throws Exception {
        return new String(jcs.toBytes(mapper.readTree(json)), StandardCharsets.UTF_8);
    }

    @Test
    void rfcSection3Example() throws Exception {
        String input = """
                {
                  "numbers": [333333333.33333329, 1E30, 4.50, 2e-3, 0.000000000000000000000000001],
                  "string": "\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\"\\/",
                  "literals": [null, true, false]
                }""";
        assertThat(canon(input)).isEqualTo(
                "{\"literals\":[null,true,false],\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],"
                        + "\"string\":\"€$\\u000f\\nA'B\\\"\\\\\\\\\\\"/\"}");
    }

    @Test
    void sortsKeysByUtf16CodeUnits() throws Exception {
        String input = """
                {"\\u20ac": "Euro Sign", "\\r": "Carriage Return", "\\ufb33": "Hebrew Letter Dalet With Dagesh",
                 "1": "One", "\\ud83d\\ude00": "Emoji: Grinning Face", "\\u0080": "Control",
                 "\\u00f6": "Latin Small Letter O With Diaeresis"}""";
        assertThat(canon(input)).isEqualTo(
                "{\"\\r\":\"Carriage Return\",\"1\":\"One\",\"\u0080\":\"Control\",\"ö\":\"Latin Small Letter O With Diaeresis\","
                        + "\"€\":\"Euro Sign\",\"\uD83D\uDE00\":\"Emoji: Grinning Face\",\"\uFB33\":\"Hebrew Letter Dalet With Dagesh\"}");
    }

    @Test
    void ecmascriptNumberFormatting() throws Exception {
        assertThat(canon("[0, -0, 1, -1, 300, 1e21, 1e20, 123e-20, 0.000001, 0.0000001, 9007199254740991, 5e-324]"))
                .isEqualTo("[0,0,1,-1,300,1e+21,100000000000000000000,1.23e-18,0.000001,1e-7,9007199254740991,5e-324]");
    }
}
