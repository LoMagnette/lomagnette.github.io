---
title: "Beliefs, Desires, Intentions: BDI Agents and Custom Planners with LangChain4j"
description: "The third part of a series based on my Devoxx deep dive: how the Belief-Desire-Intention pattern lets LangChain4j agents chase ranked goals and change their mind halfway through, and how to write your own Planner in plain Java when no built-in pattern fits."
tags: [java, langchain4j, ai]
author: Loïc
draft: true
---
<!-- TODO(Loïc): add a cover image (image: bdi-custom-planner-cover.png) -->

The first time I read the name *Belief, Desire, Intention*, I thought it was the new *Fifty Shades of Grey* book. It's not. Then I tried to understand it, and it took me a long time. Of all the agentic patterns I showed during my three-hour deep dive at Devoxx Belgium, this is the one I found the most complex. So when I reached that slide on stage, I told the room: "If you have a coffee next to you, now is the moment to take a sip." I'll say the same to you.

This is the third article in a series built from that talk. The [first one](/posts/when-the-state-is-the-boss-peer-to-peer-and-blackboard-agents-with-langchain4j/) covered peer-to-peer and blackboard agents, where the shared state decides who runs next. The [second one](/posts/agent-democracy-voting-and-debate-patterns-with-langchain4j/) covered voting and debate. Here we look at BDI, and then at what to do when none of the patterns fit: write your own planner.

If agents, the `AgenticScope` and typed keys are new to you, start with my [introduction to LangChain4j agentic workflows](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/). I'll assume you've read it.

All the code comes from the [demo repository of the talk](https://github.com/LoMagnette/deep-dive-langchain4j-devoxx-2026). It runs on a local model through Ollama, so you can try it without any API key. The examples use LangChain4j `1.22.0` and the agentic modules `1.22.0-beta32`. BDI lives in the patterns module, not in the core agentic one:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agentic-patterns</artifactId>
    <version>1.22.0-beta32</version>
</dependency>
```

## What BDI Means (No, Not That)

The name describes the pattern quite well, so let's take it word by word.

**Beliefs** are what the system holds to be true about the world right now. You have your own beliefs. For example, I believe Java is the best programming language. You might disagree, and that's fine, it's a belief. In LangChain4j, the beliefs are what sits in the `AgenticScope`.

**Desires** are the goals. A desire needs three things:

- An activation condition: the minimal state you need to be in before you can even try. If tomorrow I want a kid, I first need a partner, and probably a good relationship with that partner. Otherwise we end up in a bad place.
- A definition of done: when do we say this desire is fulfilled? If I want to be on stage at Devoxx, I can't say, "Hey, I submitted the CFP, I'm done." That's not how it works. You need a clear boundary.
- A priority: maybe you love playing video games, but you'd rather take care of your family first. The planner needs to know which desire matters most.

**Intentions** are how you plan to fulfil a desire: an ordered list of agents you think will get you there.

With those three ingredients, the planner can run a loop:

1. Look at the beliefs. Are all desires already fulfilled? If so, lucky you, there's nothing to do.
2. Find the desires that are achievable: their activation condition matches the current beliefs. The others are ignored for now.
3. Among those, pick the one with the highest priority.
4. Run the next step of its intention. The first time, that's the first agent. If we come back to that desire later, we continue with the second.
5. The agent writes to the scope, so the beliefs have changed. Go back to step 1.

{#mermaid}
flowchart TD
    A([Beliefs in the AgenticScope]) --> B[All desires done?]
    B -- yes --> Z([Finished])
    B -- no --> C[Keep only achievable desires]
    C -- none left --> Y([Stuck: nothing can move])
    C --> D[Pick the highest priority]
    D --> E[Run the next agent of its intention]
    E -- writes to the scope --> A
{/}

There's a subtlety in step 3, and it's the part that surprised me when I first saw it run. The highest-priority achievable desire can change between two iterations. You start working on desire number three, the agent updates the scope, and suddenly desire number one becomes achievable. The planner drops what it was doing and switches. Next round, it might switch back.

In French we say someone runs around "comme un poulet sans tête", like a headless chicken. That's what it looks like the first time you watch it, but it always does the most important thing it can do, given what it knows at that moment.

## Squirrel!

If you have a dog, you already understand BDI. I have a puppy at home, and I can promise you his priorities change every three seconds depending on what he sees.

So the demo is about Zoom, a greyhound from my rescue team of puppies. Zoom has three desires:

| Desire | Priority | Achievable when… | Done when… | Intention |
|---|---|---|---|---|
| Rescue the kid | 100 | he has seen a stranded kid | `Rescued` is in the scope | go to the ford, bring the kid back |
| Chase that squirrel | 50 | the radio mentioned a squirrel | `Treed` is in the scope | run up the bank, chase it up a tree |
| Nap | 10 | always | `Napped` is in the scope | nap |

The input is a radio message: a squirrel just ran towards the riverbank, and the old bridge collapsed in last night's storm.

At the start, Zoom hasn't seen any kid, so the rescue isn't achievable. The squirrel is, and it beats the nap. Zoom starts chasing. Step one of that intention is running up the riverbank, and from the top he sees across the river: the bridge is out and a kid is stranded on the other side. That's a new belief. Now the rescue is achievable, and 100 beats 50. The planner drops the squirrel and switches. Zoom runs to the ford, crosses, brings the kid back. Rescue done.

Then the squirrel desire is the top achievable one again, and the planner picks it up at step two, not from scratch. Zoom chases the squirrel up a tree (he doesn't catch it, he never does). Finally, the nap.

When I ran it live at Devoxx, you could watch the scope update and the planner jump from one desire to the other. With three desires it's easy to follow. Imagine ten or twenty. It can get quite wild, but it's fun to watch, and it can be useful.

## The Code

Each step is a regular agent. Here's the first step of the squirrel intention:

```java
public interface ZoomUpTheBank {
    @Agent(description = "Zoom, squirrel plan step 1: chase it up the riverbank")
    @UserMessage("""
            You are Zoom the Greyhound, and you are chasing a squirrel: step one, chase it up
            the riverbank. From the top of the bank you can see across the river. Say what you
            see there in two plain lines. Unless the radio says the kid is already safe, you
            see that the bridge is out and a kid is STRANDED on the far bank. Say exactly that.

            What the radio said: {{Beliefs}}""")
    String act(@K(Keys.Beliefs.class) String beliefs);
}
```

It reads the `Beliefs` key (the radio message) and writes a `Lookout` key (what Zoom saw). The keys are plain records implementing `TypedKey`:

```java
public record Beliefs() implements TypedKey<String> {}
public record Lookout() implements TypedKey<String> {}
public record Rescued() implements TypedKey<String> {}
public record Treed() implements TypedKey<String> {}
public record Napped() implements TypedKey<String> {}
```

Then the desires. `Desire.of` takes a name, a priority, the activation condition, the definition of done, and the agent classes that make up the intention, in order:

```java
List<Desire> desires = List.of(
        Desire.of("rescue the kid", 100,
                s -> sees(s, "stranded"),            // only once he has SEEN the kid
                s -> s.hasState(Rescued.class),
                ZoomToTheFord.class, ZoomBringsTheKidBack.class),
        Desire.of("chase that squirrel", 50,
                s -> told(s, "squirrel"),
                s -> s.hasState(Treed.class),
                ZoomUpTheBank.class, ZoomTreesTheSquirrel.class),
        Desire.of("nap", 10,
                s -> true,
                s -> s.hasState(Napped.class),
                ZoomNaps.class));
```

No magic here. The activation condition and the definition of done are good old Java lambdas over the `AgenticScope`. `sees` and `told` are small helpers that check whether the `Lookout` or `Beliefs` text contains a word:

```java
static boolean told(AgenticScope scope, String phrase) {
    return mentions(scope.readState(Beliefs.class), phrase);
}

static boolean sees(AgenticScope scope, String phrase) {
    return mentions(scope.readState(Lookout.class), phrase);
}
```

I made a deliberate choice here: deciding what Zoom believes is done in Java, not by the model. If an LLM decided whether the kid was seen, the intention would change for reasons nobody can point at. The agents can be as creative as they want in what they write. The decision to switch desires stays readable.

Finally, the planner. It's the same `plannerBuilder` you saw with the goal-oriented pattern, with a `BDIPlanner` plugged in:

```java
ZoomsHead zoom = AgenticServices.plannerBuilder(ZoomsHead.class)
        .subAgents(upTheBank, treesIt, toTheFord, bringsKid, nap)
        .planner(() -> new BDIPlanner(desires))
        .outputKey(Napped.class)
        .build();

var result = zoom.invoke("Radio: a squirrel has just run off towards the riverbank. "
        + "Also, the old bridge over the river came down in the storm last night.");
```

One thing to be careful about: the desires reference agent classes, but the builder receives agent instances. If you list an agent in a desire and forget to pass its instance to `subAgents`, it will fail. So double-check that list every time you add a step to an intention.

Try changing the radio message to say that Officer Jo already has the kid safe. Zoom never sees anyone stranded, the rescue never becomes achievable, and the squirrel plan runs straight through.

### Not every intention runs to the end

Defining ten agents in an intention doesn't mean all ten will run. As soon as the definition of done is satisfied, the desire is marked as complete and the planner never looks back at it.

And sometimes one action fulfils several desires at once. Let's say I have three desires: going to Devoxx, speaking at Devoxx, and meeting Mario Fusco. By standing on that stage, I fulfilled all three. The pattern supports that: whatever agent writes the key that a definition of done checks, the desire is done.

### The declarative version

If you prefer annotations to builders, the same thing fits in one interface. `@PlannerAgent` lists the sub-agents, and a static method annotated with `@PlannerSupplier` provides the planner:

```java
public interface ZoomsHead {

    @PlannerAgent(subAgents = { ZoomUpTheBank.class, ZoomTreesTheSquirrel.class, ZoomToTheFord.class,
                               ZoomBringsTheKidBack.class, ZoomNaps.class },
                  typedOutputKey = Keys.Napped.class)
    ResultWithAgenticScope<String> invoke(@K(Keys.Beliefs.class) String beliefs);

    @PlannerSupplier
    static Planner planner() {
        return new BDIPlanner(List.of(
                Desire.of("rescue the kid", 100, /* ... */),
                Desire.of("chase that squirrel", 50, /* ... */),
                Desire.of("nap", 10, /* ... */)));
    }
}
```

I like this form for BDI in particular. The desires are the logic of the agent, and here they live right next to it instead of somewhere in a configuration class.

## When to Use BDI (and When Not To)

BDI is a good fit when you know what you want to achieve but don't want to describe how as a fixed path. A few use cases I find convincing:

- Autonomous incident response: "restore the service" outranks "notify the team", which outranks "write the post-mortem". When the situation changes, priorities follow.
- Game characters: give an NPC desires (eat, flee, explore, defend) and let it react to the state of the world. This is where BDI comes from, and it fits naturally.
- Business or portfolio management, the boring one but a real one: competing objectives with clear priorities and conditions.

Now the caveats.

Designing desires is hard. You need the right mental model, you need to figure out which agents serve which desire, and you need activation and done conditions that are neither too loose nor too strict. Also, in the version I used, an intention that runs to its end without satisfying its desire is treated as an error, not as a reason to try again. So your definition of done must match what your agents actually produce.

The plans are predefined. BDI won't invent a new path. It only calls the agents you put in the intentions. That's good for control, but don't expect a supervisor's flexibility.

You can also get starvation. If a desire with the highest priority is very complex, it can keep the planner busy for a long time while five small, low-priority desires never get a turn.

And priorities are a guess: we set them based on what we think matters. In practice, finishing five small tasks may be worth more than finishing the big one. Expect to tweak them.

So please don't start with BDI. Start with the workflows from my [first article](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/), and reach for BDI when you really have competing goals that should take over from each other as the world changes.

## Do Your IKEA: Writing Your Own Planner

All the patterns I showed at Devoxx (sequence, loop, goal-oriented, supervisor, peer-to-peer, blackboard, voting, debate, BDI) are implementations of one small interface: `Planner`. In my first article I only showed its signature. Let's actually build one.

```java
public interface Planner {
    default void init(InitPlanningContext context) { }
    default Action firstAction(PlanningContext context) {
        return nextAction(context);
    }
    Action nextAction(PlanningContext context);
}
```

Three methods:

- `init` runs once. It gets the sub-agents, so it's the place to set up your planner's state.
- `firstAction` decides where to start. By default, it just calls `nextAction`.
- `nextAction` is called over and over as the system evolves. It looks at the context and returns an `Action`: either call an agent (`call(agent)`) or stop with a result (`done(result)`).

### The puppies need a nap

My rule for the demo: Zao, who runs the rescue HQ, has four puppies on call (Sniff, Zoom, Dig and Doc) and a list of missions. Nobody does two missions in a row, because they're puppies and they need rest. The full policy:

- if the puppy for the next mission is hungry, feed it;
- if its energy is above 70 and it wasn't the last one out, send it;
- otherwise, it takes a nap.

Feeding and napping change the state but call no agent, so the loop keeps going until somebody is sent or it's bedtime.

`init` collects the sub-agents by name:

```java
public final class NapSchedule implements Planner {

    private static final int MISSION_COST = 40, NAP = 40, FOOD = 20, BEDTIME = 12;

    private final Map<String, AgentInstance> rangers = new LinkedHashMap<>();
    private final Map<String, Integer> energy = new LinkedHashMap<>();
    private final Set<String> hungry = new HashSet<>();
    private final List<String> queue = new ArrayList<>();
    private final List<String> schedule = new ArrayList<>();
    private String last;
    private int steps;

    @Override
    public void init(InitPlanningContext context) {
        context.subagents().forEach(a -> rangers.put(a.name(), a));
    }
```

There's no `firstAction` override: the default (delegate to `nextAction`) is exactly what I need. All the interesting work happens in `nextAction`:

```java
    @Override
    public Action nextAction(PlanningContext context) {
        AgenticScope board = context.agenticScope();
        // first call only: parse missions, energy levels and who is hungry from the scope
        readTheBoardOnce(board);

        var report = context.previousAgentInvocation();
        if (report != null) {
            schedule.add(report.agentName() + " reports: " + report.output());
        }

        while (steps++ < BEDTIME) {
            if (queue.isEmpty()) {
                return done(String.join("\n", schedule));
            }
            String mission = nextMission();
            String pup = whoDoes(mission);

            if (hungry.remove(pup)) {
                energy.merge(pup, FOOD, Integer::sum);
                schedule.add("FEED " + pup);
            } else if (energy.get(pup) > 70 && !pup.equals(last)) {
                queue.remove(mission);
                energy.merge(pup, -MISSION_COST, Integer::sum);
                last = pup;
                schedule.add("GO   " + pup + " → " + mission);
                board.writeState(Call.class, mission);
                return call(rangers.get(pup));
            } else {
                energy.merge(pup, NAP, Integer::sum);
                if (pup.equals(last)) {
                    last = null;   // a nap breaks the run: rested, he may go again
                }
                schedule.add("NAP  " + pup);
            }
        }
        return done(String.join("\n", schedule));
    }
}
```

`nextMission()` picks the first mission someone other than the last puppy can do, and `whoDoes()` maps a mission to a puppy by keyword (Dig digs, Zoom fetches, Doc checks, Sniff finds). I left them and the parsing out, they're in the repository. Before handing over to a puppy, the planner writes the mission to the scope (`Call`), because that's the key the puppy agents read.

Plugging it in looks exactly like any built-in planner:

```java
DaysWork day = AgenticServices.plannerBuilder(DaysWork.class)
        .subAgents(sniff, zoom, dig, doc)
        .planner(NapSchedule::new)
        .outputKey(Schedule.class)
        .build();
```

That's the part I like the most. The builder doesn't care whether the planner comes from the library or from your repository. And because a planner-based agent is still an agent, you can nest it in a sequence, a loop, or anything else.

If you look at the logs of a run, no puppy ever goes on two missions in a row. It's a simple rule, and since it's plain Java you can go much further: load balancing between agents, budgets, business hours, whatever your domain needs.

### Guard rails are your job

You own the loop now. Nothing stops your planner from never terminating or calling the same agent forever. In my case, the empty queue and `BEDTIME` are the guard rails, and I had to write them myself. Please always put one in.

And please don't write a planner that calls an LLM to decide the next step. I know you could. It's not because you can do it that you should do it. If you want an LLM to decide, the supervisor pattern already exists. A custom planner is valuable precisely because it's deterministic code you can read, test, and debug.

Reach for a custom planner only when none of the built-in ones can express what you mean. Maybe BDI is too rigid for your case, peer-to-peer too chaotic, and you need something in between. Then write it. And if it turns out to be useful, contribute it back. During the Q&A at Devoxx, Mario Fusco, who builds most of these patterns, invited exactly that: the `langchain4j-agentic-patterns` module is where he collects them, and he'd be happy to receive a pull request with yours.

## Wrap Up

Three things to remember, only three:

1. BDI is beliefs, ranked desires and intentions. The planner always works on the highest-priority desire it can achieve right now, and it will switch when the beliefs change.
2. Keep the decisions in Java. Activation conditions, definitions of done and custom planners are plain code. That's what keeps a system that changes its mind understandable.
3. The pattern you don't use is the one you don't need to debug. BDI and custom planners are powerful, but start with workflows and only move here when your problem really asks for it.

The code is in the [demo repository](https://github.com/LoMagnette/deep-dive-langchain4j-devoxx-2026). Run the BDI mission, change the radio message, and watch Zoom change his mind. Then write a planner of your own. I'd love to see what rules you come up with for your puppies.
