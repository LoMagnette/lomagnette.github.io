---
title: "Quarkus 4 Is Here: What Changes, What Breaks, and Why You Should Care"
description: "A hands-on tour of Quarkus 4.0: the Java 21 baseline, Vert.x 5 and HTTP/3, Hibernate ORM 8 and Quarkus Data, Jackson 3, CDI 5, observability out of the box, Kafka @ExactlyOnce, and a practical migration checklist."
tags: [java, quarkus]
author: Loïc
image: quarkus-4.0.jpg
draft: true
---
<!-- TODO(Loïc): add a header image (image: quarkus-4.png) -->
<!-- TODO(Loïc): written against 4.0.0.Beta3 (Nov 4, 2026), updated with CR1 release notes (Nov 11). Snippets were compiled and run on Beta3 only; re-run on CR1/Final before publishing. -->

## A New Major, Finally

Quarkus 3 shipped in April 2023. Since then we got forty feature releases, a handful of LTS versions, and a framework that kept getting better without ever really breaking anything. That's great when you maintain an app. It's less great when you maintain the framework, because every "we'll clean this up later" piles up somewhere.

Quarkus 4 is that "later". The [roadmap](https://github.com/quarkusio/quarkus/discussions/52020), published back in January, sums it up: lean on what the Java platform now offers, make the architecture more modular and lightweight, and double down on AI integration. In practice, this release is mostly about **foundations**: new Java baseline, new networking layer, new Hibernate, new Jackson, new CDI. Some of those changes you'll feel the moment you bump the version. Others are there to unlock what comes next.

Rather than just paraphrase the release notes, I spun up a project on 4.0.0.Beta3 and tried things. Every snippet in this post compiles and runs on it. Where something didn't behave the way I expected, I'll tell you.

<!-- TODO(Loïc): personal hook. Something like how long you've been using Quarkus, or the fact that this very blog runs on Quarkus + Roq. -->

## The Timeline

Quick context if you've only been half-following:

- **Sept 30**: 3.40 LTS, the last feature release of the 3.x line.
- **Oct 1 – Nov 4**: three betas (4.0.0.Beta1 → Beta3).
- **Nov 11**: 4.0.0.CR1.
- **Nov 18**: Quarkus 4.0 Core final.
- **Nov 25**: full Platform release and official announcement.
- **Jan & Feb 2027**: 4.1 and 4.2.
- **March 2027**: the first Quarkus 4 LTS, currently planned as **4.3 LTS**.

If you're on an LTS and you're not in a hurry, you have until spring before the "LTS to LTS" upgrade becomes the natural move. 3.x will keep being maintained for a while after GA (the team mentioned an overlap of probably around a year), but there won't be feature releases on both lines.

## Java 21 Is the New Floor

Let's start with the one that'll affect the most people: **Java 17 is gone**. Java 21 is the minimum, and Java 25 is the recommended version.

Clement Escoffier [explained the reasoning](https://quarkus.io/blog/java21/) in a dedicated post, and the core argument is virtual threads. Java 21 is the first LTS that has them, and so far Quarkus had to support them "through reflection hacks" to keep running on 17. With 21 as the baseline: "No reflection, no conditional paths." For you, that means virtual thread support that no longer depends on tricks. For extension authors, it means a much simpler way to support them, so expect more extensions to play nicely with `@RunOnVirtualThread` over the 4.x releases.

The rest of the ecosystem is going the same way: Jakarta EE 12 sets Java 21 as its baseline, and tools like Error Prone and Jenkins already require it. On the native side, Quarkus 4 builds with Mandrel/GraalVM 25 (25.0.4 minimum as of Beta3).

If you're already on 21 or 25, nothing changes for you. If you're still on 17, you now have a very concrete reason to plan that upgrade. And my advice is to do it *before* touching Quarkus 4, on your current 3.x version. One migration at a time.

## A New Networking Foundation

### Vert.x 5 and Netty 4.2

This is the change you probably won't see in your code, and yet it's one of the biggest. Quarkus' whole I/O layer runs on Vert.x and Netty, and both got a major upgrade: **Vert.x 5** and **Netty 4.2**. Unless you were using Vert.x APIs directly, you shouldn't need to touch anything. If you were, the Vert.x 5 migration guide is your friend: `Future` APIs, the end of callback-style methods... that part is on you.

You also get **native transports**: `epoll` or `io_uring` for better concurrency, and it now works in native executables too.

### HTTP/3, Experimentally

QUIC is quietly becoming the transport for a lot of things: HTTP/3, MQTT over QUIC, WebTransport. Quarkus 4 ships **experimental HTTP/3 support** as a separate extension:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-http3</artifactId>
</dependency>
```

That's it, no property needed. HTTP/3 requires TLS (1.3), and that's where the nice part comes in: in dev and test mode, if you haven't configured any TLS, Quarkus generates a certificate for you. Here's what dev mode logged when I added the extension:

```text
HTTP/3 (QUIC) support enabled
HTTP/3: auto-generated self-signed TLS certificate (run 'quarkus tls generate-quarkus-ca --install' for browser-trusted certificates)
HTTP/3: insecure HTTP requests will be redirected to HTTPS (set quarkus.http.insecure-requests=enabled to allow plain HTTP)
```

So once you add the extension, `http://localhost:8080` answers with a `301` to `https://localhost:8443`. That surprised me for a second. If you want to keep plain HTTP in dev, set `quarkus.http.insecure-requests=enabled`. And if you want your browser to trust the certificate, run the command from the log once: it creates a local Quarkus CA, and the next generated certificates will be signed by it.

HTTP/1.1, HTTP/2 and HTTP/3 are all served on the same port, TCP for the first two, UDP for QUIC. If your `curl` was built with HTTP/3 support, you can check it with:

```bash
curl --http3 -k https://localhost:8443/hello
```

A few limitations to keep in mind: it relies on a native QUIC library (Maven picks the right one for your OS, Gradle needs you to add it), you need to expose the UDP port when running in a container, and there's no WebSocket over HTTP/3 yet. In other words: great to play with, not something I'd put in production on day one.

### gRPC Moves In With HTTP

One more change on the networking front, and this one *will* bite you if you use gRPC: **the separate gRPC server is gone**. gRPC now runs on the main HTTP server, so port 9000 no longer exists. Update your clients:

```properties
# Before: quarkus.grpc.server.port=9000
quarkus.grpc.clients.hello.port=$\{quarkus.http.port}
```

The server-side TLS properties move too (`quarkus.grpc.server.ssl.*` becomes the regular `quarkus.http.ssl.*`), and certificate files must now be filesystem paths, not classpath resources. One port, one TLS config, one less thing to explain to your ops team.

## The Data Layer: Hibernate ORM 8 and Quarkus Data

Quarkus 4 brings **Hibernate ORM 8**, which implements **Jakarta Persistence 4.0**, together with **Jakarta Data 1.1**. (As of CR1, ORM 8 itself is still at 8.0.0.Beta4, so keep an eye on that one.)

### Quarkus Data

What used to be called *Panache Next* is now **Quarkus Data** (`quarkus-data-hibernate`), aligned with Jakarta Data. It's still flagged **experimental**, and package names may still move, but it's where Panache is heading, so it's worth a look.

Here's an entity with its repository nested inside:

```java
@Entity
public class Book extends RecordEntity {
    public String title;
    public BigDecimal price;

    public interface Repo extends RecordRepository<Book> {
        @Find
        List<Book> findByTitle(String title);

        @Query("where price < :maxPrice order by title")
        List<Book> cheaperThan(BigDecimal maxPrice);
    }
}
```

You inject `Book.Repo` like any other bean. `@Find` and `@Query` come straight from Jakarta Data (`jakarta.data.repository`), and the implementation is generated at build time by an annotation processor you add to the compiler plugin:

```xml
<annotationProcessorPaths>
    <path>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-data-processor</artifactId>
    </path>
</annotationProcessorPaths>
```

My favorite part: **queries are checked at compile time**. I made a typo on purpose, `prcie` instead of `price`, and the build refused to compile:

```text
[ERROR] Book.java:[21,16] Could not interpret path expression 'prcie' [from Book where prcie < :maxPrice order by title]
```

No more finding out in production that you renamed a field and forgot a query.

Notice `RecordEntity`. Quarkus Data offers two models:

- **Record** (`RecordEntity` / `RecordRepository`) uses Hibernate's `StatelessSession`: `insert()` runs an `INSERT` immediately, `update()` runs an `UPDATE`, no persistence context, no dirty checking. Simple and predictable.
- **Managed** (`ManagedEntity` / `ManagedRepository`) is the classic JPA model you know: `persist()`, dirty checking, lazy loading.

Both have reactive variants (`ManagedRepository.Reactive`, `RecordRepository.Reactive`). And if you don't want any Quarkus supertype at all, a plain Jakarta Data `@Repository` interface works too.

One rule to remember: **you can't mix classic Panache and Quarkus Data in the same application**. The build fails with an explicit error. Pick one per project.

### Schema and Data Scripts

**`import.sql` is out.** Well, deprecated. It's replaced by two separate scripts:

- `schema.sql` runs only when Hibernate creates the schema.
- `data.sql` loads data when `quarkus.hibernate-orm.data-management.strategy` is `create`, which is the default in dev and test.

Having both `import.sql` and the new files is an error, so rename yours when you migrate. And if you were (bravely) relying on `import.sql` in production, you now need to opt in explicitly:

```properties
%prod.quarkus.hibernate-orm.data-management.strategy=create
```

### Newer Default Database Versions

Quarkus now assumes recent databases at build time: PostgreSQL 16, MySQL 8.4, MariaDB 11.4, SQL Server 2022, Oracle 23, Db2 12.1. If you're running something older in production, your app will fail at startup with a message along the lines of "configured to run with a database version of at least '16'". The fix is one property:

```properties
quarkus.datasource.db-version=15
```

### `java.time` Handling: Quarkus Keeps the Old Behavior

Hibernate ORM 8 passes `java.time` types directly to the JDBC driver by default. Quarkus deliberately turns that off: it has known issues, like Oracle `LocalTime` values shifting, or `quarkus.hibernate-orm.jdbc.timezone` and `quarkus.hibernate-orm.mapping.timezone.default-storage` being ignored for some types. So your dates keep being converted through `java.sql` types, exactly like in Quarkus 3. If you want to try the new mode, it's a per-persistence-unit opt-in:

```properties
quarkus.hibernate-orm.mapping.java-time.use-direct-jdbc=true
```

### Validation and DDL, Decoupled

If you use Hibernate Validator with ORM, the `DDL` value of `quarkus.hibernate-orm.validation.mode` is deprecated. Whether constraints like `@NotNull` or `@Size` influence the generated schema is now controlled separately, with `quarkus.hibernate-orm.validation.ddl-influence`.

### H2 Without Docker

Small one, but nice: **Dev Services for H2 now run in-process**. Set `quarkus.datasource.db-kind=h2`, leave out the URL, and you get a database without Docker or Podman. `H2DatabaseTestResource` is deprecated accordingly. Handy for demos and lightweight CI.

## Jackson 3: The One That Will Hurt a Bit

The Quarkus team sums it up best: Jackson 3 is a new major version where "everything has changed except for the annotations." In their July progress report they even called it a "**severe** breaking change". At least they're honest.

### New Packages

The most visible part is the package rename:

```java
// Jackson 2
import com.fasterxml.jackson.databind.ObjectMapper;

// Jackson 3
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
```

`com.fasterxml.jackson.core` and `com.fasterxml.jackson.databind` become `tools.jackson.core` and `tools.jackson.databind`. Your `@JsonProperty` and friends stay in `com.fasterxml.jackson.annotation`, which is good news: your DTOs mostly survive. Custom serializers and anything touching the mapper directly are another story.

### Customizing the Mapper

Quarkus now produces a `JsonMapper` bean. Injecting `ObjectMapper` still works, but the customizer API changes. Jackson 3 mappers are immutable, so you customize the *builder* instead of the mapper:

```java
// Quarkus 3
@Singleton
public class MyCustomizer implements ObjectMapperCustomizer {
    public void customize(ObjectMapper mapper) {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
}

// Quarkus 4
import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class MyCustomizer implements JsonMapperBuilderCustomizer {
    public void customize(JsonMapper.Builder builder) {
        builder.enable(SerializationFeature.INDENT_OUTPUT);
    }
}
```

There's also a new `JsonFactoryBuilderCustomizer` if you need to go one level lower.

### Stricter Defaults

This is the part that will pass your unit tests and break a client in production. Jackson 3 changed several defaults, and Quarkus follows most of them:

| Behavior | Jackson 2 | Quarkus 4 default | Property |
|---|---|---|---|
| Fail on trailing tokens | `false` | `true` | `quarkus.jackson.fail-on-trailing-tokens` |
| Fail on `null` for primitives | `false` | `true` | `quarkus.jackson.fail-on-null-for-primitives` |
| Include un-annotated props with `@JsonView` | `true` | `false` | `quarkus.jackson.default-view-inclusion` |
| Use Collection/Map getters as setters | `true` | `false` | `quarkus.jackson.use-getters-as-setters` |

I tried the first two with a simple `record Person(String name, int age)` endpoint. Both used to be accepted silently. Now both get a `400`:

```bash
# trailing garbage after the JSON document
curl -H 'Content-Type: application/json' -d '\{"name":"Duke","age":30} \{}' localhost:8080/echo
# → 400

# null for a primitive int
curl -H 'Content-Type: application/json' -d '\{"name":"Duke","age":null}' localhost:8080/echo
# → 400
```

Honestly, I think both are the right behavior. But if you have clients sending sloppy payloads, every one of these defaults can be switched back with its `quarkus.jackson.*` property while you fix things. For two other defaults (failing on empty beans, and writing durations as timestamps), Quarkus deliberately keeps the Jackson 2 behavior.

### JSON Merge Patch

Jakarta REST 4 adds a `MediaType.APPLICATION_MERGE_PATCH_JSON` constant. It's just the media type, not automatic patching. But combined with Jackson's `readerForUpdating`, a PATCH endpoint is only a few lines:

```java
@PATCH
@Path("\{id}")
@Transactional
@Consumes(MediaType.APPLICATION_MERGE_PATCH_JSON)
public Book patch(@PathParam("id") long id, String mergePatch) {
    Book book = repo.findByIdOptional(id).orElseThrow(NotFoundException::new);
    Book updated = mapper.readerForUpdating(book).readValue(mergePatch);
    repo.update(updated);
    return updated;
}
```

Sending `\{"price": 7.5}` updates the price and leaves the title alone. (Beta2 had a bug where the unpatched fields were nulled; it's fixed in Beta3.)

## CDI 5: Quarkus Concepts Become Standard

ArC now implements **CDI 5.0**, and a few things Quarkus did its own way for years are now in the spec.

### `@Eager` Replaces `@Startup`

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Eager;

@Eager
@ApplicationScoped
public class CacheWarmer {
    @PostConstruct
    void warm() {
        Log.info("Warming caches");
    }
}
```

The bean is created at startup instead of on first use. According to the spec, only `@ApplicationScoped` beans can be eager (Quarkus also allows `@Singleton`).

### `@Reserve` Replaces `@DefaultBean`, With a Catch

`@Reserve` is the opposite of `@Alternative`: a bean that's used only if nothing else provides the same type. That's exactly what `@DefaultBean` did, and `@DefaultBean` is now deprecated for removal.

Here's the catch, and it got me. I swapped `@DefaultBean` for `@Reserve` and the build failed with an `UnsatisfiedResolutionException`. The CDI 5 Javadoc explains why: a reserve "is not available for injection [...] unless the reserve is *selected* for the application (by adding the `@Priority` annotation)." So you need both:

```java
@ApplicationScoped
public class GreeterProducer {
    @Produces
    @Reserve
    @Priority(1)
    Greeter defaultGreeter() {
        return name -> "Hello " + name;
    }
}
```

With that, any other `Greeter` bean in the application wins, and this one is the fallback. It's a mechanical change, but I wouldn't expect `quarkus update` to guess the priority for you.

### `@AutoClose`

The third new annotation: put `@AutoClose` on a bean that implements `AutoCloseable`, and `close()` is called when the bean is destroyed. No more `@PreDestroy void cleanup() { resource.close(); }` boilerplate.

## Observability Out of the Box

This is probably my favorite part of the release, because it removes setup work.

### OpenTelemetry Metrics and Logs, On by Default

Until now, OpenTelemetry in Quarkus mostly meant traces. In Quarkus 4, **OTel metrics and logs are stable and enabled by default**. Add `quarkus-opentelemetry` and you get all three signals.

If you also have `quarkus-micrometer-registry-prometheus`, you'll now collect metrics twice, and Quarkus warns you about it at build time. Pick one, for example with `quarkus.otel.metrics.enabled=false`.

### `@Observed`: One Annotation, Metrics and Traces

The new `quarkus-observation` extension brings the **Micrometer Observation API**. You annotate a method once and get both a timer metric and a span:

```java
import io.micrometer.observation.annotation.Observed;

@ApplicationScoped
public class Things {

    @Observed(name = "things.compute", contextualName = "compute-things")
    public int compute() {
        return 42;
    }
}
```

`name` becomes the metric name, `contextualName` the span name. After one call, my `/q/metrics` endpoint had `things_compute_*` series next to the usual HTTP ones. It works with synchronous methods, `Uni`, `Multi` and `CompletionStage`, and there's a programmatic API (`Observation.createNotStarted(...).observe(...)`) for when an annotation doesn't fit. You still need a backend: Prometheus, OpenTelemetry, or both.

### Observability in the Dev UI

The Dev UI gets a built-in **Observability** page in dev mode. You pick the cards you want (metrics, OpenTelemetry traces...), arrange them, and you get live charts without wiring anything. There's even an **Export Grafana** button that downloads a dashboard you can import into Grafana. Combined with the Grafana LGTM Dev Service (which dev mode started on its own in my test project), you go from zero to a Grafana dashboard without writing a line of config.

<!-- TODO(Loïc): a screenshot of the Dev UI Observability page would be great here. -->

### Defaults That Changed

Two default changes can surprise you in production:

- **The OTLP exporter switches from gRPC to `http/protobuf`**, and the default port goes from 4317 to 4318. If your collector only listens on gRPC, you'll see "Connection refused: localhost/127.0.0.1:4318" warnings and no data.
- **Trace sampling drops to 10%** in production (dev and test stay at 100%).

To restore the old behavior:

```properties
# keep exporting over gRPC
quarkus.otel.exporter.otlp.protocol=grpc
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317

# keep sampling 100% of traces
quarkus.otel.traces.sampler.arg=1.0d
```

Some metric names changed too (for instance with the Prometheus client v1 under Micrometer), so check your dashboards and alerts after upgrading.

## Messaging

### Kafka `@ExactlyOnce`

Exactly-once processing with Kafka has always been possible in Quarkus, but it took some ceremony: a `KafkaTransactions` emitter, `withTransactionAndAck`, and a few config properties. Quarkus 4 boils it down to one annotation:

```java
import io.quarkus.smallrye.reactivemessaging.kafka.ExactlyOnce;
import io.smallrye.reactive.messaging.kafka.Record;

@ApplicationScoped
public class OrderProcessor {

    @Incoming("orders-in")
    @Outgoing("orders-out")
    @ExactlyOnce
    Record<String, Integer> process(Record<String, Integer> order) {
        return Record.of(order.key(), order.value() + 1);
    }
}
```

The result is sent within a Kafka transaction, and the offset of the incoming record is committed as part of the same transaction. Quarkus configures what's needed for you: a `transactional.id` derived from your application name and channel (in my test app, the producer showed up as `transactionalId=q4demo-orders-out`), idempotence, `acks=all`, and `read_committed` on the consumer side. You can even combine it with `@Transactional` to write to the database in the same method.

Current limitations: it works on methods that take and return payloads (not `Message`), and not yet with Kotlin suspend functions. For more advanced cases, `KafkaTransactions` is still there.

### Signals

Quarkus **Signals** (`quarkus-signals`) is a new in-process messaging API. It takes CDI-style type-safe resolution and combines it with the emission modes of the Vert.x event bus: publish-subscribe, point-to-point and request-reply.

```java
public record OrderPlaced(String orderId, BigDecimal total) {}

@Inject
Signal<OrderPlaced> orderPlaced;

orderPlaced.publish(new OrderPlaced("123", BigDecimal.TEN));   // everyone
orderPlaced.send(new OrderPlaced("123", BigDecimal.TEN));      // one receiver
String confirmation = orderPlaced.request(
        new OrderPlaced("123", BigDecimal.TEN), String.class); // request-reply
```

```java
@ApplicationScoped
public class OrderHandlers {
    void onOrderPlaced(@Receives OrderPlaced order, NotificationService notifications) {
        notifications.send("Order placed: " + order.orderId());
    }
}
```

Receivers always run asynchronously. Early roadmap posts said it would replace the Vert.x event bus, but the team has since [clarified](https://quarkus.io/blog/quarkus-insights-261-quarkus-signals/) that it's an addition, not a replacement: CDI events and the Vert.x event bus are not going away. And after being experimental since 3.36, it's now **stable** as of 4.0.

### JMS

JMS support also got some attention, but in the Quarkiverse rather than in core: [Quarkus JMS](https://docs.quarkiverse.io/quarkus-jms/dev/quarkus-jms.html) is a new extension with `@JmsListener`, a typed `JmsQueue<T>` for sending, a Spring JMS compatibility layer, and integration with pooled JMS and IronJacamar. It's in preview, but if you've got a Spring JMS app to migrate, it's worth a look.

## Everything Else Worth Knowing

A lot more landed than I can cover in detail. In no particular order:

- **Stricter HTTP**: `;` is no longer a query-parameter separator. With `?a=1;b=2`, I got `a = "1;b=2"` and `b = null`. Restore with `quarkus.http.use-semicolon-as-query-param-delimiter=true`. Malformed JSON and multipart bodies now return `400` instead of a server error.
- **Jakarta REST 4 and RESTEasy 7**: `SseEventSink.close()` now throws `IOException`, and Quarkus REST endpoints can no longer return a REST Client `Response`.
- **Security**: post-quantum key exchange through the TLS registry, a SPIFFE client extension, OIDC client attestation. `OidcTenantConfig` setters are gone in favor of `OidcTenantConfig.builder()`.
- **Fault Tolerance 7**: `@Blocking` / `@NonBlocking` on guarded methods now fail the build.
- **Smaller images**: JLink module tree shaking, and container images now based on UBI 10.
- **Artifact renames**: the old relocations are gone, so `quarkus-junit5` becomes `quarkus-junit`, `quarkus-junit5-mockito` becomes `quarkus-junit-mockito`, and so on.
- **Removals**: the Scala 2 extension (a Scala 3 one lives in the Quarkiverse), and config properties deprecated before 3.33.
- **Under the hood**: extensions are moving from bytecode recorders to a new service start/stop infrastructure with explicit dependencies. Nothing to do for you, but it's the kind of groundwork that makes startup and shutdown easier to reason about. Graceful shutdown moved to this infrastructure too, and its handlers now run in parallel when parallel start/stop is enabled (the old APIs are deprecated, which matters if you write extensions).
- **Tooling**: Gradle 9.6+ is required, and the docs now recommend [mise](https://mise.jdx.dev/) alongside SDKMAN. And the CLI finally has remote dev mode: `quarkus dev --remote` replaces `./mvnw quarkus:remote-dev`, reading the URL and password from `quarkus.live-reload.url` and `quarkus.live-reload.password`.

## What About AI?

AI was one of the three pillars of the roadmap, so you might expect it front and center. It isn't, at least not in this first release, and I think that's the right call. The `.0` lays the foundations, and the AI work is happening in parallel: a non-blocking rewrite of LangChain4j, a revamp of agentic patterns (which I [wrote about here](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/)), and MCP server 2.0 with stateful and stateless modes. Expect it to show up over the 4.x releases.

<!-- TODO(Loïc): update this section if the GA announcement includes AI items. -->

## Migrating: My Checklist

Here's how I'd approach it:

1. **Get to Java 21 (ideally 25) first**, on your current 3.x version.
2. **Move to 3.40 LTS and clear your deprecation warnings.** Quarkus 4 removes properties deprecated before 3.33, so a clean 3.40 makes the jump smaller.
3. **Run `quarkus update`.** It handles config renames and many of the mechanical changes for you:

   ```bash
   quarkus update --stream=4.0
   # or with Maven
   ./mvnw quarkus:update -Dstream=4.0
   ```

4. **Read the [migration guide](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-4.0)** for what can't be automated. Here's where I'd look first:

   | Area | What to check |
   |---|---|
   | Jackson 3 | Imports, custom serializers, `ObjectMapperCustomizer`, stricter defaults |
   | Hibernate ORM 8 | `import.sql` → `schema.sql` / `data.sql`, database version, removed APIs (`Session.replicate()`, `QueryHints`...), validation `mode=ddl` → `ddl-influence` |
   | CDI | `@DefaultBean` → `@Reserve` **+ `@Priority`**, `@Startup` → `@Eager` |
   | gRPC | Port 9000 is gone, TLS config moves to `quarkus.http.ssl.*` |
   | OpenTelemetry | OTLP protocol and port, 10% sampling, metric names |
   | HTTP | `;` in query strings, `400` on malformed bodies |
   | Build | `quarkus-junit5` → `quarkus-junit`, Gradle 9.6+ |

5. **Test with real payloads.** The stricter Jackson and HTTP defaults are exactly the kind of change that passes your unit tests and breaks a client in production. If you have contract tests or recorded traffic, now is the time to use them.

And if you're on an LTS with no urgent need, waiting for 4.3 LTS in March is a perfectly reasonable plan. Just don't wait until then to *read* the migration guide.

<!-- TODO(Loïc): add your own migration story here (this blog? a demo project?): what broke, how long it took. That's the part readers will remember. -->

## Wrap-Up

Quarkus 4 isn't a flashy release, and it isn't trying to be. It's a new floor: Java 21, Vert.x 5, Hibernate 8, Jackson 3, CDI 5. It also gets rid of a lot of the compatibility baggage the 3.x line had to carry. The visible wins are real: observability that works out of the box, compile-time checked queries with Quarkus Data, exactly-once Kafka in one annotation, HTTP/3 to play with. But the bigger win is what this foundation makes possible over the next releases.

Some migrations will be smooth, others will spend an afternoon fighting Jackson. Either way, the direction is right, and I'm looking forward to seeing what 4.x becomes.

Have you tried the betas yet? Let me know what broke, and what you liked.
