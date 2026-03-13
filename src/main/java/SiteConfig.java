import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.util.List;

/**
 * Provides site-level configuration for non-Roq templates (e.g. talk detail pages).
 * Values mirror those in content/index.html frontmatter and data/menu.yml.
 */
@ApplicationScoped
@Named("siteConfig")
public class SiteConfig {

    public String name() {
        return "Loïc Magnette";
    }

    public record Social(String url, String icon, String label) {}

    public record MenuItem(String title, String path, String icon) {}

    public List<Social> socials() {
        return List.of(
                new Social("https://github.com/lomagnette", "fa-brands fa-github", "GitHub"),
                new Social("https://linkedin.com/in/lomagnette", "fa-brands fa-linkedin", "LinkedIn"),
                new Social("https://bsky.app/profile/lomagnette.bsky.social", "fa-brands fa-bluesky", "Bluesky")
        );
    }

    public List<MenuItem> menuItems() {
        return List.of(
                new MenuItem("Blog", "/", "fa-regular fa-newspaper"),
                new MenuItem("Talks", "/talks", "fa-solid fa-microphone-lines"),
                new MenuItem("Conferences", "/conferences", "fa-solid fa-calendar-days"),
                new MenuItem("About", "/about", "fa fa-thumbs-up")
        );
    }
}
