import io.quarkus.qute.TemplateExtension;

/**
 * Qute helpers for the navbar (templates/partials/navbar-menu.html).
 */
@TemplateExtension
public class NavExtensions {

    /**
     * Whether the menu entry {@code itemPath} is the current section for {@code currentPath}.
     * The blog entry ("/") also covers individual posts under /posts.
     */
    static boolean isCurrentSection(String currentPath, Object itemPath) {
        if (currentPath == null || itemPath == null) {
            return false;
        }
        String item = itemPath.toString();
        if (item.equals("/")) {
            return currentPath.equals("/") || currentPath.startsWith("/posts");
        }
        return currentPath.startsWith(item);
    }
}
