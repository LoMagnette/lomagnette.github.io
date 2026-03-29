import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.*;

@ApplicationScoped
@Named("conferencesList")
public class ConferencesList {

    @Inject
    Talks talks;

    @Inject
    Conferences conferences;

    public record ResolvedTalk(String title, String slides, String recording) {
    }

    public record ResolvedEvent(String name, String url, String date, String location, String flag,
                                List<ResolvedTalk> talks) {
    }

    public record ResolvedYear(String year, List<ResolvedEvent> events) {
    }

    public List<ResolvedYear> getList() {
        // Build a lookup map from talk id to Talk definition
        Map<String, Talks.Talk> talkById = new LinkedHashMap<>();
        for (Talks.Talk talk : talks.list()) {
            talkById.put(talk.id(), talk);
        }

        List<ResolvedYear> result = new ArrayList<>();
        for (Conferences.Year year : conferences.list()) {
            List<ResolvedEvent> resolvedEvents = new ArrayList<>();
            for (Conferences.Event event : year.events()) {
                List<ResolvedTalk> resolvedTalks = new ArrayList<>();
                for (Conferences.EventTalk eventTalk : event.talks()) {
                    Talks.Talk talkDef = talkById.get(eventTalk.talkId());
                    String title = eventTalk.title() != null ? eventTalk.title()
                            : (talkDef != null ? talkDef.title() : eventTalk.talkId());
                    resolvedTalks.add(new ResolvedTalk(title, eventTalk.slides(), eventTalk.recording()));
                }
                resolvedEvents.add(new ResolvedEvent(
                        event.name(), event.url(), event.date(),
                        event.location(), event.flag(), resolvedTalks));
            }
            result.add(new ResolvedYear(year.year(), resolvedEvents));
        }
        return result;
    }
}
