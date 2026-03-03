---
title: "From AI to Agent with Langchain4j"
description: "Discover how modern Java has evolved into a powerful scripting language, eliminating boilerplate and enabling instant execution for automation tasks"
tags: [java, langchain4j, ai]
author: Loïc
---
You have built AI features into your Java application. Your model is wrapped in a service, RAG is feeding it context, tools are wired, and calls are flowing. It works. Then requirements evolve. A single prompt-and-response is no longer enough. You need steps that follow each other, branches based on decisions, retries when things fail, and sometimes multiple actions happening at the same time. The question shifts from "how do I call an LLM?" to "how do I orchestrate multiple LLM-driven tasks into a coherent system?"

This article walks through that transition using LangChain4j's agentic module. We will start from simple agents, move through five workflow patterns, and arrive at fully agentic systems, covering shared state, error handling, non-AI agents, human-in-the-loop, and the critical question of when to use which approach.

The intended audience is Java developers who already have basic AI integration experience. If you know how to call a model and wire a tool, you are ready for what comes next.


## The Building Block: What Is an Agent?

Before we orchestrate anything, we need a common unit of work. In LangChain4j, an agent is a Java interface with a single method that performs a task, typically backed by an LLM. You define it declaratively:

```java
public interface SithNameGenerator {
    @UserMessage("You are a Sith naming council. Transform this boring name into something " +
                 "properly villainous: {{jediName}}")
    @Agent("Transforms a Jedi name into a suitably menacing Sith identity")
    String darken(@V("jediName") String jediName);
}
```

The `@Agent` annotation marks this as an agent with a description. The `@UserMessage` provides the prompt template. This looks deceptively similar to a plain LangChain4j AI service, and that is intentional: agents build on the same foundation.

To instantiate an agent, you use the builder:

```java
SithNameGenerator sithNamer = AgenticServices
    .agentBuilder(SithNameGenerator.class)
    .chatModel(myChatModel)
    .outputKey("sithName")
    .build();
```

Two details matter here. First, every agent has a **name** that uniquely identifies it in a system (derived from the method name or set explicitly). Second, every agent has an **output key**, a named slot in shared state where it writes its result. That output key is how agents communicate with each other without direct coupling.

This is the fundamental contract: an agent reads inputs, does its work, and writes its result to a shared location. Everything else in this article builds on that idea.


## Shared State with AgenticScope

If agents communicate through named keys rather than direct method calls, there needs to be a shared container holding those keys. That container is the `AgenticScope`.

The AgenticScope implements the blackboard pattern: a shared state object that all agents in a system can read from and write to. When the root agent is invoked, the scope is created automatically. Each subsequent agent reads what it needs, does its work, and writes its output back.

The core operations are straightforward:

- `writeState(key, value)`: store a result
- `readState(key)`: retrieve a value
- `readState(key, defaultValue)`: retrieve with a fallback
- `hasState(key)`: check if a key exists

This pattern enables loose coupling. Agent A does not call Agent B. Agent A writes `"imperialPlans"` to the scope. Agent B reads `"imperialPlans"` from the scope. Neither knows about the other. The orchestration layer decides the order.

The scope also maintains an invocation history: `invocations()` returns the full list of agent executions and their responses, giving you a built-in audit trail.

For production use, LangChain4j offers typed keys to prevent spelling errors and type mismatches:

```java
public static class InterceptedTransmission implements TypedKey<String> { }

public interface ThreatClassifier {
    @Agent(typedOutputKey = ThreatLevel.class)
    ThreatLevel classify(@K(InterceptedTransmission.class) String transmission);
}
```

Typed keys enforce constraints at compile time. When your system grows to dozens of agents processing intercepted Imperial transmissions, accidentally reading a `ThreatLevel` as a `String` is not optional: it is survival.


## Workflow Pattern 1: Sequential

The simplest orchestration pattern is the sequential workflow. Agents execute one after another in a defined order, each reading from and writing to the AgenticScope:

```java
UntypedAgent jediTrainingPipeline = AgenticServices
    .sequenceBuilder()
    .subAgents(aptitudeTester, masterAssigner, lightsaberGuide)
    .outputKey("jediKnight")
    .build();
```

Here, `aptitudeTester` measures midi-chlorian count and Force aptitude, flagging applicants who show "tendency toward attachment and anger" for special review. Then `masterAssigner` reads the profile and pairs the Padawan with a compatible Master — rejecting the pairing if both are known to be emotionally unstable. Finally, `lightsaberGuide` produces the weapon construction specifications, including recommended crystal color.

This is the pattern you reach for when tasks have a natural ordering and each step depends on the previous one. Think of content pipelines, multi-stage data processing, or any chain-of-thought decomposition where you want explicit control over each step.

LangChain4j also provides a declarative equivalent using annotations:

```java
@SequenceAgent(
    outputKey = "missionBriefing",
    subAgents = { IntelAnalystAgent.class, SquadAssemblerAgent.class })
MissionBriefing planAttack(String imperialInstallation);
```

The annotation-driven approach is cleaner for straightforward cases and integrates well with CDI frameworks like Quarkus.


## Workflow Pattern 2: Loop

Not every problem is solved in a single pass. Sometimes you need iterative refinement: write a draft, score it, revise, score again, until the quality threshold is met. That is the loop workflow:

```java
UntypedAgent attackPlanReview = AgenticServices
    .loopBuilder()
    .subAgents(generalAckbar, planReviser)
    .maxIterations(5)
    .exitCondition(scope -> scope.readState("planApproved", false))
    .build();
```

The loop runs `generalAckbar` (reviews the current attack plan, sets `planApproved = true` when no critical weaknesses remain, otherwise produces a list of concerns — ideally including at least one "It's a trap!") then `planReviser` (addresses the concerns and updates the plan). After each round, the exit condition is evaluated. If `planApproved` flips to `true`, the fleet launches. After five attempts, they fly in anyway and trust the Force.

Three configuration points matter:

- **`maxIterations(n)`**: a hard safety cap. Without this, a loop that never satisfies its exit condition runs forever. Always set this.
- **`exitCondition(Predicate<AgenticScope>)`**: the condition that ends the loop. It has access to the full scope, so you can base it on scores, flags, booleans, or any combination.
- **`testExitAtLoopEnd(boolean)`**: controls whether the condition is checked at the beginning or end of each iteration.

The loop pattern is powerful for quality gates. A gatekeeper agent evaluates the output, a refinement agent improves it, and the loop continues until the bar is cleared.


## Workflow Pattern 3: Parallel

When agents are independent and can work without each other's results, running them sequentially wastes time. The parallel workflow executes agents simultaneously:

```java
BattleOfEndorAgent battlePlanner = AgenticServices
    .parallelBuilder(BattleOfEndorAgent.class)
    .subAgents(spaceFleetStrategist, groundAssaultAgent, jediMissionPlanner)
    .executor(Executors.newFixedThreadPool(3))
    .outputKey("battleOfEndor")
    .output(scope -> assembleBattlePlan(
        scope.readState("fleetDisposition"),
        scope.readState("ewokGroundStrategy"),
        scope.readState("lukeObjective")))
    .build();
```

`spaceFleetStrategist`, `groundAssaultAgent`, and `jediMissionPlanner` run simultaneously on separate threads. The fleet engagement above Endor, the Ewok ground coordination, and Luke's solo mission into the Death Star are all planned at the same time. When all three finish, the output function assembles the final battle order. If each agent takes 3 seconds, the total is 3 seconds, not 9. The Emperor never saw it coming.

You provide the thread pool via `executor()`, so you control the concurrency model. The `output()` function is where you merge the parallel results into a single coherent output. LangChain4j also offers a parallel mapper variant where the same agent processes multiple items concurrently, useful for batch operations like simultaneously assessing every Rebel pilot's combat readiness.


## Workflow Pattern 4: Conditional

When different inputs require different processing paths, you need conditional branching. The conditional workflow routes execution based on the current state:

```java
UntypedAgent missionBriefing = AgenticServices
    .conditionalBuilder()
    .subAgent(scope -> scope.readState("alignment") == LIGHT_SIDE,  jediCouncilAgent)
    .subAgent(scope -> scope.readState("alignment") == DARK_SIDE,   sithLordAgent)
    .subAgent(scope -> scope.readState("alignment") == NEUTRAL,     mandalorianAgent)
    .build();
```

Each sub-agent is paired with a predicate. The predicates are evaluated against the AgenticScope, and only the matching agent is invoked. The conditions can examine the full accumulated state of the workflow, making this far more expressive than a simple switch on a single variable.

Conditional workflows are the routing layer. An `AlignmentClassifier` agent reads the inbound request, writes the force alignment to the scope, and the conditional workflow routes to the right specialist. The Mandalorian takes jobs the others refuse. No LLM decides the routing: you do, with explicit predicates.


## Composing Patterns

The real power of these patterns emerges when you combine them. Every workflow is itself an agent, which means it can be a sub-agent in another workflow. A loop inside a sequence. A parallel step inside a conditional. A conditional inside a loop.

Consider a Rebel Alliance assault planner: a sequence that first decodes intercepted Imperial transmissions, then loops through Rebel Council debate until the battle plan reaches majority approval, and finally broadcasts the attack order across the fleet.

```java
RebelCouncilLoop councilLoop = AgenticServices
    .loopBuilder(RebelCouncilLoop.class)
    .subAgents(jediCouncilCritic, planReviser)
    .outputKey("battlePlan")
    .exitCondition(scope -> scope.readState("masterApprovals", 0) >= 4)
    .maxIterations(3)
    .build();

// The loop becomes one step inside a larger sequence
UntypedAgent rebellionHQ = AgenticServices
    .sequenceBuilder()
    .subAgents(imperialDecoder, councilLoop, holonetBroadcaster)
    .outputKey("callToArms")
    .build();
```

`imperialDecoder` cracks the intercepted transmissions and writes the intel to the scope. `councilLoop` bounces the strategy between `jediCouncilCritic` (tallies approvals, flags disturbances in the Force) and `planReviser` (addresses them) until four Jedi Masters sign off — or three rounds expire and they fly in anyway. `holonetBroadcaster` then sends the call to arms across the galaxy. This composability is what separates a framework from a collection of utilities. You build complex behaviors from simple, well-understood pieces.


## Error Handling as a First-Class Concern

In any system that involves network calls, LLM responses, and multi-step processing, things will fail. LangChain4j treats error handling as a core feature rather than an afterthought.

Error handlers receive the full context (the exception, the agent name, and the current AgenticScope) and return an `ErrorRecoveryResult` that dictates the recovery strategy:

```java
.errorHandler(errorContext -> {
    if (errorContext.agentName().equals("darken")) {
        // The council could not agree — retry with something universally menacing
        errorContext.agenticScope().writeState("jediName", "Dave");
        return ErrorRecoveryResult.retry();
    }
    return ErrorRecoveryResult.throwException();
})
```

Four recovery strategies are available:

1. **`retry()`**: re-execute the failed agent. The handler can modify scope state before retrying, effectively correcting the input that caused the failure.
2. **`skip()`**: move to the next agent. For non-critical steps where the workflow can continue without this agent's output.
3. **`complete(value)`**: provide a fallback value as the agent's result. The workflow continues as if the agent succeeded.
4. **`throwException()`**: propagate the exception. For truly unrecoverable failures.

The handler can inspect the scope, check which agent failed, and make an informed decision. This is error handling as workflow logic, not as exception plumbing.


## Going Agentic: The Supervisor Pattern

Everything described so far has been deterministic. You define the sequence, the branches, the loops. You control the flow. But what happens when the flow itself is not predictable?

The supervisor pattern hands control to an LLM-based planner. Instead of a fixed sequence, the supervisor decides which agents to invoke and in what order:

```java
SupervisorAgent darthVader = AgenticServices
    .supervisorBuilder()
    .chatModel(plannerModel)
    .subAgents(stormtrooperRegiment, bountyHunterAgent, starDestroyerAgent, deathStarAgent)
    .responseStrategy(SupervisorResponseStrategy.SUMMARY)
    .build();
```

Vader receives the situation report, reads the available assets and their descriptions, and decides what to deploy. For a small Rebel cell on Tatooine: `bountyHunterAgent`. For a planetary uprising: `stormtrooperRegiment`. For "the Rebels have the Death Star plans and a farm boy with a lightsaber": all four — with `deathStarAgent` reserved as the Emperor's preferred solution to disagreements. The sequence is not coded: it is reasoned.

Response strategies control how the final answer is assembled:

- **LAST**: returns whatever the last agent produced.
- **SUMMARY**: generates a summary of all operations performed.
- **SCORED**: uses a scoring agent to select the best response.

Context strategies control what information flows between agents: full chat memory, summarized context, or a custom approach you define.

The supervisor is the most flexible pattern, but it comes with costs: higher latency, higher token consumption, and harder auditability. Use it when the flexibility genuinely justifies these trade-offs.


## Goal-Oriented Agents

Between fully deterministic workflows and fully autonomous supervisors lies the goal-oriented pattern. Inspired by Goal-Oriented Action Planning (GOAP), this approach uses deterministic graph-based planning rather than LLM-based planning.

Each agent declares what it needs (preconditions) and what it produces (postconditions) through its required inputs and output keys. The planner builds a dependency graph, examines the current scope state, and calculates the shortest path to the goal. The declarative agent definitions, with their `@K` input annotations and `outputKey` declarations, serve as the specification the planner uses to construct the execution plan.

Consider a lightsaber forging pipeline where three agents have explicit dependencies:

```java
// Needs "jediName" in scope, produces "kyberCrystal"
public interface CrystalForagerAgent {
    @Agent(description = "Meditates in the Ilum caves to find the attuned kyber crystal")
    @UserMessage("Find the kyber crystal resonating with this Jedi's Force signature: {{jediName}}")
    String forage(@K("jediName") String jediName);
}

// Needs "kyberCrystal" in scope, produces "hiltDesign"
public interface HiltCrafterAgent {
    @Agent(description = "Designs a hilt that channels the crystal without catastrophic feedback")
    @UserMessage("Design a hilt for this kyber crystal: {{kyberCrystal}}")
    String craft(@K("kyberCrystal") String kyberCrystal);
}

// Needs "hiltDesign" in scope, produces "lightsaber"
public interface BladeCalibrationAgent {
    @Agent(description = "Sets blade length, color, and decides whether to go double-bladed")
    @UserMessage("Calibrate the final lightsaber from this hilt design: {{hiltDesign}}")
    String calibrate(@K("hiltDesign") String hiltDesign);
}

UntypedAgent lightsaberForge = AgenticServices
    .goalOrientedBuilder()
    .subAgents(crystalForager, hiltCrafter, bladeCalibration)
    .outputKey("lightsaber")  // the Force wills it
    .build();
```

At runtime, the scope contains `"jediName"` (say, `"Luke Skywalker"`). The goal is `"lightsaber"`. The planner resolves: `CrystalForagerAgent` (jediName → kyberCrystal) then `HiltCrafterAgent` (kyberCrystal → hiltDesign) then `BladeCalibrationAgent` (hiltDesign → lightsaber). No hardcoded sequence. Change the goal to `"hiltDesign"` and you get the blueprint without the blade assembly — useful if you just need the schematics. The planner finds the path; you trust the Force.

The key insight: the planning is algorithmic, not LLM-driven. The agents may use LLMs, but orchestration is a graph traversal problem. This gives you more flexibility than a hardcoded workflow while maintaining full auditability. Note that this pattern does not currently support built-in loops; compose with a loop workflow if you need iterative refinement.


## Custom Orchestration: The Planner Interface

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

The planner separates the planning layer (which agent comes next?) from the execution layer (actually running the agent). You are not limited to the built-in patterns: implement the `Planner` interface for custom orchestration strategies.

LangChain4j also provides a **peer-to-peer planner** where agents activate when their dependencies in the scope are satisfied, with no central coordinator. The system runs until it reaches a stable state, useful for event-driven architectures.


## Mixing AI and Non-AI Agents

Not every step in a workflow requires an LLM. Some steps are pure business logic: data validation, database lookups, formatting, aggregation, routing based on rules. Forcing these through an LLM is wasteful in both cost and latency.

LangChain4j supports `NonAiAgentInstance`: agents that participate in the same orchestration framework, read and write the same AgenticScope, but execute plain Java code instead of LLM calls.

A hybrid workflow might have an LLM agent analyze a distress signal, a non-AI agent query the Rebel fleet database for nearby ships, another LLM agent draft the rescue coordinates, and a non-AI agent broadcast them on the holonet. This mixing matters because real applications are not pure AI pipelines. Treating both AI and non-AI steps as first-class agents in the same framework keeps your architecture coherent.


## Human-in-the-Loop

Some decisions should not be automated. Firing the Death Star's superlaser at a populated planet, granting a Padawan the rank of Jedi Knight, or deciding whether to trust a suspiciously helpful protocol droid: these require human judgment. Human-in-the-loop allows agents to pause execution and request human input before continuing.

The pattern works like this: a supervisor or conditional workflow includes a human validation step. The agent writes its proposed action to the AgenticScope. Execution pauses. A human reviews the proposal and approves or rejects it. Execution resumes based on the decision. The community is actively developing more sophisticated versions with non-blocking execution and state persistence for resumability across sessions.


## Observability

When you move from a single LLM call to a multi-agent workflow, observability becomes essential. LangChain4j provides the `AgentListener` interface with hooks for `beforeAgentInvocation`, `afterAgentInvocation`, and `onAgentInvocationError`. The built-in `AgentMonitor` records execution trees in memory, and `HtmlReportGenerator` produces visual HTML reports showing the flow of agents, their inputs, outputs, and timing. When a multi-agent workflow produces an unexpected result, these tools let you trace which agent introduced the problem.


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

The transition from AI calls to agentic systems is not about making your prompts smarter. It is about structuring the logic around those prompts so that multi-step processes are explicit, composable, and maintainable.

LangChain4j's agentic module gives you a spectrum: deterministic workflows at one end, goal-oriented agents in the middle, and LLM-driven supervisors at the other. The shared state model keeps agents decoupled. Error handling offers context-aware recovery. Non-AI agents let you mix reasoning with traditional logic. Human-in-the-loop ensures automation does not mean abdication. And observability makes the whole system debuggable.

Start with workflows. Compose patterns. Add autonomy only when it pays for itself. That is the path from AI to agentic.
