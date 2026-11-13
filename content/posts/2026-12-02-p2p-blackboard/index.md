---
title: "When the State Is the Boss: Peer-to-Peer and Blackboard Agents with LangChain4j"
description: "Two LangChain4j agentic patterns where nobody is in charge: peer-to-peer agents that wake up when the data they listen to changes, and a blackboard where specialists contribute one at a time. Real code, the traps I fell into, and when to use them."
tags: [java, langchain4j, ai]
author: Loïc
draft: true
---
<!-- TODO(Loïc): add a cover image (image: ...) -->

At Devoxx Belgium this year, I had the Monday 9:30 slot for a three-hour deep dive on LangChain4j agents. Monday morning is not my most efficient moment of the week, so I brought the next best thing to my puppy: a team of rescue puppies on the screen, the Pawer Rangers, in every demo.

Somewhere in the middle of those three hours, I showed a pattern that surprised me the first time I read about it. No agent was in charge: there was no sequence, no supervisor and no graph computed upfront. The agents just watched the shared state, and whenever something they cared about showed up, they jumped in.

This is the first of a short series of articles on the patterns I showed in that talk, the ones that didn't fit in my [first article on LangChain4j agentic workflows](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/). Here we look at two of them, peer-to-peer and blackboard. They look very similar, and the difference between them is a single question: when several agents can act at the same time, who gets to talk?

## Who Decides What Runs Next?

Every agentic system has to answer one question over and over again: "Which agent do I call next?" In LangChain4j, the component that answers it is the `Planner`. Most of the time you don't see it, because the builders create one for you. But every pattern is a different answer to that question:

- In a workflow (sequence, loop, parallel, conditional), *you* decide. The order is written in your code.
- In the goal-oriented pattern, a *graph algorithm* decides. It computes the shortest path once, then follows it.
- In the supervisor pattern, an *LLM* decides, at every step.

If those don't ring a bell, the [previous article](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/) covers all of them, along with the `AgenticScope` (the shared state every agent reads from and writes to) and typed keys.

Peer-to-peer and blackboard give a fourth answer: *the state* decides. Each agent declares what it needs as inputs. When those inputs are in the scope, the agent can run. When it runs, it writes its output to the scope, and that output might be exactly what another agent was waiting for. Nobody writes the order. It falls out of who reads what.

Both patterns live in a separate module, so you need two dependencies. The workflows only need the first one, so it's an easy one to forget:

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

## Peer-to-Peer: The Brainstorming Room

Think about what happens when you have a problem at work and you put a few people in a room. Nobody runs the meeting. Somebody says, "Hey, I noticed this." Somebody else goes, "Oh, that gives me an idea," and builds on it. Then the first person hears that and has a new idea of their own. Ideas bounce around until someone says, "Found it!", or until everybody runs out of things to say.

That's the peer-to-peer pattern. Every agent is a peer. Each one reacts whenever a value it listens to changes, and the conversation goes wherever the data takes it.

### The Corn Maze

In the demo, the Mayor's goat has wandered into a giant corn maze. Three puppies go in, and nobody is in charge:

- Sniff the beagle reads the scent and says where it leads.
- Zoom the greyhound runs the paths Sniff points to.
- Dig the dachshund checks under the hedges where the scent goes.

Each puppy is a plain LangChain4j agent. What matters is what it reads (`@K`) and what it writes (its output key). Here is Zoom:

```java
public interface ZoomInTheMaze {
    @Agent(description = "Zoom the Greyhound: runs the paths Sniff's scent points along")
    @UserMessage("""
            You are Zoom, in a giant corn maze with Sniff and Dig, looking for the Mayor's goat.
            Nobody is in charge: you only run where Sniff's nose points.

            - If Sniff says the scent leads to the middle of the maze: you run there and see
              the goat. Start with "FOUND:" and say where she is.
            - Otherwise: run the path Sniff names. It is clear, no goat, but you see fresh hoof
              prints turning north, towards the middle. Say exactly that.

            The mission: {{Mission}}
            Sniff says: {{Scent}}""")
    String run(@K(Mission.class) String mission, @K(Keys.Scent.class) String scent);
}
```

Zoom listens to `Scent`. Dig listens to `Scent` too. Sniff listens to the other two: Zoom's `Clearing` and Dig's `Burrows`. Each puppy has its own key and listens to the *others'* keys, never its own. That's all the coordination there is. Who listens to what *is* the wiring.

Now let's put them in the maze:

```java
var sniff = AgenticServices.agentBuilder(SniffInTheMaze.class)
        .chatModel(model).name("Sniff").outputKey(Scent.class).build();
var zoom = AgenticServices.agentBuilder(ZoomInTheMaze.class)
        .chatModel(model).name("Zoom").outputKey(Clearing.class).build();
var dig = AgenticServices.agentBuilder(DigInTheMaze.class)
        .chatModel(model).name("Dig").outputKey(Burrows.class).build();

MazeSearch search = AgenticServices.plannerBuilder(MazeSearch.class)
        .subAgents(zoom, dig, sniff)
        .planner(() -> new P2PPlanner(20, scope -> goatFound(scope)))
        .outputKey(Scent.class)
        .build();
```

You've seen `plannerBuilder` before if you've used the goal-oriented planner: the only thing that changes is the planner we give it. `P2PPlanner` takes two things here. The first is a maximum number of agent invocations, because a room full of peers who keep inspiring each other can go on forever (we've all been in that meeting). The second is the exit condition, a plain Java predicate on the scope.

The order of `subAgents` doesn't mean anything. I registered Zoom first, and Zoom doesn't run first.

### How the Planner Picks

The rules are simple, and once you know them, you can predict every run:

1. An agent can run when *every* key it reads is in the scope.
2. All agents that can run, run at the same time.
3. When an agent writes its output key, every agent that reads that key is woken up again.
4. An agent is not re-triggered by its own output alone. It needs one of its inputs to be written again.
5. It stops when the exit condition is true, when the invocation budget is spent, or when nobody can run anymore (a stable state).

So in the maze, Sniff goes first and says the scent splits at the entrance. That writes `Scent`, which wakes up Zoom *and* Dig together. Dig finds a rabbit burrow, a dead end. Zoom finds hoof prints heading for the middle. Both reports wake Sniff again, who now says the trail leads to the middle. That wakes Zoom, who runs there and shouts "FOUND:". That order (Sniff, then Zoom and Dig together, then Sniff, then Zoom) is written nowhere in the code.

I had never thought about letting the state drive the agents like that. Watching it run, with the scope updating at every step and puppies lighting up two at a time, is also the moment you realize how little control you have over what happens.

### Two Traps I Fell Into

The first trap is the very first move. Look at rule 1 again: an agent runs only when *every* key it reads exists. Sniff reads `Clearing` and `Burrows`. At the start, neither Zoom nor Dig has said anything, so those keys don't exist. Sniff can't run. And since Zoom and Dig are waiting for Sniff, nobody ever moves. The run ends immediately in a "stable state", with an empty result and no error.

The fix is to seed the scope with what the first agent needs:

```java
var result = search.invoke(mission,
        "(nothing yet, we have just walked in)",   // Clearing
        "(nothing yet, we have just walked in)");  // Burrows
```

The second trap is the exit condition. In an earlier version of this demo, my exit condition checked whether a peer's own output key existed, something like `scope -> scope.hasState(Scent.class)`. It reads well: "stop when we have a scent". Except the scent exists the moment Sniff has spoken once. The search ended after one turn, very confident and with no goat. The predicate has to read the *content*:

```java
static boolean goatFound(AgenticScope scope) {
    return found(scope.readState(Scent.class))
            || found(scope.readState(Clearing.class))
            || found(scope.readState(Burrows.class));
}
```

`found` is a small helper that looks for the `FOUND:` marker the prompts ask for. Whoever finds the goat, found it. Please check your exit predicate in both directions: one that can never be true runs until the budget is gone, and one that is always true ends silently after the first step. Neither of them throws an exception.

One more detail that matters: when `P2PPlanner` reaches its maximum invocations, it just stops. You get whatever is in the scope at that moment. So check the result instead of assuming the goal was reached. In the demo, I get a `ResultWithAgenticScope` back and look at the scope before claiming victory.

### When to Use Peer-to-Peer

This pattern fits when several agents work on the same problem and each one's findings can inspire the others: collaborative research, exploring a problem from several angles, or anything where you hope some ideas will emerge that nobody would have reached alone. That's what we do in a good brainstorming session, and it maps quite naturally.

It's not a silver bullet, though. First, it's chatty: every change wakes every listener. If Zoom's and Dig's reports land a moment apart, Sniff can run once for each, and in a bigger system you can end up with ten agents running in parallel, writing ten updates, waking ten agents again. Tokens are not free, so budget the invocations and watch them.

Agents running at the same time also write to the same scope. If two of them write the same key, one of them wins and you lose the other's answer, so give every agent its own output key.

And you don't know where it goes. The path depends on what each agent says, so two runs of the same request can take very different routes. That's the point of the pattern, and also the reason it's hard to audit.

## Blackboard: The Classroom

Now picture the same situation as the maze, but in a classroom. The teacher asks a question and three hands go up. If everyone answers at the same time, you won't hear a thing. So the teacher points: "You, go ahead."

That's the blackboard pattern. It's peer-to-peer where you decide who talks next. Agents still watch the shared state (the blackboard), and they still can only contribute once what they need is on it. But instead of letting every ready agent run at once, the planner picks one, lets it write, then looks at the board again.

### The Great Sausage Heist

The demo for this one is a whodunit. Every sausage in the butcher's window is gone, the door was locked, and a sausage crumb has been found in the beard of Zao, the team leader. Zao would like it known that this proves nothing.

Five Rangers stand around the board, and each one can only act once the board holds what they need:

- Sniff needs the crime and pins where the scent goes.
- Rivet, the robot dog, needs the crime and pins the camera log.
- Dig needs Sniff's scent before crawling down the right drain.
- Doc needs Rivet's cameras before testing the crumb.
- Zao needs Dig's prints *and* Doc's crumb analysis before naming the culprit.

The agents' `@K` parameters are their preconditions. Here is Zao:

```java
public interface ZaoNamesTheCulprit {
    @Agent(description = "Zao the Bouvier: reads the board and names the culprit")
    @UserMessage("""
            You are Zao, leader of the Pawer Rangers. Read the clues on the board and name the
            culprit, with the clue that convicts them. A sausage crumb was found in your own
            beard: say whether the board clears you, and how.

            The drain: {{TunnelClue}}
            The crumb: {{CrumbClue}}""")
    String conclude(@K(Keys.TunnelClue.class) String tunnel,
                    @K(Keys.CrumbClue.class) String crumb);
}
```

Rivet is a bit different, because reading a camera log is a lookup, not a judgement. Please don't ask an LLM for timestamps: it will happily invent plausible ones. So Rivet is good old Java code, annotated as an agent:

```java
public class RivetCameras {

    @Agent(name = "Rivet",
           description = "Rivet the robot dog: pins the camera log, in plain Java",
           typedOutputKey = CameraClue.class)
    public String clue(@K(Mission.class) String mission) {
        return cameraLogFor(mission);
    }
}
```

The board doesn't care whether a pin was written by an LLM or by a method. Doc wakes up on Rivet's camera log all the same. Not everything needs to be an agent backed by a model, and this pattern is a nice place to mix both.

Wiring it looks very much like peer-to-peer:

```java
Investigation investigation = AgenticServices.plannerBuilder(Investigation.class)
        .subAgents(zao, doc, dig, rivet, sniff)
        .planner(() -> new BlackboardPlanner(
                scope -> scope.hasState(Culprit.class),
                ConflictResolutionStrategy.declarationOrder()))
        .outputKey(Culprit.class)
        .build();
```

I registered the agents backwards on purpose, Zao first. If that list were the order, Zao would rule on an empty board. It isn't. Zao can't act until both clues are there, so the list is only used as a tie-break among the agents that are able to act at that moment.

Here, `hasState(Culprit.class)` is the right goal: the only agent that writes `Culprit` is the one who reads all the evidence. It's the same check that bit me in the maze, but this time it's the content I want. If you don't pass a goal predicate at all, `BlackboardPlanner` uses exactly that by default: stop when the planner's output key is in the scope.

### The Conflict Resolution Strategy

The `ConflictResolutionStrategy` is the teacher. It receives the scope and the list of agents that are ready, and returns the one that talks next. The simplest one is `declarationOrder()`: take the first ready agent in the `subAgents` list.

But you can make it smarter. During the talk, I showed a strategy that gives priority to one agent when it's ready, and falls back to declaration order otherwise:

```java
var teacher = ConflictResolutionStrategy.agentWithName("Dig")
        .or(ConflictResolutionStrategy.declarationOrder());
```

There's also `agentOfType(...)`, and versions of both that only apply when a condition on the scope is true. And since it's a functional interface, you can write whatever you want. Let's say you want every agent to get the same amount of speaking time: keep a count of how many times each one has been picked and choose the one that has spoken least. That's a lambda of `(scope, candidates) -> ...` and some good old Java.

Compared to peer-to-peer, this gives you back some control. Agents run one at a time, so there are no concurrent writes, and the order follows a rule you wrote. Each agent also fires once, and only becomes eligible again when one of its inputs is rewritten, so the board doesn't keep re-running the same contributor for nothing.

### The Dry Run Where Nobody Found the Answer

The day before my talk, I was dry-running the whole thing. In one of the runs, the blackboard stopped without an answer. No exception, no culprit. Every agent that could contribute had contributed, and nobody else could act. The planner reached what it calls quiescence and stopped.

That's a perfectly valid outcome for this pattern, and it's the same as the classroom: you can ask ten students, get ten answers, and never get the right one. With an LLM in the loop, all it takes is one link in the chain that doesn't produce what the next agent needs, and the agent waiting for it never gets the chance to act.

So treat "the planner stopped" and "the goal was reached" as two different things, and check which one you got. Note that the two planners behave differently when they hit their invocation limit, too: `P2PPlanner` stops quietly, while `BlackboardPlanner` throws an `IllegalStateException` (its default budget is 50 invocations).

### When to Use the Blackboard

This pattern shines when a problem is solved by specialists adding pieces to a shared picture: technical diagnosis, incident response ("you look at the logs first, then you check the database"), investigations like our sausage heist. It's also often presented for constraint-solving workflows. You can do some of that with it, but if you have a real constraint-solving problem, look at a tool built for it, like [Timefold](https://timefold.ai/). It's still AI, just not an LLM.

The catch is that your strategy becomes your bottleneck. The whole system's behavior depends on who you pick, and a bad strategy can make you go around in circles or never call on the agent holding the clue that matters. It's also slower than peer-to-peer, since one agent at a time means no parallelism: you trade speed for control. And as the dry run showed, a stable state is not an answer.

## Peer-to-Peer or Blackboard?

They're siblings, and in the demo I used two different stories, but you could run the exact same agents through both planners. The difference comes down to what happens when several agents are ready at the same time:

| | Peer-to-peer | Blackboard |
|---|---|---|
| Ready agents | All run, in parallel | One is picked by a strategy |
| Control over order | None | Your `ConflictResolutionStrategy` |
| Speed | Faster (parallel) | Slower (one at a time) |
| Concurrent write conflicts | Possible | No |
| Hitting the invocation limit | Stops quietly | Throws `IllegalStateException` |

If you want ideas to bounce around and you can afford the tokens, go peer-to-peer. If you want specialists to build on each other in a controlled way, go blackboard.

And if you're unsure, let's be honest: you probably need neither yet. In my experience with my clients, most real agentic systems are still workflows, and most of the rest are fine with a goal-oriented planner. These two patterns are great when the problem really is open-ended and collaborative. Don't choose them because they look cool on a dashboard. The pattern you don't use is the one you don't need to debug.

A big thank you to Mario Fusco, who builds the agentic patterns module in LangChain4j and was in the room (pair programming from the audience when my fingers weren't awake yet). As he said at the end of the session, the module is a collection of `Planner` implementations, and he's very open to contributions if you come up with a pattern of your own.

## Wrap Up

Three things to remember, only three:

1. In both patterns, the state is the boss. An agent's `@K` inputs are its precondition, and its output key is what wakes the others up. Design who reads what, and you've designed the coordination.
2. Check your exit condition in both directions. One that can't be true burns your budget, one that can't be false ends after the first step, and neither tells you. Read the content of the scope, not just whether a key exists.
3. "It stopped" is not "it worked". Both planners can reach a stable state with no answer. Check the result before you trust it.

All the demos from the talk are in the [deep-dive-langchain4j-devoxx-2026 repository](https://github.com/LoMagnette/deep-dive-langchain4j-devoxx-2026). Everything runs on a local model with Ollama, so no API key and no tokens to pay for. Go play with the puppies, break the exit predicate on purpose, and see what happens. And if you have questions or a pattern of your own, come chat with me on social media or at the next BeJUG meetup.
