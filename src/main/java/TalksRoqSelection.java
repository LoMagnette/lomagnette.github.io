import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.stream.Collectors;

@ApplicationScoped
public class TalksRoqSelection {

    @Inject
    TalksList talksList;

    @Produces
    @Singleton
    RoqSelection produceTalkPaths() {
        return new RoqSelection(
                talksList.getList().stream()
                        .map(talk -> SelectedPath.builder()
                                .html("/talks/" + talk.slug())
                                .build())
                        .collect(Collectors.toList())
        );
    }
}
