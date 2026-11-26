import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
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

    /**
     * Talk with its appearances, for the talk page layout (templates/layouts/talk.html).
     * Accepts the raw {@code page.data.id} value, which YAML may parse as a number.
     * A talk defined in data/talks.yml but never given yet is returned with no appearances.
     */
    public TalkEntry getById(Object id) {
        String key = String.valueOf(id);
        return getList().stream()
                .filter(t -> t.id().equals(key))
                .findFirst()
                .orElseGet(() -> talks.list().stream()
                        .filter(t -> t.id().equals(key))
                        .findFirst()
                        .map(t -> new TalkEntry(t.id(), slugOf(t), t.title(), t.description(), t.article(),
                                t.github(), List.of(), Boolean.TRUE.equals(t.retired())))
                        .orElse(null));
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
            result.add(new TalkEntry(id, talkDef != null ? slugOf(talkDef) : toSlug(canonicalTitle), canonicalTitle, description, article, github, entry.getValue(), retired));
        }

        return result;
    }

    /** The talk page slug, as Roq resolves it: the explicit {@code slug} field, else the slugified title. */
    private static String slugOf(Talks.Talk talk) {
        return talk.slug() != null ? talk.slug() : toSlug(talk.title());
    }

    /** Same slug Roq derives from the title for the talk page URL (/talks/:slug/). */
    private static String toSlug(String title) {
        return RoqTemplateExtension.slugify(title);
    }
}
