import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

import java.util.List;

@DataMapping(value = "conferences", parentArray = true)
public record Conferences(List<Year> list) {

    public record Year(String year, List<Event> events) {}

    public record Event(String name, String url, String date, String location, String flag, List<EventTalk> talks) {}

    public record EventTalk(String talkId, String title, String slides, String recording) {}
}
