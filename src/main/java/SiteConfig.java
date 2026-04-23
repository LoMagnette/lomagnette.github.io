import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

/**
 * Provides unified site-level configuration for all templates.
 * Reads from data/menu.yml, data/socials.yml, and data/authors.yml
 * so there is a single source of truth.
 */
@ApplicationScoped
@Named("siteConfig")
public class SiteConfig {

    @Inject
    MenuData menuData;

    @Inject
    Socials socials;

    public String name() {
        return "Loïc Magnette";
    }

    public List<Socials.Social> socials() {
        return socials.list();
    }

    public List<MenuData.MenuItem> menuItems() {
        return menuData.items();
    }
}
