import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

import java.util.List;

@DataMapping(value = "menu")
public record MenuData(List<MenuItem> items) {

    public record MenuItem(String title, String path, String icon) {}
}
