---
title: "From AI to Agent with Langchain4j"
description: "Learn how to orchestrate multiple AI agents in Java using LangChain4j's agentic module, from sequential pipelines and loops to goal-oriented planning and LLM-driven supervisors."
tags: [java, langchain4j, ai]
author: Loïc
image: ai-to-agent-cover.png
---
You have built AI features into your Java application. Your model is wrapped in a service, RAG is feeding it context, tools are wired, and calls are flCowing. It works. Then requirements evolve. A single prompt-and-response is no longer enough. You need steps that follow each other, branches based on decisions, retries when things fail, and multiple actions running concurrently. The question shifts from "how do I call an LLM?" to "how do I orchestrate multiple LLM-driven tasks into a coherent system?"

This article walks through that transition using LangChain4j's agentic module. We will start from simple agents, move through four workflow patterns and their composition, then progress to goal-oriented planning and fully agentic systems, covering shared state, error handling, non-AI agents, human-in-the-loop, and the critical question of when to use which approach.

The intended audience is Java developers who already have basic AI integration experience. If you know how to call a model and wire a tool, you are ready for what comes next.


## Getting Started

LangChain4j's agentic module is currently in beta. Add it alongside the core library and your preferred model provider:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agentic</artifactId>
    <version>1.11.0-beta19</version>
</dependency>
```

The goal-oriented planner lives in a separate module, currently only available as a snapshot (see the Goal-Oriented Agents section for details):

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agentic-patterns</artifactId>
    <version>1.12.0-beta20-SNAPSHOT</version>
</dependency>
```

All examples in this article use `AgenticServices` as the entry point for building agents and workflows. If you have used `AiServices` to build AI-backed interfaces, `AgenticServices` extends that same pattern for multi-agent orchestration.

The examples configure a chat model like this:

```java
ChatModel model = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama3.2:1b")
    .build();
```

Any LangChain4j-supported model provider works. Swap in OpenAI, Anthropic, or Azure as needed. Check the [LangChain4j documentation](https://docs.langchain4j.dev) for the latest artifact coordinates and version.

Code examples use Java 23+ preview features (`IO.println`, `IO.readln`) for concise I/O. Replace with `System.out.println` and `BufferedReader` if you are on an earlier version.


## The Building Block: What Is an Agent?

Before we orchestrate anything, we need a common unit of work. In LangChain4j, an agent is a Java interface with a single method that performs a task, typically backed by an LLM. You define it declaratively:

```java
public interface SithNameGenerator {
    @UserMessage("""
            You are a Sith naming council. Transform this boring name into something \
            properly villainous. Return ONLY the Sith name, nothing else: {{jediName}}""")
    @Agent("Transforms a Jedi name into a suitably menacing Sith identity")
    String darken(@V("jediName") String jediName);
}
```

The `@Agent` annotation marks this as an agent with a description. The `@UserMessage` provides the prompt template. `@V` binds the method parameter to a template variable. We will see shortly why this annotation exists, as it becomes essential when agents communicate through shared state.

To instantiate an agent, you use the builder:

```java
SithNameGenerator sithNamer = AgenticServices
    .agentBuilder(SithNameGenerator.class)
    .chatModel(model)
    .outputKey("sithName")
    .build();

String sithName = sithNamer.darken("Obi-Wan Kenobi");
```

Two details matter here. First, every agent has a **name** that uniquely identifies it in a system (derived from the method name or set explicitly). Second, every agent has an **output key**, a named slot in shared state where it writes its result. That output key is how agents communicate with each other without direct coupling.

This is the fundamental contract: an agent reads inputs, does its work, and writes its result to a shared location. Everything else in this article builds on that idea.


## Shared State with AgenticScope

Once you have more than one agent, the question becomes: how do they exchange data? If agents communicate through named keys rather than direct method calls, there needs to be a shared container holding those keys. That container is the `AgenticScope`.

The AgenticScope implements the blackboard pattern: a shared state object that multiple independent components read from and write to, with no direct coupling between them. When the root agent is invoked, the scope is created automatically. Each subsequent agent reads what it needs, does its work, and writes its output back.

The core operations are straightforward:

- `writeState(key, value)`: store a result
- `readState(key)`: retrieve a value
- `readState(key, defaultValue)`: retrieve with a fallback
- `hasState(key)`: check if a key exists

This pattern enables loose coupling. Agent A does not call Agent B. Agent A writes `"interceptedTransmission"` to the scope. Agent B reads `"interceptedTransmission"` from the scope. Neither knows about the other. The orchestration layer decides the order.

The scope also maintains an invocation history: `invocations()` returns the full list of agent executions and their responses, giving you a built-in audit trail.

### `@V` vs `@K`: External Input vs Scope Input

Before we look at more examples, one distinction is critical. LangChain4j uses two annotations to feed data into agents:

- **`@V("name")`** binds a method parameter directly, used when calling an agent from outside or as the entry point of a pipeline. The value is also written to the scope, making it available to downstream agents via `@K`.
- **`@K(Key.class)`** reads a value from the AgenticScope, used when an agent runs inside a workflow and consumes data written by a previous agent.

### Typed Keys

For production use, LangChain4j offers typed keys to prevent spelling errors and type mismatches:

```java
public static class InterceptedTransmission implements TypedKey<String> { }

public interface ThreatClassifier {
    @Agent("Classifies the threat level of an intercepted Imperial transmission")
    @UserMessage("""
            You are a Rebel Alliance intelligence analyst. \
            Classify the threat level of this intercepted Imperial transmission as \
            LOW, MEDIUM, HIGH, or CRITICAL. Respond with the level and a brief justification.

            Transmission: {{InterceptedTransmission}}""")
    String classify(@K(InterceptedTransmission.class) String transmission);
}
```

The `@K` annotation maps a method parameter to a typed scope variable. The `TypedKey` class it references defines both the key name and the expected type, letting the compiler catch mismatches rather than your production logs.

Typed keys can also define a `defaultValue()` for when the key has not been written yet:

```java
public static class MasterApprovals implements TypedKey<Integer> {
    @Override
    public Integer defaultValue() {
        return 0;
    }
}
```

You can chain agents in a typed sequence. The `TransmissionInterceptor` receives input via `@V` and writes to `InterceptedTransmission`; the `ThreatClassifier` reads it via `@K`:

```java
IntelPipeline pipeline = AgenticServices
    .sequenceBuilder(IntelPipeline.class)
    .subAgents(interceptor, classifier)
    .outputKey(ThreatLevel.class)
    .build();

String threatAssessment = pipeline.analyze(scrambledInput);
```

When your system grows to dozens of agents, catching a `ThreatLevel` misread as a `String` at compile time is not a convenience. It is survival.


## Workflow Pattern 1: Sequential

The simplest orchestration pattern is the sequential workflow. Agents execute one after another in a defined order, each reading from and writing to the AgenticScope:

```java
public static class AptitudeReport implements TypedKey<String> { }
public static class AssignedMaster implements TypedKey<String> { }
public static class LightsaberRecommendation implements TypedKey<String> { }

public interface AptitudeTester {
    @Agent("Evaluates a youngling's Force sensitivity and aptitudes")
    @UserMessage("""
            You are a Jedi Temple instructor. Evaluate this youngling's Force aptitude. \
            Provide a short report covering their strengths.

            Youngling: {{name}}""")
    String test(@V("name") String name);
}

public interface MasterAssigner {
    @Agent("Assigns the most suitable Jedi Master based on the youngling's aptitudes")
    @UserMessage("""
            You are the Jedi Council. Based on this aptitude report, assign the most \
            suitable Jedi Master from the Order.

            Aptitude report: {{AptitudeReport}}""")
    String assign(@K(AptitudeReport.class) String aptitude);
}

public interface LightsaberGuide {
    @Agent("Recommends lightsaber form and crystal based on master assignment and aptitude")
    @UserMessage("""
            You are the Jedi Temple lightsaber instructor. Based on the assigned master \
            and training path, recommend a lightsaber form and kyber crystal color.

            Assigned master: {{AssignedMaster}}""")
    String guide(@K(AssignedMaster.class) String master);
}
```

Each agent declares what it reads (`@K`) and writes (`outputKey`). The pipeline chains them together:

```java
AptitudeTester aptitudeTester = AgenticServices
    .agentBuilder(AptitudeTester.class)
    .chatModel(model)
    .outputKey(AptitudeReport.class)
    .build();

MasterAssigner masterAssigner = AgenticServices
    .agentBuilder(MasterAssigner.class)
    .chatModel(model)
    .outputKey(AssignedMaster.class)
    .build();

LightsaberGuide lightsaberGuide = AgenticServices
    .agentBuilder(LightsaberGuide.class)
    .chatModel(model)
    .outputKey(LightsaberRecommendation.class)
    .build();

JediTrainingPipeline pipeline = AgenticServices
    .sequenceBuilder(JediTrainingPipeline.class)
    .subAgents(aptitudeTester, masterAssigner, lightsaberGuide)
    .outputKey(LightsaberRecommendation.class)
    .build();

String result = pipeline.train("Ahsoka Tano");
```

Here, `aptitudeTester` measures Force aptitude and writes an `AptitudeReport` to the scope. Then `masterAssigner` reads the report via `@K` and pairs the Padawan with a compatible Master. Finally, `lightsaberGuide` reads the `AssignedMaster` and produces the weapon construction specifications.

This is the pattern you reach for when tasks have a natural ordering and each step depends on the previous one. Think of content pipelines, multi-stage data processing, or any chain-of-thought decomposition where you want explicit control over each step.

One note on the builder: `sequenceBuilder(JediTrainingPipeline.class)` creates a typed pipeline, giving you a concrete interface to call. When you don't need a typed interface (for example, when a workflow is only used as a sub-agent inside another workflow), you can use `sequenceBuilder()` without a type argument, which returns an `UntypedAgent`. We will see this in the composing patterns section.


## Workflow Pattern 2: Loop

Not every problem is solved in a single pass. Sometimes you need iterative refinement: write a draft, score it, revise, score again, until the quality threshold is met. That is the loop workflow:

```java
public interface AdmiralAckbar {
    @Agent("Reviews an attack plan for traps and tactical weaknesses")
    @UserMessage("""
            You are Admiral Ackbar. Review this attack plan. If you find issues, \
            explain them briefly and say REJECTED. If the plan is solid, say APPROVED.

            Attack plan: {{AttackPlan}}""")
    String review(@K(AttackPlan.class) String plan);
}

public interface PlanReviser {
    @Agent("Revises the attack plan based on Admiral Ackbar's feedback")
    @UserMessage("""
            You are a Rebel Alliance battle strategist. Revise the attack plan \
            to address Admiral Ackbar's concerns.

            Current plan: {{AttackPlan}}

            Ackbar's feedback: {{ReviewFeedback}}""")
    String revise(@K(AttackPlan.class) String plan, @K(ReviewFeedback.class) String feedback);
}

AttackPlanReviewPipeline pipeline = AgenticServices
    .loopBuilder(AttackPlanReviewPipeline.class)
    .subAgents(ackbar, reviser)
    .maxIterations(3)
    .outputKey(AttackPlan.class)
    .build();
```

The loop runs `ackbar` (reviews the current attack plan, says APPROVED when no critical weaknesses remain, otherwise produces a list of concerns, ideally including at least one "It's a trap!") then `reviser` (addresses the concerns and updates the plan). After three iterations, they fly in anyway and trust the Force.

Three configuration points matter:

- **`maxIterations(n)`**: a hard safety cap. Without this, a loop that never satisfies its exit condition runs forever. Always set this.
- **`exitCondition(Predicate<AgenticScope>)`**: an optional condition that ends the loop early. It has access to the full scope, so you can base it on scores, flags, or any combination. The composing patterns section will show this in action.

The loop pattern is powerful for quality gates. A gatekeeper agent evaluates the output, a refinement agent improves it, and the loop continues until the bar is cleared or the maximum iterations are reached.


## Workflow Pattern 3: Parallel

When agents are independent and can work without each other's results, running them sequentially wastes time. The parallel workflow executes agents simultaneously:

```java
SpaceFleetStrategist spaceFleetStrategist = AgenticServices
    .agentBuilder(SpaceFleetStrategist.class)
    .chatModel(model)
    .outputKey("fleetDisposition")
    .build();

GroundAssaultAgent groundAssaultAgent = AgenticServices
    .agentBuilder(GroundAssaultAgent.class)
    .chatModel(model)
    .outputKey("ewokGroundStrategy")
    .build();

JediMissionPlanner jediMissionPlanner = AgenticServices
    .agentBuilder(JediMissionPlanner.class)
    .chatModel(model)
    .outputKey("lukeObjective")
    .build();

BattleOfEndorPipeline pipeline = AgenticServices
    .parallelBuilder(BattleOfEndorPipeline.class)
    .subAgents(spaceFleetStrategist, groundAssaultAgent, jediMissionPlanner)
    .executor(Executors.newFixedThreadPool(3))
    .output(scope -> assembleBattlePlan(
            (String) scope.readState("fleetDisposition"),
            (String) scope.readState("ewokGroundStrategy"),
            (String) scope.readState("lukeObjective")))
    .outputKey("battleOfEndor")
    .build();
```

Note that this example uses string-based output keys for brevity; typed keys work identically and are recommended for production code.

`spaceFleetStrategist`, `groundAssaultAgent`, and `jediMissionPlanner` run simultaneously on separate threads. The fleet engagement above Endor, the Ewok ground coordination, and Luke's solo mission into the Death Star are all planned at the same time. When all three finish, the `output()` function assembles the final battle order. If each agent takes 3 seconds, the total is 3 seconds, not 9.

You provide the thread pool via `executor()`, so you control the concurrency model. The `output()` function merges the parallel results into a single coherent output.

Because agents run on separate threads, treat the `AgenticScope` as the single point of coordination. Each agent should write to its own distinct output key. The scope handles concurrent writes safely.


## Workflow Pattern 4: Conditional

When different inputs require different processing paths, you need conditional branching. The conditional workflow routes execution based on the current state.

A notable LangChain4j feature at play here: agents can return Java enums directly. The framework maps the LLM's text response to the enum value, which makes enums natural for conditional routing predicates:

```java
public enum AlignmentType {
    LIGHT_SIDE, DARK_SIDE, NEUTRAL, UNKNOWN;
}

public interface AlignmentClassifier {
    @Agent(value = "Classifies a Star Wars character's Force alignment")
    @UserMessage("""
            You are a Force-sensitive oracle. Classify this character's alignment as
            exactly one of: LIGHT_SIDE, DARK_SIDE, or NEUTRAL.
            Respond with ONLY the alignment label, nothing else.

            Character: {{CharacterName}}""")
    AlignmentType classify(@V("CharacterName") String name);
}
```

The classifier writes its result to the scope. Then the conditional router dispatches to the right specialist:

```java
var conditionalRouter = AgenticServices.conditionalBuilder()
    .subAgents(scope -> scope.readState(Alignment.class) == AlignmentType.LIGHT_SIDE, jediCouncilAgent)
    .subAgents(scope -> scope.readState(Alignment.class) == AlignmentType.DARK_SIDE, sithLordAgent)
    .subAgents(scope -> scope.readState(Alignment.class) == AlignmentType.NEUTRAL, mandalorianAgent)
    .build();

MissionRouter router = AgenticServices
    .sequenceBuilder(MissionRouter.class)
    .subAgents(alignmentClassifier, conditionalRouter)
    .outputKey(MissionBriefing.class)
    .build();
```

Each sub-agent is paired with a predicate. The predicates are evaluated against the AgenticScope, and only the matching agent is invoked. The `.subAgents()` method in the conditional builder is overloaded: it accepts a predicate-agent pair, unlike the plain list version used in sequential and parallel builders.

The architecture here is worth examining: the conditional router is wrapped inside a sequence. The `AlignmentClassifier` agent reads the inbound request, writes the force alignment to the scope, and the conditional workflow routes to the right specialist. No LLM decides the routing. You do, with explicit predicates.


## Composing Patterns

The real power of these patterns emerges when you combine them. In LangChain4j, every workflow is itself an agent, which means it can be used as a sub-agent in another workflow. A loop inside a sequence. A parallel step inside a conditional. A conditional inside a loop.

Consider a Rebel Alliance assault planner: a sequence that first decodes intercepted Imperial transmissions, then loops through Jedi Council debate until the battle plan reaches approval, and finally broadcasts the attack order across the fleet.

```java
// Build the loop: council reviews, reviser improves, repeat.
// Exits when 4 masters approve or after 3 iterations.
UntypedAgent councilLoop = AgenticServices
    .loopBuilder()
    .subAgents(jediCouncilCritic, planReviser)
    .outputKey(BattlePlan.class)
    .exitCondition(scope -> scope.readState(MasterApprovals.class) >= 4)
    .maxIterations(3)
    .build();

// Build the full sequence: decode -> loop(critique <-> revise) -> broadcast
RebellionHQ hq = AgenticServices
    .sequenceBuilder(RebellionHQ.class)
    .subAgents(imperialDecoder, councilLoop, holonetBroadcaster)
    .outputKey(CallToArms.class)
    .build();

String callToArms = hq.plan(interceptedData);
```

Notice `loopBuilder()` without a type argument. It returns an `UntypedAgent` because the loop is only used as a sub-agent inside the outer sequence, not invoked directly.

`imperialDecoder` cracks the intercepted transmissions and writes the intel to the scope. `councilLoop` bounces the strategy between `jediCouncilCritic` (each Jedi Master votes APPROVE or REJECT with a brief reason, then tallies the total approvals) and `planReviser` (addresses their concerns) until four Jedi Masters sign off, or three rounds expire.

Here the `exitCondition` is at work: `scope.readState(MasterApprovals.class) >= 4` reads the typed key (which defaults to `0` via its `defaultValue()`) and checks whether enough masters have approved. `holonetBroadcaster` then sends the call to arms across the galaxy.

This composability is what separates a framework from a collection of utilities. You build complex behaviors from simple, well-understood pieces.


## Error Handling as a First-Class Concern

In any system that involves network calls, LLM responses, and multi-step processing, things will fail. LangChain4j treats error handling as a core feature rather than an afterthought.

Error handlers receive the full context (the exception, the agent name, and the current AgenticScope) and return an `ErrorRecoveryResult` that dictates the recovery strategy:

```java
SithPipeline pipeline = AgenticServices
    .sequenceBuilder(SithPipeline.class)
    .subAgents(jediProfiler, sithNamer, sithAnnouncer)
    .outputKey(Announcement.class)
    .errorHandler(errorContext -> {
        if (errorContext.agentName().equals("darken")) {
            // The Sith namer failed, retry with a known-good fallback name
            errorContext.agenticScope().writeState("JediName", "Dave");
            return ErrorRecoveryResult.retry();
        }
        return ErrorRecoveryResult.throwException();
    })
    .build();
```

Three recovery strategies are available:

1. **`retry()`**: re-execute the failed agent. The handler can modify scope state before retrying, effectively correcting the input that caused the failure.
2. **`result(value)`**: provide a fallback value as the agent's result and continue the workflow as if the agent had succeeded. Useful for non-critical steps with a sensible default.
3. **`throwException()`**: propagate the exception. For truly unrecoverable failures.

The handler can inspect the scope, check which agent failed via `errorContext.agentName()` (which defaults to the method name, in this case `"darken"`), and make an informed decision. This is error handling as workflow logic, not as exception plumbing.


## Goal-Oriented Agents

> **Note:** The goal-oriented planner (`GoalOrientedPlanner`) is not yet released at the time of writing. It lives in the `langchain4j-agentic-patterns` module, currently available as a `1.12.0-beta20-SNAPSHOT`. It is expected to ship with the next stable LangChain4j release. The API shown here may evolve before then.

Workflows give you full control but require you to hardcode the sequence. The goal-oriented pattern removes that constraint: you declare what each agent produces and what it needs, and a deterministic planner calculates the execution path automatically.

Inspired by Goal-Oriented Action Planning (GOAP), this approach uses algorithmic graph traversal rather than either a hardcoded sequence or LLM reasoning. Each agent declares its input keys via `@K` annotations and its output key. The planner builds a dependency graph, examines the current scope state, and calculates the shortest path to the goal.

Consider a lightsaber forging pipeline where three agents have explicit dependencies:

```java
public static class JediName implements TypedKey<String> { }
public static class KyberCrystal implements TypedKey<String> { }
public static class HiltDesign implements TypedKey<String> { }
public static class Lightsaber implements TypedKey<String> { }

// Needs "jediName" in scope, produces "kyberCrystal"
public interface CrystalForagerAgent {
    @Agent("Forages a kyber crystal on Ilum based on the Jedi's Force affinity")
    @UserMessage("""
            You are a kyber crystal guide on Ilum. Based on this Jedi's name \
            and identity, describe the kyber crystal that calls to them through the Force.

            Jedi: {{jediName}}""")
    String forage(@K(JediName.class) String jediName);
}

// Needs "kyberCrystal" in scope, produces "hiltDesign"
public interface HiltCrafterAgent {
    @Agent("Crafts a lightsaber hilt design based on the kyber crystal properties")
    @UserMessage("""
            You are a lightsaber hilt artisan. Design a hilt that complements \
            this kyber crystal.

            Kyber crystal: {{kyberCrystal}}""")
    String craft(@K(KyberCrystal.class) String kyberCrystal);
}

// Needs "hiltDesign" in scope, produces "lightsaber"
public interface BladeCalibrationAgent {
    @Agent("Calibrates and activates the completed lightsaber")
    @UserMessage("""
            You are a Jedi weapon master performing the final lightsaber calibration. \
            Describe the blade's characteristics when ignited.

            Hilt design: {{hiltDesign}}""")
    String calibrate(@K(HiltDesign.class) String hiltDesign);
}

LightsaberForge forge = AgenticServices
    .plannerBuilder(LightsaberForge.class)
    .subAgents(crystalForager, hiltCrafter, bladeCalibration)
    .outputKey(Lightsaber.class)
    .planner(GoalOrientedPlanner::new)
    .build();
```

At runtime, the scope contains `"jediName"` (say, `"Cal Kestis"`). The goal is `"lightsaber"`. The planner resolves: `CrystalForagerAgent` (jediName → kyberCrystal) then `HiltCrafterAgent` (kyberCrystal → hiltDesign) then `BladeCalibrationAgent` (hiltDesign → lightsaber). No hardcoded sequence. Change the goal to `"hiltDesign"` and you get the blueprint without the blade assembly:

```java
HiltOnlyForge hiltOnly = AgenticServices
    .plannerBuilder(HiltOnlyForge.class)
    .subAgents(crystalForager, hiltCrafter, bladeCalibration)
    .outputKey(HiltDesign.class)
    .planner(GoalOrientedPlanner::new)
    .build();
```

The planner finds the path; you declare the goal.

The key insight: the planning is algorithmic, not LLM-driven. The agents may use LLMs, but orchestration is a graph traversal problem. This gives you more flexibility than a hardcoded workflow while maintaining full auditability.

### Custom Orchestration: The Planner Interface

All the patterns described above are implementations of a single abstraction: the `Planner` interface.

```java
public interface Planner {
    default void init(InitPlanningContext context) { }
    default Action firstAction(PlanningContext context) {
        return nextAction(context);
    }
    Action nextAction(PlanningContext context);
}
```

You are not limited to the built-in patterns: implement `Planner` for custom orchestration strategies.


## Going Agentic: The Supervisor Pattern

Everything described so far has been deterministic. Whether a fixed sequence, a loop, a conditional branch, or a goal-oriented graph, the execution plan is controlled by code or by an algorithm, never by an LLM. But what happens when the plan itself is not predictable?

The supervisor pattern hands control to an LLM-based planner. Rather than following a fixed sequence, the supervisor reads the available agents and their descriptions, reasons about the incoming request, and decides which agents to invoke and in what order:

```java
DarthVaderCommand vader = AgenticServices
    .supervisorBuilder(DarthVaderCommand.class)
    .chatModel(plannerModel)
    .subAgents(stormtrooperRegiment, bountyHunter, starDestroyer, deathStar)
    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
    .build();
```

Note that the supervisor's `chatModel` is specifically for the planning and routing decisions. Each sub-agent can use its own (potentially different) model.

Vader receives the situation report and reasons about what to deploy. For a fugitive Jedi on Coruscant, the supervisor might choose `bountyHunter`. For a Rebel base on Hoth, it might combine `stormtrooperRegiment` and `starDestroyer`. The sequence is not coded; it is reasoned at runtime.

Response strategies control how the final answer is assembled: **LAST** returns the last agent's output, **SUMMARY** generates a combined report, and **SCORED** uses a scoring agent to select the best response.

The supervisor is the most flexible pattern, but it comes with costs: higher latency, higher token consumption, and harder auditability. Use it when the flexibility genuinely justifies these trade-offs.


## Mixing AI and Non-AI Agents

Not every step in a workflow requires an LLM. Some steps are pure business logic: data validation, database lookups, formatting, aggregation, routing based on rules. Forcing these through an LLM is wasteful in both cost and latency.

LangChain4j supports non-AI agents via `AgenticServices.agentAction()`, which wraps a `Consumer<AgenticScope>` into a first-class participant in the same orchestration framework, reading and writing the same scope but executing plain Java code instead of an LLM call:

```java
// A non-AI agent that queries the fleet database
var fleetLookup = AgenticServices.agentAction(scope -> {
    String system = scope.readState(TargetSystem.class);
    List<String> ships;
    if (system != null && system.toLowerCase().contains("hoth")) {
        ships = List.of("Millennium Falcon", "Rogue Squadron (12 X-Wings)", "GR-75 Transports");
    } else if (system != null && system.toLowerCase().contains("endor")) {
        ships = List.of("Home One (MC80)", "A-Wing Interceptors", "B-Wing Assault Fighters");
    } else {
        ships = List.of("X-Wing Squadron", "Millennium Falcon", "Tantive IV");
    }
    scope.writeState(AvailableShips.class, ships.toString());
});
```

A hybrid workflow chains AI and non-AI agents in the same pipeline. An AI agent analyzes a distress signal, a non-AI `agentAction` parses the structured output, another `agentAction` queries the fleet database, an AI agent drafts the rescue plan, and a final `agentAction` formats and broadcasts the orders:

```java
RescuePipeline pipeline = AgenticServices
    .sequenceBuilder(RescuePipeline.class)
    .subAgents(distressAnalyzer, analysisParser, fleetLookup, rescueCoordinator, broadcastAction)
    .outputKey("broadcast")
    .build();
```

This mixing matters because real applications are not pure AI pipelines. Treating both AI and non-AI steps as first-class agents in the same framework keeps your architecture coherent.


## Human-in-the-Loop

Some decisions should not be automated. LangChain4j treats human-in-the-loop as a special kind of non-AI agent. The built-in `HumanInTheLoop` class wraps a function that receives the `AgenticScope` and returns the human's response.

Consider a Death Star firing protocol. A `TargetAnalyzer` agent examines the proposed planet, then a human commander must confirm before the superlaser fires:

```java
public static class ProposedTarget implements TypedKey<String> { }
public static class TargetAnalysis implements TypedKey<String> { }
public static class FiringResult implements TypedKey<String> { }

public interface TargetAnalyzer {
    @Agent("Analyzes a proposed planetary target for the Death Star's superlaser")
    @UserMessage("""
            You are an Imperial Intelligence officer aboard the Death Star. \
            Analyze this proposed target planet. Provide a brief tactical assessment. \
            Keep it to 3-4 sentences.

            Proposed target: {{ProposedTarget}}""")
    String analyze(@K(ProposedTarget.class) String target);
}
```

The `HumanInTheLoop` agent sits between the analyzer and the firing agent. Note that `outputKey` currently accepts a string key rather than a `TypedKey`:

```java
HumanInTheLoop commanderApproval = AgenticServices.humanInTheLoopBuilder()
    .description("An agent that asks for validation")
    .outputKey("ConfirmedTarget")
    .responseProvider(scope -> {
        String target = scope.readState(ProposedTarget.class);
        String analysis = scope.readState(TargetAnalysis.class);
        String message = String.format("""
                === COMMANDER APPROVAL REQUIRED ===

                Target analysis: %s

                Commander, the Death Star is in position above %s.
                Confirm target to proceed with superlaser firing? (yes/no)
                """, analysis, target);
        return IO.readln(message);
    })
    .build();
```

Because it is just another agent, it plugs into any workflow. The typed pipeline interface and sequence wire everything together:

```java
public interface DeathStarProtocol {
    @Agent("Death Star firing protocol")
    String execute(@V("ProposedTarget") String target);
}

DeathStarProtocol protocol = AgenticServices
    .sequenceBuilder(DeathStarProtocol.class)
    .subAgents(targetAnalyzer, commanderApproval, superlaserAgent)
    .outputKey(FiringResult.class)
    .build();

String result = protocol.execute("Alderaan");
```

The sequence pauses at `commanderApproval`, prompts the console via `IO.readln()`, and waits. Once the commander responds, the value is written to `"ConfirmedTarget"` and the next agent picks it up.

The `responseProvider` is deliberately generic: it takes the scope and returns an object. In production, replace the console I/O with a REST endpoint, a UI callback, or a messaging system.


## Observability

When you move from a single LLM call to a multi-agent workflow, observability becomes essential. LangChain4j provides the `AgentListener` interface with hooks for `beforeAgentInvocation`, `afterAgentInvocation`, and `onAgentInvocationError`. The built-in `AgentMonitor` records execution trees in memory, and `HtmlReportGenerator` produces visual HTML reports showing the flow of agents, their inputs, outputs, and timing. When a multi-agent workflow produces an unexpected result, these tools let you trace exactly which agent introduced the problem.


## When to Use What

The hardest question is not "how do I build this?" but "which pattern should I use?" Here is a decision framework.

| Dimension | Workflow | Goal-Oriented | Supervisor |
|-----------|----------|---------------|------------|
| **Control** | High (deterministic) | Medium | Low (LLM decides) |
| **Flexibility** | Low | Medium-High | High |
| **Cost/Latency** | Low | Medium | High |
| **Auditability** | Excellent | Good | Difficult |
| **Best for** | Stable, predictable pipelines | Moderate variability | Complex adaptive systems |

**Start with workflows.** If the process is well-defined and you need auditability, a workflow gives you full control at the lowest cost. **Graduate to goal-oriented planning** when the sequence varies based on what is already known. **Use a supervisor** when the sequence is genuinely unpredictable. **Mix approaches**: a conditional workflow that routes to a supervisor for edge cases and a fixed sequence for the common path is often the right architecture.

The key principle: do not over-index on autonomy. A workflow that solves 90% of cases deterministically, with a supervisor handling the remaining 10%, is cheaper, faster, and more debuggable than a supervisor handling everything.


## Conclusion

The transition from AI calls to agentic systems is not about making your prompts smarter. It is about making your architecture explicit.

LangChain4j's central insight is the separation of concerns: agents own their LLM calls, the AgenticScope owns the shared state, and the planner owns the execution order. When those responsibilities are clear, a system with a dozen agents becomes as readable and debuggable as any other Java codebase.

The spectrum from hardcoded sequences to goal-oriented graphs to LLM-driven supervisors is not a ladder you must climb. It is a menu. Most real systems live mostly at the deterministic end and borrow from the other options only where variability genuinely demands it.

Start with workflows. Add autonomy only where the problem requires it. When something goes wrong, follow the `AgentMonitor` trail. And remember: the pattern you do not adopt is the one you do not have to debug.
