import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.*;

@ApplicationScoped
@Named("talksList")
public class TalksList {

    @Inject
    Talks talks;

    @Inject
    Conferences conferences;

    public record Appearance(String eventName, String eventUrl, String date, String location, String flag,
                             String slides, String recording, String originalTitle) {
    }

    public record TalkEntry(String title, List<Appearance> appearances, boolean retired) {
    }

    public List<TalkEntry> getList() {
        // Build a lookup map from talk id to Talk definition
        Map<String, Talks.Talk> talkById = new LinkedHashMap<>();
        for (Talks.Talk talk : talks.list()) {
            talkById.put(talk.id(), talk);
        }

        // LinkedHashMap preserves insertion order — conferences data is sorted newest-first
        Map<String, List<Appearance>> grouped = new LinkedHashMap<>();

        for (Conferences.Year year : conferences.list()) {
            for (Conferences.Event event : year.events()) {
                for (Conferences.EventTalk eventTalk : event.talks()) {
                    String id = eventTalk.talkId();
                    Talks.Talk talkDef = talkById.get(id);
                    String displayTitle = eventTalk.title() != null ? eventTalk.title()
                            : (talkDef != null ? talkDef.title() : id);

                    Appearance appearance = new Appearance(
                            event.name(), event.url(), event.date(),
                            event.location(), event.flag(),
                            eventTalk.slides(), eventTalk.recording(), displayTitle);
                    grouped.computeIfAbsent(id, k -> new ArrayList<>()).add(appearance);
                }
            }
        }

        List<TalkEntry> result = new ArrayList<>();
        for (Map.Entry<String, List<Appearance>> entry : grouped.entrySet()) {
            String id = entry.getKey();
            Talks.Talk talkDef = talkById.get(id);
            String canonicalTitle = talkDef != null ? talkDef.title() : id;
            boolean retired = talkDef != null && Boolean.TRUE.equals(talkDef.retired());
            result.add(new TalkEntry(canonicalTitle, entry.getValue(), retired));
        }

        return result;
    }
}
