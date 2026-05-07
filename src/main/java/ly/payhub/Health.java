package ly.payhub;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Health(String status, @JsonProperty("psps") List<String> psps) {}
