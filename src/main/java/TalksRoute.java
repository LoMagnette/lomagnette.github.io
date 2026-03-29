import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.qute.Template;

@ApplicationScoped
public class TalksRoute {

    @Inject
    Template talkDetail;

    @Inject
    TalksList talksList;

    void init(@Observes Router router) {
        router.get("/talks/:slug").handler(this::handleTalkDetail);
    }

    void handleTalkDetail(RoutingContext ctx) {
        String slug = ctx.pathParam("slug");
        TalksList.TalkEntry talk = talksList.getBySlug(slug);
        if (talk == null) {
            ctx.next();
            return;
        }
        talkDetail.data("talk", talk)
                .data("currentPath", "/talks/" + slug)
                .renderAsync()
                .thenAccept(html -> ctx.response()
                        .putHeader("Content-Type", "text/html;charset=UTF-8")
                        .end(html))
                .exceptionally(t -> {
                    ctx.fail(t);
                    return null;
                });
    }
}
