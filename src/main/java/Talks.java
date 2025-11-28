import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

import java.util.List;

@DataMapping(value = "talks", parentArray = true)
public record Talks(List<Year> list) {

    public record Year(String year, List<Event> events) {}

    public record Event( String name, String date, String location, List<Talk> talks){}

    public record Talk(String title, String slides, String recording) {}
}
