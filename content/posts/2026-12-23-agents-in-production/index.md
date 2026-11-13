---
title: "Agents in Production: Observability, Model Routing and Resilience with LangChain4j"
description: "The last part of the series on agentic patterns from my Devoxx deep dive: seeing inside an agentic system with AgentListener and AgentMonitor, choosing a cheap or strong model per call, surviving flaky models with optional agents and bounded retries, async agents, the parallel mapper for batching, and the declarative API."
tags: [java, langchain4j, ai]
author: Loïc
draft: true
---
<!-- TODO(Loïc): add a cover image (image: ...) -->

## The Demo Works. Now What?

Not long ago, a client asked me to build an AI agent. The job: read an Excel file, do some computation, write another Excel file. I was like, "Hey, you don't need an agent for that. We can write code." And they said, "Yeah, but can we try with an agent?" They're the client, so I said yes. But please, don't do that.

I'm starting with that story because this article is about the unglamorous part of agentic systems. Over this series, we went through a lot of patterns: [peer-to-peer and blackboard](/posts/when-the-state-is-the-boss-peer-to-peer-and-blackboard-agents-with-langchain4j/), [voting and debate](/posts/agent-democracy-voting-and-debate-patterns-with-langchain4j/), [BDI and custom planners](/posts/beliefs-desires-intentions-bdi-agents-and-custom-planners-with-langchain4j/), and before that, [the workflows, goal-oriented planning and the supervisor](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/). They make great demos. But once a system leaves your laptop, other questions show up. What is it doing right now? Why is it so slow? Why did this month's bill double? What happens when the model drops a call at 3 a.m.?

This article comes from the second half of my three-hour Devoxx deep dive on LangChain4j. It's the toolbox I reach for once a system has to run for real: seeing inside it, paying less, surviving failures, scaling out, and writing less code. All the examples come from the [demo repository](https://github.com/LoMagnette/deep-dive-langchain4j-devoxx-2026), where a team of rescue puppies handles the missions. No cats. I'm a dog person.

The code targets `langchain4j` 1.22.0 and `langchain4j-agentic` 1.22.0-beta32. The agentic module is still in beta, so names can move between releases.

## See It: Listeners, Monitors and a Free HTML Report

When you have three agents, you can keep the whole system in your head. When you have twenty, you can't. I know because I tried. At some point I started building my own little tool to draw what my agents were doing. Then I checked the next LangChain4j release notes, and it was already there. That felt good: I wasn't the only one with that problem.

### AgentListener: a hook on every step

In my demos, I always pass a listener so the web front end can show what's happening. On stage I said, "Don't worry too much about it." In a real system you should worry about it, because it's your first observability tool.

`AgentListener` is an interface from `dev.langchain4j.agentic.observability`. Every method has a default, so you only override the moments you care about:

```java
public class AuditListener implements AgentListener {

    @Override
    public boolean inheritedBySubagents() {
        return true; // also listen to every sub-agent, not only the top-level one
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        log.info("-> {}", request.agentName());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        log.info("<- {} : {}", response.agentName(), response.output());
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        log.error("x {} failed", error.agentName(), error.error());
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution tool) {
        var request = tool.toolExecution().request();
        log.info("{} calls {}({})", tool.agentInstance().name(), request.name(), request.arguments());
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution tool) {
        log.info("tool {} -> {}", tool.toolExecution().request().name(), tool.toolExecution().result());
    }

    @Override
    public void afterAgenticScopeCreated(AgenticScope scope) { /* a run starts */ }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope scope) { /* a run ends */ }

    @Override
    public void onAgenticSystemSuspended(AgenticScope scope) { /* e.g. waiting for a human */ }
}
```

A few things are worth pointing out:

- `inheritedBySubagents()` decides whether the listener is propagated to every sub-agent. Return `false` and you only hear about the top-level agent. Sometimes that's what you want, for example a listener that only times the whole run.
- `AgentRequest` and `AgentResponse` give you an `agentId()`, so you can measure how long each agent took. In my demo I store `System.nanoTime()` in a map keyed by the agent id in `before…` and compute the duration in `after…`.
- The tool hooks are the ones I like the most. The model decides which tools to call and in which order, and those hooks are where you can see that decision.
- `onAgenticSystemSuspended` fires when the system stops and waits, for example on a [human-in-the-loop](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/) step.

You plug it into any builder with `.listener(...)`. You're not limited to one: call it as many times as you need, one for logs, one for metrics, one for your front end.

```java
RoseRescue app = AgenticServices.sequenceBuilder(RoseRescue.class)
        .subAgents(plan, approval, act)
        .outputKey(RescueStatus.class)
        .listener(auditListener)
        .listener(monitor)
        .build();
```

### AgentMonitor and the HTML report

That second listener, `monitor`, is an `AgentMonitor`. It ships with LangChain4j and it's a listener with more on top. Instead of raw events, it records the runs. And it comes with an HTML report generator:

```java
var monitor = new AgentMonitor();

// ... build the system with .listener(monitor) and run it ...

HtmlReportGenerator.generateReport(monitor, Path.of("human-in-the-loop.html"));
```

Open that file in a browser and you get two things. First, the topology of your agentic system: which agent contains which, drawn for you. With four agents, you could draw it yourself. With twenty, you'll be happy to have it. Second, the execution: every step and the time spent in it. If someone tells you, "Hey, the system takes five minutes and I don't know why," plug in the monitor and you'll see where the time goes, without setting up Grafana or tracing. It's just a file.

<!-- TODO(Loïc): add a screenshot of the generated HTML report (the one from the human-in-the-loop demo). -->

Let's be honest, it won't replace real observability in production. But for development, for a code review, or for explaining the system to a colleague, it's really useful.

## Pay Less: Choose the Model per Call

You don't always need the biggest model. Sometimes the task is trivial. Sometimes the result is almost there and a small model can finish the job. And sometimes the input is so bad that no model will save it. Putting your best model on that is putting lipstick on a pig.

In the demo, the puppies have a night phone. A classifier puts every call in a category, and one agent, Doc, answers everything. A lost umbrella doesn't need the strong model. A swollen ankle does.

The trick is that `chatModel(...)` has an overload taking a `Function<AgenticScope, ChatModel>`. The model is chosen when the agent is invoked, not when it's built, so it can read whatever the previous agents wrote in the scope:

```java
private static final Set<String> SERIOUS = Set.of("hurt", "urgent");

var classifier = AgenticServices.agentBuilder(CallClassifier.class)
        .chatModel(cheapModel) // one word of output: always the cheap model
        .outputKey(Category.class)
        .build();

var doc = AgenticServices.agentBuilder(DocOnNights.class)
        .chatModel(scope -> SERIOUS.contains(scope.readState(Category.class))
                ? strongModel
                : cheapModel)
        .outputKey(Response.class)
        .build();

NightPhone phone = AgenticServices.sequenceBuilder(NightPhone.class)
        .subAgents(classifier, doc)
        .outputKey(Response.class)
        .build();
```

No magic here. It's a good old lambda, and you can put any logic you want in it: a category, a score from a previous agent, the customer's plan, the time of day.

One caveat to keep in mind. Your classifier is now a cost decision as well as a routing decision, and its mistakes aren't symmetric. Using the strong model when the cheap one was enough costs you a few cents. Using the cheap model on the swollen ankle is the expensive mistake, and it's the silent one: you get an answer, just a worse one. So when the classifier can't decide, please fall back to the strong model. And as with [conditional routing](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/), have the classifier return an enum instead of free text: a typo like `Hurt` instead of `hurt` shouldn't decide which model you pay for.

## Survive Failures: Optional Agents and Bounded Retries

Models are non-deterministic, and they'll fail. Probably in production, not on your laptop, because that would be no fun. The basics are in the [error handling section of the first article](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/): an error handler returns `retry()`, `result(...)` or `throwException()`. Here I want to go a bit further, with two tools that look alike but answer different questions.

### Optional agents: when an input is missing

During the talk, I presented `optional` as a way to skip an agent that fails. Mario Fusco, who builds the agentic module, was in the room and added a clarification: he introduced it for another reason, and I think it's the better use case. Sometimes an agent's input just isn't there. If you do nothing, the whole system stops with a `MissingArgumentException` naming a step that looks unrelated. Mark the agent as optional and the planner skips it instead.

In the demo, Doc gives first aid when the rescue report says someone is hurt. Most days nobody is, so most runs never write the `Injuries` key:

```java
public record Injuries() implements TypedKey<String> {} // no default value, on purpose

var doc = AgenticServices.agentBuilder(DocFirstAid.class)
        .chatModel(model)
        .outputKey(FirstAid.class)
        .optional(true) // skipped when Injuries isn't in the scope
        .build();
```

Two traps here. Give `Injuries` a default value and Doc always has an input, so it's never skipped. And when Doc is skipped, `FirstAid` stays empty, so whatever reads it has to expect `null`.

### Retries: when a call fails

The error handler is for a call that fails: a dropped connection, a timeout, the model that returns broken JSON one time out of ten (if you ever asked a model for JSON, you know). Retrying is often the cheapest fix. But there's a trap: `retry()` runs the agent again, and if it fails again, you end up back in the same handler. A handler that always retries never stops.

So keep a counter:

```java
private static final int MAX_RETRIES = 2;

AtomicInteger attempts = new AtomicInteger();

BadRadioDay rescue = AgenticServices.sequenceBuilder(BadRadioDay.class)
        .subAgents(sniff, doc, zoom)
        .errorHandler(ctx -> attempts.incrementAndGet() <= MAX_RETRIES
                ? ErrorRecoveryResult.retry()
                : ErrorRecoveryResult.result("(Sniff's radio is down, no location)"))
        .build();
```

Below the threshold, we retry. Above it, you choose: give up with `throwException()`, or land on your feet with a fallback value and let the rest of the workflow continue. Here the rescue goes on without a location rather than failing completely.

To test this, I didn't wait for the network to misbehave. In the demo there's a `FlakyModel`, a `ChatModel` wrapper that throws on its first call and then delegates to the real model. If you wait for a real failure, of course, it never happens during the demo. Please do the same in your tests: make the failure happen on purpose.

## Scale Out: Async Agents and the Parallel Mapper

### Async: wait only where you need the result

The first article covered the [parallel workflow](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/): several agents running at the same time. Sometimes you don't need a whole parallel block. You have a sequence, and one step is slow and nobody needs its result right away. Mark that step as async:

```java
var forestCheck = AgenticServices.agentBuilder(SniffChecksForest.class)
        .chatModel(model)
        .outputKey(ForestReport.class)
        .async(true)
        .build();

StormRound round = AgenticServices.sequenceBuilder(StormRound.class)
        .subAgents(forestCheck, bridgeCheck, tunnelCheck, mergeReports)
        .outputKey(SafetyReport.class)
        .build();
```

It's still a sequence, and the forest check still starts first. But the bridge and tunnel checks don't wait for it. The run only waits for it when `mergeReports` reads `ForestReport`. That read is the join, and you don't declare it anywhere.

The catch is that you still wait, only somewhere less obvious. If the async agent fails, you'll see the error when its key is read, far from the step that caused it. Only mark a step async when nothing between it and its reader needs it.

### Parallel mapper: the same agent, many times

The parallel workflow runs different agents on one input. The parallel mapper does the opposite: one agent, many inputs, all at once. In the demo, eight ducklings are lost and each was last seen somewhere else. One tracker agent, sent eight times:

```java
public record Ducklings() implements TypedKey<List<String>> {}
public record Sighting() implements TypedKey<String> {}
public record FoundDucklings() implements TypedKey<List<String>> {}

public interface DucklingSearch {
    @Agent
    List<String> search(@V("Ducklings") List<String> ducklings);
}

var sniff = AgenticServices.agentBuilder(SniffSearches.class)
        .chatModel(model)
        .outputKey(Sighting.class)
        .build();

DucklingSearch search = AgenticServices.parallelMapperBuilder(DucklingSearch.class)
        .subAgents(sniff)                         // only one sub-agent
        .itemsProvider(new Ducklings().name())    // the collection in the scope to iterate over
        .outputKey(FoundDucklings.class)
        .build();

List<String> found = search.search(ducklings);
```

`itemsProvider` points at a collection in the scope. For each item, the mapper runs the sub-agent with that item bound to its first parameter, and gathers the results in the same order. The fan-out is decided by the data: add a ninth duckling and you get nine searches. On my laptop with a local model it took about thirty seconds, because Ollama doesn't love concurrent requests. On a real provider it's much faster.

Good use cases: batch classification, extracting data from a pile of documents, or sending a batch of emails where each one is personalized for its recipient.

The caveats:

- Every item must be independent. If item 3 needs the result of item 2, this isn't your pattern.
- Each item is a model call. Nothing in the builder checks how long the list is, so a list of 5,000 customers is 5,000 concurrent calls. Watch your rate limits and your bill. Tokens are not free.
- The instances don't share a chat memory. Each one only sees its own item.

## Write Less: The Declarative API

In the talk and in this series I used builders everywhere, because when you start, it's easier to see everything in one place. But LangChain4j also has a declarative API, and once you know the builders, it often reads better. Every builder call becomes an annotation attribute or a static method on the interface.

Here's the night phone again, declared:

```java
public interface NightPhone {

    @SequenceAgent(subAgents = { CallClassifier.class, DocOnNights.class },
                   typedOutputKey = Response.class)
    String answer(@K(Call.class) String call);
}

public interface DocOnNights {

    @Agent(typedOutputKey = Response.class,
           description = "Answers every call on the night phone")
    @UserMessage("""
            You are on the night phone. Answer this call directly and practically.
            The call: {{Call}}""")
    String answer(@K(Call.class) String call);

    @ChatModelSupplier
    static ChatModel model(@K(Category.class) String category) {
        return SERIOUS.contains(category) ? STRONG_MODEL : CHEAP_MODEL;
    }
}

NightPhone phone = AgenticServices.createAgenticSystem(NightPhone.class, cheapModel);
```

The model choice now lives on the agent that needs it. And `@ChatModelSupplier` gets its input with `@K`, like any agent parameter, so there's no scope reading at all.

The resilience example works the same way: `optional = true` goes on the agent's `@Agent` annotation, and the error handler becomes a static method annotated with `@ErrorHandler` that receives an `ErrorContext`. Since a static method can't close over an `AtomicInteger`, the demo keeps the attempt counter in the scope:

```java
@ErrorHandler
static ErrorRecoveryResult radioDropped(ErrorContext error) {
    AgenticScope scope = error.agenticScope();
    int attempts = requireNonNullElse(scope.readState(Attempts.class), 0) + 1;
    scope.writeState(Attempts.class, attempts);
    return attempts <= MAX_RETRIES
            ? ErrorRecoveryResult.retry()
            : ErrorRecoveryResult.result("(Sniff's radio is down, no location)");
}
```

You'll find an annotation for almost every pattern from this series: `@LoopAgent` with an `@ExitCondition` method, `@ParallelAgent`, `@ParallelMapperAgent`, `@ConditionalAgent` with `@ActivationCondition`, `@SupervisorAgent`, and `@PlannerAgent` with a `@PlannerSupplier` method for the goal-oriented, peer-to-peer, blackboard, voting, debate and BDI planners. Listeners come from an `@AgentListenerSupplier` method.

What I like about it: open the interface and you know right away what this agent is and how it's orchestrated. With builders, an interface can be used in a loop somewhere in your wiring code and nothing in the interface tells you. The downside: hooks are static methods, so anything they need (a model, a listener, a counter) has to be reachable statically or live in the scope. For big systems I end up mixing both.

## Before You Ship

A few last things that didn't fit anywhere else, but that I didn't want to leave out.

### Check your dependencies

The workflows (sequence, loop, parallel, conditional, parallel mapper), the supervisor and everything in this article live in `langchain4j-agentic`. The planners from this series (goal-oriented, peer-to-peer, blackboard, voting, debate, BDI) live in `langchain4j-agentic-patterns`. If a planner class can't be found, check your POM first.

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agentic</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agentic-patterns</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```

### Use your framework's integration

I used plain LangChain4j in the talk so you could see the bare experience. If you're on Quarkus, please use the Quarkus LangChain4j extension: in my opinion it's the most advanced integration of LangChain4j in a framework. I'm a big Quarkus fan, so take that as it may. Spring and CDI have integrations too.

### Start with workflows

In my experience with clients, most real systems are workflows. Maybe 10% genuinely need more. Workflows give you the most control, and the supervisor gives you almost none. The patterns from this series sit in between. Don't choose a pattern because it looks cool. The pattern you don't use is the one you don't need to debug. And when you do mix patterns, remember that everything you put in there, you'll have to maintain.

### Keep a human where it matters

If tomorrow your bank offers you an AI agent to manage your money, would you let it? Personally, not yet. For sensitive actions, keep a human-in-the-loop step, and plug in a listener so you can see when the system is suspended waiting for one.

### Watch the docs

This module moves fast. I already told you in the [voting and debate article](/posts/agent-democracy-voting-and-debate-patterns-with-langchain4j/) about the release that landed right before my talk. And right after my talk, Mario showed a router that asks a decision model which agent to call instead of using a lambda. The [agentic tutorial in the LangChain4j documentation](https://docs.langchain4j.dev/tutorials/agents/) is the page to check regularly. Two things I haven't dug into yet but you'll find there: an A2A integration to call agents running on other systems (if A2A is new to you, I wrote [an introduction to A2A](/posts/from-lonely-agents-to-talking-teams-an-introduction-to-a2a/)), and a recent agent registry to pick agents from.

<!-- TODO(Loïc): check whether the decision-model router and agent registry have doc pages worth linking. -->

## Wrap Up

That's the end of the series. If you followed all of it, thank you. We went from the [workflows](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/), to agents driven by a [shared state](/posts/when-the-state-is-the-boss-peer-to-peer-and-blackboard-agents-with-langchain4j/), to agents that [vote and debate](/posts/agent-democracy-voting-and-debate-patterns-with-langchain4j/), to [beliefs, desires and intentions and our own planners](/posts/beliefs-desires-intentions-bdi-agents-and-custom-planners-with-langchain4j/), and finally to what it takes to run all that for real.

Three things to remember, only three:

1. Plug in a listener from day one. An `AgentMonitor` and its HTML report cost you three lines and save you hours the first time something is slow or wrong.
2. Make cost and failure explicit: choose the model per call, bound your retries with a counter, and use `optional` for inputs that may legitimately be missing.
3. Start with workflows, and remember that not everything needs to be an agent. My client's Excel file still doesn't need one. Sometimes good old Java code is the right answer.

All the code is in the [demo repository](https://github.com/LoMagnette/deep-dive-langchain4j-devoxx-2026), including a `declarative-api` branch with the annotation version of every demo. It runs on a local model, so no API key and no tokens to pay. Go play with it, and if you build a planner of your own, Mario would love a pull request to `langchain4j-agentic-patterns`.

And if you attended a talk recently, mine or anyone else's, please take a minute to give feedback to the speaker. We give the same talk many times, and your feedback is what makes the next one better.
