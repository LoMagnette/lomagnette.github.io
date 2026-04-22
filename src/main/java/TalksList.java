import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import io.quarkus.qute.RawString;

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

    public record TalkEntry(String id, String slug, String title, String description, String article, String github, List<Appearance> appearances, boolean retired) {
        public RawString descriptionHtml() {
            if (description == null) return null;
            String html = description
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")
                    .replace("\n\n", "</p><p>")
                    .replace("\n", "<br>");
            return new RawString("<p>" + html + "</p>");
        }

        public String latestRecordingEmbed() {
            for (Appearance a : appearances) {
                if (a.recording() != null) {
                    return toYouTubeEmbed(a.recording());
                }
            }
            return null;
        }

        public String latestSlides() {
            for (Appearance a : appearances) {
                if (a.slides() != null) {
                    return a.slides();
                }
            }
            return null;
        }

        private static String toYouTubeEmbed(String url) {
            if (url != null && url.contains("youtube.com/watch")) {
                String videoId = url.replaceAll(".*[?&]v=([^&]+).*", "$1");
                return "https://www.youtube.com/embed/" + videoId;
            }
            return url;
        }
    }

    public TalkEntry getById(String id) {
        return getList().stream()
                .filter(t -> t.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public TalkEntry getBySlug(String slug) {
        return getList().stream()
                .filter(t -> t.slug().equals(slug))
                .findFirst()
                .orElse(null);
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
            String description = talkDef != null ? talkDef.description() : null;
            String article = talkDef != null ? talkDef.article() : null;
            String github = talkDef != null ? talkDef.github() : null;
            boolean retired = talkDef != null && Boolean.TRUE.equals(talkDef.retired());
            result.add(new TalkEntry(id, toSlug(canonicalTitle), canonicalTitle, description, article, github, entry.getValue(), retired));
        }

        return result;
    }

    private static String toSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-|-$", "");
    }
}
