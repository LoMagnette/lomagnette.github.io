---
title: "Agent Democracy: Voting and Debate Patterns with LangChain4j"
description: "Two agentic patterns borrowed from politics: let several agents vote and turn the ballots into a decision with a strategy you write, or let them debate in rounds until a judge rules. Built in Java with LangChain4j's VotingPlanner and DebatePlanner, including the traps: correlated voters, convergence that never comes, and the token bill."
tags: [java, langchain4j, ai]
author: Loïc
draft: true
---
<!-- TODO(Loïc): add a cover image (image: ...) -->

At home, some decisions go to a vote. Pizza or pasta, park or swimming pool. My daughter votes, my wife votes, I vote, and the dog would vote too if he could hold a pen. Some decisions don't go to a vote. If my wife says the ice on the pond looks too thin, nobody goes on the ice. Even if it's two against one.

That difference is what this article is about. When you put several agents in front of the same question, you also need a rule that says how their answers become *one* decision.

This post is part of a series based on my three-hour deep dive on LangChain4j at Devoxx Belgium 2026. The workflow basics (sequence, loop, parallel, conditional), the goal-oriented planner and the supervisor are already covered in [LangChain4j Agentic Workflows: From AI Calls to Multi-Agent Systems in Java](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/). The [previous article](/posts/when-the-state-is-the-boss-peer-to-peer-and-blackboard-agents-with-langchain4j/) was about agents driven by the shared state. Here, we go to what I called the *political side* of agentic patterns on stage: **voting** and **debate**. You probably know both already. You've lived them, in a meeting room or at an election.

All the code comes from the talk demo, a rescue team of puppies working in a small town called Barkville. The repository is on GitHub: [deep-dive-langchain4j-devoxx-2026](https://github.com/LoMagnette/deep-dive-langchain4j-devoxx-2026).

## Before we start: the dependency

The four workflows only need `langchain4j-agentic`. Voting and debate live in a separate module, `langchain4j-agentic-patterns`, so add both:

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

The agentic modules are still in beta, so check the [LangChain4j documentation](https://docs.langchain4j.dev) for the latest version. Things move fast there. The Friday before my talk, a new release landed (Mario Fusco confirmed it during the Q&A), and I was like, "Hey, I thought I had covered everything."

Both patterns are implementations of the `Planner` interface. If you read the first article, you know the idea: a planner decides which agent runs next. You plug one into `plannerBuilder()` and LangChain4j takes care of the rest.

## Voting: let the agents cast a ballot

The idea is simple. You take a bunch of agents and give them the same question. Each agent reads only the inputs it needs from the `AgenticScope` (the shared state, explained [here](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/)). Your scope can hold twenty values and a voter might only look at one. Every agent gives its vote, without seeing the others. Then a **strategy** reads all the ballots and decides.

### Is the lake ice safe?

In the demo, the mayor of Barkville wants a skating party on the lake this afternoon. The ice is 12 cm thick in the middle, but there is a dark patch near the reeds where a stream comes in, and the ducks are walking on it quite happily. Should we go?

Three puppies vote. And this is the first important point: they don't judge the same way.

Sniff the Beagle only looks at who is already on the ice and whether it holds:

```java
public interface SniffVotes {
    @Agent(description = "Sniff the Beagle: votes on the ice from what his nose and eyes tell him")
    @UserMessage("""
            Is the lake ice safe for the skating party? Judge ONLY by who is already out on the
            ice right now and whether it is holding them — nothing else: not its colour, not any
            patches, not measurements. Answer with exactly SAFE or NOT SAFE, then a few words why.

            The lake: {{Mission}}""")
    String vote(@K(Mission.class) String mission);
}
```

Doc the St. Bernard is the medic, and medics look for what could go wrong:

```java
public interface DocVotes {
    @Agent(description = "Doc the St. Bernard: votes on the ice as the medic, who says no often")
    @UserMessage("""
            Is the lake ice safe for the skating party? You are the medic and you look for
            what could go wrong: weak spots, dark patches, running water underneath. One weak
            spot is enough. Answer with exactly SAFE or NOT SAFE, then a few words why.

            The lake: {{Mission}}""")
    String vote(@K(Mission.class) String mission);
}
```

And Rivet, the robot dog, isn't an AI at all. He has a ruler and a rule: 10 cm of clear ice holds a skating party, less does not. Good old Java code annotated with `@Agent`:

```java
public class RivetVotes {

    static final double SAFE_CM = 10.0;

    @Agent(name = "Rivet",
           description = "Rivet the robot dog: votes on the measured thickness, in plain Java",
           typedOutputKey = Vote3.class)
    public String vote(@K(Mission.class) String mission) {
        double cm = firstNumber(mission, 0);
        return (cm >= SAFE_CM ? "SAFE" : "NOT SAFE") + " — measured " + cm + " cm";
    }
}
```

Not everything needs to be an agent backed by an LLM. A voter can be plain code, and here it's the most reliable voter of the three.

### Wiring the vote

Now we put them together:

```java
var sniff = AgenticServices.agentBuilder(SniffVotes.class)
        .chatModel(model).name("Sniff").outputKey(Vote1.class).build();
var doc = AgenticServices.agentBuilder(DocVotes.class)
        .chatModel(model).name("Doc").outputKey(Vote2.class).build();
var rivet = new RivetVotes();

IceVote vote = AgenticServices.plannerBuilder(IceVote.class)
        .subAgents(sniff, doc, rivet)
        .planner(() -> new VotingPlanner(VETO))
        .outputKey(Verdict.class)
        .build();

var result = vote.invoke(LAKE);
```

`IceVote` is a small interface with one `@Agent` method that takes the mission. The same builder you've seen for the other patterns. The only part that changes is `.planner(...)`. The `VotingPlanner` calls every voter at once, collects what each one returned, and hands the collection to the strategy. Whatever the strategy returns is the verdict.

### The strategy is the design

`VotingStrategy` is a one-method interface: it takes a `Collection<Object>` of votes and returns the result. LangChain4j ships a few ready-made ones: `majority()`, `average()` and `highest()`. You could say, "I'm going democratic, the side with the most votes wins." That's `majority()`.

But this is ice. So the rule for the demo is a veto: if one voter says it's not safe, we don't go.

```java
public static final VotingStrategy VETO =
        votes -> votes.stream().allMatch(VotingPattern::isSafe) ? "SAFE" : "NOT SAFE";

static boolean isSafe(Object vote) {
    String v = String.valueOf(vote).toUpperCase(Locale.ROOT);
    // "NOT SAFE" contains "SAFE", so check the negative first
    return !v.contains("NOT SAFE") && !v.contains("UNSAFE") && v.contains("SAFE");
}
```

No magic here. It's a lambda and some string checks. When I ran it live, Sniff saw the ducks and said safe, Rivet measured 12 cm and said safe, and Doc saw the dark patch and said not safe. Two against one, and the answer is still NOT SAFE. With `majority()`, the mayor would have had his skating party. That's why I insist: **the strategy is part of the design**, and you choose it on purpose. Majority is fine to pick the name of the team mascot. For ice, for money, for anything with risk, you probably want a veto. You could also give one voter more weight, or stop as soon as someone sees a risk. You can write whatever you want.

### Please make the votes easy to count

Look again at the prompts: "Answer with exactly SAFE or NOT SAFE, then a few words why." At the end, your strategy needs to *read* those ballots. If one agent says "Yes, looks fine!" and another "I would rather not", good luck counting.

There's a trap even with `majority()`: it counts *equal* votes. "SAFE, the ducks are on it" and "SAFE, 12 cm" are two different votes for it. In the demo, I reduce every ballot to a single word before counting.

You might say, "Yeah, okay, but I could just put an LLM in the strategy and ask it what the votes mean." You could. But you're adding more non-deterministic behaviour exactly at the place where you want a clear decision. I wouldn't go that way. The better option is the same advice I gave for the conditional workflow in the [first article](/posts/langchain4j-agentic-workflows-from-ai-calls-to-multi-agent-systems-in-java/): have your voters return an **enum**. LangChain4j maps the answer to the enum value for you, the set of possible answers is closed, and your strategy becomes a plain `switch`.

### When does voting make sense?

- Classification: if one agent isn't reliable enough on a label, put three and let them vote. You often get a better answer.
- Candidate selection: that's why we use votes everywhere in real life.
- Safety checks, with a veto strategy like the ice.

### Four voters don't mean four opinions

On stage, I asked the room: "What's the better language, C# or Java?" At Devoxx, I already know the answer. I didn't need to ask all of them, because they all share the same bias.

Agents are the same. If your four voters use the **same model**, they probably have the same bias. If they use the **same tools**, same problem. If they share the **same context**, the same prompt or the same RAG documents, same problem again. Put the same prompt three times and you get one opinion with a bigger bill, and three voters agreeing on the same mistake.

That's why the three voters in the demo judge in three different ways: a nose, a medic and a ruler. When you design a vote, think about how your voters differ before you think about how many you need.

Two more caveats:

- Aggregation is a problem of its own. Start with the simplest strategy you can, and only go further when it fails. Don't build an electoral college for three agents.
- Tokens are not free. Four voters is four calls, and it doesn't mean four times the quality. Ask yourself honestly: is my single classifier good enough? Sometimes it is.

### Mixing it in

A vote is an agent, like everything else built with `AgenticServices`. So you can nest it in a bigger system. In the demo, the "Lake Party" mission does the job properly. A parallel mapper sends Sniff to check four spots of the lake at the same time, a plain Java step writes the findings into the report, the same three voters decide with the same veto, and Howl the Husky announces the result to the town:

```java
IceBallot ballot = AgenticServices.plannerBuilder(IceBallot.class)
        .subAgents(sniff, doc, new RivetVotes())
        .planner(() -> new VotingPlanner(VETO))
        .outputKey(IceVerdict.class)
        .build();

LakeParty party = AgenticServices.sequenceBuilder(LakeParty.class)
        .subAgents(survey, report, ballot, howl)
        .outputKey(Announcement.class)
        .build();
```

A workflow and an agentic pattern in the same sequence. Honestly, what this demo taught me was mostly about glue. The voters read the `Mission` key because that's the key they declared, so the inspection has to be written back *into* the mission for them to see it. When you reuse an agent, you accept the keys it already declared.

## Debate: the Belgian solution

The next pattern is political too, and when you see how it ends you'll understand why I call it the Belgian solution.

You take a bunch of agents again, but this time they have roles. Some are **debaters**, and one is the **judge**. In LangChain4j, the rule is simple: every sub-agent except the last one is a debater, and the last one is the judge. The order is the contract. And you need at least three agents. I can debate with myself, but it's not the most interesting debate ever. Two debaters and a judge is the minimum.

A debate runs like this:

1. Each debater gets the inputs it needs and gives its position.
2. A **convergence strategy** looks at the answers: do they agree?
3. If they do, the debate ends. If not, we start a new round. This time, each debater receives what the others said in the previous round, in a special key of the scope called `debateContext`, and produces a new answer.
4. This continues until they converge or until we hit the maximum number of rounds.
5. Then the judge gets the final statements and gives the answer.

Why a maximum number of rounds? Because you cannot let a debate run forever. We tried that in Belgium. We spent almost two years without a government. Please don't do that with your agents.

And why a judge? If everybody converged, the judge is just a summary of what happened. If they didn't, someone has to decide. Is the judge's answer the best one? That's debatable, of course. But at some point your system needs to stop and give an answer.

### Dog park or cat café?

In the demo, the Barkville town council has to decide what to do with the empty lot on Elm Street: a dog park or a cat café. Howl the Husky argues for the dog park. Marmalade the cat argues for the cat café (I told you at Devoxx there wouldn't be many cats in this talk, and the one that shows up is the villain). Fifi the Poodle judges.

A debater reads the motion and the last round:

```java
public interface HowlArgues {
    @Agent(description = "Howl the Husky: argues for the dog park, loudly")
    @UserMessage("""
            You are Howl, at the Barkville town council, arguing FOR the dog park. Take your turn
            for this round. If the last round below is empty, the debate has just begun: open your
            case. Otherwise answer the strongest thing Marmalade said, then make one new point.
            Two or three sentences, dramatic but on the subject.

            The motion: {{Motion}}
            Last round: {{debateContext}}""")
    String argue(@K(Keys.Motion.class) String motion,
                 @K(Keys.DebateContext.class) String lastRound);
}
```

`DebateContext` is a typed key, but its name belongs to the library, not to me. So instead of typing `"debateContext"` by hand and getting it wrong at 10 p.m., I point the key at the library's constant:

```java
public record DebateContext() implements TypedKey<String> {
    @Override
    public String name() {
        return DebatePlanner.DEBATE_CONTEXT_KEY;
    }
}
```

A typo there would leave both debaters reading nothing, and they would keep opening the debate round after round. Trust me, typos in keys are the kind of bug I've written more often than I'd like to admit.

The judge reads the same context, which after the last round holds the closing statements:

```java
public interface FifiJudges {
    @Agent(description = "Fifi the Poodle: hears the closing statements and declares the winner")
    @UserMessage("""
            You are Fifi, moderating the Barkville town council. The debate is over; below are
            the closing statements. Declare a winner — dog park or cat café — with exactly three
            reasons, each taken from something actually said. Also name the winner's weakest
            moment.

            The motion: {{Motion}}
            Closing statements: {{debateContext}}""")
    String judge(@K(Keys.Motion.class) String motion,
                 @K(Keys.DebateContext.class) String closing);
}
```

And the wiring:

```java
var howl = AgenticServices.agentBuilder(HowlArgues.class)
        .chatModel(model).name("Howl").outputKey(HowlTurn.class).build();
var marmalade = AgenticServices.agentBuilder(MarmaladeArgues.class)
        .chatModel(model).name("Marmalade").outputKey(MarmaladeTurn.class).build();
var fifi = AgenticServices.agentBuilder(FifiJudges.class)
        .chatModel(model).name("Fifi").outputKey(Verdict.class).build();

Debate debate = AgenticServices.plannerBuilder(Debate.class)
        .subAgents(howl, marmalade, fifi)   // debaters first, the judge LAST
        .planner(() -> new DebatePlanner(3, ConvergenceStrategy.unanimous()))
        .outputKey(Verdict.class)
        .build();
```

Same builder again, a different planner. `DebatePlanner` takes the maximum number of rounds and a convergence strategy.

### Only the last round, and that's on purpose

The debaters only know what the others said in the *last* round. They don't get a live feed of the debate. There's a good reason for that. If you've ever watched a political debate on TV, all you hear is people screaming over each other. Here, everybody gets the other side's position, takes the time to think, and answers. Maybe we should stop paying politicians and use AI. I'm not sure it's the best solution either.

One side effect to know: since `debateContext` is overwritten every round, the judge rules on the closing statements, not on the whole debate. If you want the full transcript (I wanted it for the demo), rebuild it from the scope's record of agent invocations.

### Writing your own convergence strategy

`ConvergenceStrategy` is a functional interface too: it receives the debaters' latest answers and returns `true` when you consider they agree. The built-in ones are `unanimous()` and `unanimousLastWord()`.

On stage, I used `unanimous()`: the good consensus, the Belgian way. Let's be honest about it, though. `unanimous()` means the answers are *identical*, and two puppies writing prose on opposite sides never say exactly the same thing. So the demo always runs the three rounds. `unanimousLastWord()` is more forgiving: it converges when every debater ends on the same word, which works well if you ask them to finish with their position.

And not every debate needs to end with everybody agreeing with everybody. You could stop when the positions stop changing between rounds, or when a score crosses a threshold. It's a lambda, write the one that fits your problem:

```java
ConvergenceStrategy samePosition = answers -> answers.stream()
        .map(a -> String.valueOf(a).contains("DOG PARK") ? "DOG PARK" : "CAT CAFE")
        .distinct()
        .count() == 1;
```

### When does debate make sense?

- Risk analysis: make the case against an option get said out loud before you pick it. Ask a single agent and it picks a side and rationalises it.
- Architecture decisions: give one agent the product owner's point of view, one the architect's, one the developer's and one the tester's, and let them debate. We already do this when we write prompts with personas. Here you can build it in Java with LangChain4j.
- Investment analysis: a bull and a bear, and a judge. Again, do what you want with your money. I'm not responsible for that.

### And of course, it's not a silver bullet

- More agents means more time and more cost. Two debaters for three rounds is six calls before the judge says anything. Tokens are not free.
- Agreement is not truth. It's still a political solution, and we know we can't always trust politicians. If your debaters share the same model and context, they can converge very quickly on the same wrong answer, exactly like the voters.
- Convergence can take forever. We've all had that meeting about a feature where you spend eight hours with someone who has good reasons to go the other way, and so do you. If your debaters start from very different positions, they might never converge. That's why the round limit and the judge exist.
- Checking convergence is code you write and maintain, and it's not easy to decide whether two paragraphs "agree".

## Voting or debate?

Both patterns put several agents on the same question. What changes is whether they talk to each other. In a vote, nobody talks to anybody: each agent answers once, independently, and a rule counts. It's cheap (one round), fast (everyone runs in parallel) and predictable if your strategy is plain code. In a debate, the agents *react* to each other, round after round, and a judge closes. It's slower and more expensive, and you get the arguments along with the result.

My rule of thumb: if you can describe the decision as "count the answers", vote. If you want the agents to challenge each other before deciding, debate. And if a single agent does the job well enough, keep a single agent. The pattern you don't use is the one you don't need to debug.

## Wrap Up

Three things to remember, only three:

1. The strategy is part of the design. `VotingStrategy` and `ConvergenceStrategy` are one-method interfaces. Majority or veto, unanimous or "same position" can turn the same ballots into opposite decisions, so choose on purpose and keep the strategy in plain Java.
2. Diversity beats quantity. Voters or debaters with the same model, tools and context share the same bias. Give them different criteria, and have them answer in a closed format, ideally an enum, so the results are easy to count.
3. Always put a limit. A round limit for debates, a cost budget for both. Agreement isn't truth, and tokens are not free.

Thanks to Mario Fusco, who builds these patterns in LangChain4j and joined the Q&A at Devoxx with some fresh news. As he said at the end of the session, all these patterns are implementations of the same `Planner` interface and live in the `langchain4j-agentic-patterns` module, so if you build a pattern that's missing, please contribute it back.

The full demo, voters, debaters, the lake party and everything else, is in the [deep-dive-langchain4j-devoxx-2026](https://github.com/LoMagnette/deep-dive-langchain4j-devoxx-2026) repository. It runs on a local model, so you don't need any API key. Go play with it, and let me know which strategy your puppies end up with.
