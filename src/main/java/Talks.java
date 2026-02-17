import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

import java.util.List;

@DataMapping(value = "talks", parentArray = true)
public record Talks(List<Talk> list) {

    public record Talk(String id, String title, Boolean retired) {}
}
