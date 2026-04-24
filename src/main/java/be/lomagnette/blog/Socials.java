package be.lomagnette.blog;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

import java.util.List;

@DataMapping(value = "socials", parentArray = true)
public record Socials(List<Social> list) {

    public record Social(String url, String icon, String label) {}
}
