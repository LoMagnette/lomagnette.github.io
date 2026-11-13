import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.Json;

/**
 * Qute helpers used by the SEO templates (meta tags, JSON-LD).
 */
@TemplateExtension
public class SeoExtensions {

    /** Recommended maximum length for a meta description. */
    static final int META_DESCRIPTION_MAX = 155;

    /**
     * Encodes a value as a JSON string literal for use inside a JSON-LD script block.
     * {@code <} is escaped so content can never close the surrounding script tag.
     * Usage: {@code "headline": {page.title.jsonLd}}
     */
    static RawString jsonLd(String value) {
        return new RawString(Json.encode(value == null ? "" : value).replace("<", "\\u003c"));
    }

    /** Collapses whitespace and cuts the text at a word boundary to fit a meta description. */
    static String metaDescription(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace("\\n", " ").replaceAll("\\s+", " ").trim();
        if (clean.length() <= META_DESCRIPTION_MAX) {
            return clean;
        }
        int cut = clean.lastIndexOf(' ', META_DESCRIPTION_MAX - 1);
        return clean.substring(0, cut > 0 ? cut : META_DESCRIPTION_MAX - 1).replaceAll("[\\s,.;:!?-]+$", "") + "…";
    }
}
