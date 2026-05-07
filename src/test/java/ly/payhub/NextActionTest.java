package ly.payhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NextActionTest {

    @TestFactory
    List<DynamicTest> fixtures() throws IOException {
        JsonNode doc = new ObjectMapper().readTree(
                Files.readAllBytes(Path.of("../shared/test-vectors/next-action-fixtures.json")));
        List<DynamicTest> out = new ArrayList<>();
        for (JsonNode f : doc.get("fixtures")) {
            out.add(DynamicTest.dynamicTest(f.get("name").asText(), () -> {
                NextAction got = NextAction.fromJson(f.get("json"));
                String name = switch (got) {
                    case NextAction.OtpRequired x -> "OtpRequired";
                    case NextAction.Redirect x -> "Redirect";
                    case NextAction.QR x -> "QR";
                    case NextAction.Lightbox x -> "Lightbox";
                };
                assertEquals(f.get("expect_kind").asText(), name);
            }));
        }
        return out;
    }

    @Test
    void unknownDiscriminatorThrows() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode bogus = mapper.createObjectNode().put("type", "bogus");
        assertThrows(IllegalArgumentException.class, () -> NextAction.fromJson(bogus));
    }
}
