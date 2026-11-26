import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.time.Year;

/**
 * Site-level values not available from Roq's site data.
 */
@ApplicationScoped
@Named("siteConfig")
public class SiteConfig {

    /** Year shown in the footer copyright; the static site is regenerated on each deploy. */
    public int currentYear() {
        return Year.now().getValue();
    }
}
