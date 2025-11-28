# blog

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/blog-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Roq Plugin QrCode ([guide](https://iamroq.com/docs/plugins/#plugin-qrcode)): This plugin allows generate a QR Code in ROQ
- Qute Web Markdown ([guide](https://docs.quarkiverse.io/quarkus-qute-web/dev/markdown.html)): Render Markdown templates using Qute
- Roq Plugin Diagram ([guide](https://iamroq.com/docs/plugins/#plugin-diagram)): This plugin allows generate any kind of Diagram as code by leveraging Kroki.io
- Roq ([guide](https://iamroq.com/docs/)): Hello, world! I’m Roq — a fun little SSG (Static Site Generator) with a Java soul and Quarkus energy.
- Roq Plugin - Asciidoc Java ([guide](https://iamroq.com/docs/plugins/#plugin-asciidoc)): This plugin enables Asciidoc support in Roq with a fast startup time and full compatibility with native builds. However, it has limited features and does not support Asciidoc extensions (such as diagram).
- Roq Plugin - Tagging ([guide](https://iamroq.com/docs/plugins/#plugin-tagging)): This plugin allows to generate a dynamic (derived) collection based on a given collection tags.
- Roq Plugin Markdown ([guide](https://iamroq.com/docs/plugins/#plugin-markdown)): This plugin allows to use Markdown in ROQ
- Roq Plugin Series ([guide](https://iamroq.com/docs/plugins/#plugin-series)): This plugin allows you to join multiple posts in a series.
- Roq FrontMatter ([guide](https://docs.quarkiverse.io/quarkus-roq/dev/quarkus-roq-frontmatter.html)): Create a website from your Markdown/Asciidoc/Html pages using FrontMatter headers (url, layout, seo, data).
- Roq Plugin - Lunr ([guide](https://iamroq.com/docs/plugins/#plugin-lunr)): This plugin adds lunr.js to your website -with prebuilt index- to provide searching capabilities
- Roq Data ([guide](https://docs.quarkiverse.io/quarkus-roq/dev/quarkus-roq-data.html)): Use json/yaml files content from your templates and articles with type safety.
- Roq Plugin - Aliases ([guide](https://iamroq.com/docs/plugins/#plugin-aliases)): This plugin allows creating one or many aliases (redirections) for a page.
- Roq Plugin - Sitemap ([guide](https://iamroq.com/docs/plugins/#plugin-sitemap)): This plugin allows to create a sitemap.xml for your site
- Roq Generator ([guide](https://docs.quarkiverse.io/quarkus-roq/dev/quarkus-roq-generator.html)): Command to run any Quarkus web application and extract it in a directory as purely static files (html and assets).

## Provided Code
