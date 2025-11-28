---
title: "From Lonely Agents to Talking Teams: An introduction to A2A"
description: "From Lonely Agents to Talking Teams: An introduction to A2A"
tags: [ai, chatbot, A2A, java, langchain4j]
author: Loïc
image: 2025-11-28-A2A-cover.png
---

## Why agents need friends?

We've recently seen a boom of AI "agents". AI Agents are usually described as a service that talk to an AI model to perform some kind of goal-based operation using tools and context it assumes.

But most of these agents are still working in an isolated environment. You build an agent into your application and it offers some capabilities, but that's it. You're basically building a monolith with AI in it.

## The idea behind A2A

The initial concept is really simple, let agents talk to each other. 

If we draw a quick parallel to how we work as humans, if tomorrow I want to build a house I will probably not try to do it by myself. I will call different experts with their own expertise and skills, and they will work together to build the house. Why your AI Agent should rely on a "do-everything" agent then ?

In the real world, collaboration is how things get done. A2A is exactly that. It allows specialized agents to communicate, coordinate and share work.

## What's A2A ?

A2A is a protocol that was initially developed by [Google](TODO), but that is now part of the Linux foundation. Several big major tech companies are contributing to the project.

The protocol defines how agents can:
- discover other agents
- exchange messages and tasks (more on that later)
- work securely together
- share results or errors

### Build on familiar web standards

A2A is built using web standards that most developers already know. You can actually use different protocols:
- HTTP/REST
- JSON-RPC
- gRPC

This makes any kind of integration flexible and future-proof. The only issues with supporting different protocols is that all agents supporting A2A might not use the same one making it a bit more difficult to use.

### Secure by default

We've all heard about the infamous "S" for security in MCP or more the lack of it. A2A is by default secure. It supports enterprise grade authentication and authorization, by being on par with OpenAPI's authentication schemes.

### Discoverability

To be able to communicate with each other, agents need to, first, be able to discover each other. To do so each agent will provide an Agent card.
This card is really similar to a resume. It will contain the identity of the agent and its capabilities.

The agent card describes the agent's name, version, capabilities (eg: streaming, notifications,...) and it's specific skills. The card is exposed at a well-known URL: `.well-known/agent.json`

### to Task or not to Task?

A2A introduces the concept of task. A task, as the name suggests, is a unit of work. A2A can work with or without tasks but I personally prefer to use them.
A task can be synchronous or asynchronous. You probably don't want to block your agent while it's doing some heavy computation. A task as a state and we will see a bit further how this state can vary during the execution of the task.

//TODO feedback loop (sse, websockets, webhooks)

### Not only text

AI model can handle a lot more than just text. We often talk about multi-modality where models, like Gemini from Google, support a large variety of inputs (text, images, audio, video,...).
As you can imagine A2A supports multimodality out of the box for the inputs and outputs. It allows us to build very rich and capable agents that can interact with each others.

### Extensible

A2A is built to be extended for specific use cases. A2A offers a lot of capabilities but for certain aspect we might need more specific features. 
There's already some existing extensions for A2A such as [AP2](TODO) that offers a payment protocol over A2A.

## How does it work?

As discussed earlier, the first step is to discover other agents and get their agent cards. 
Once it's done your agent will be able to communicate with other agents and create tasks.
Those tasks will need to be managed by the agents and for that there's various status. 
Let's see them in action. In the following step, it will be assumed to have 2 agents. 
The first agent will be used a client and we will call it `Neo`.
The second agent will be used as a server and we will call it `Smith`.

// TODO add terminal state info

### Happy flow

The first step is to submit a task. In this scenario, `Neo` will send a task to `Smith`. 
Once `Smith` receives the task and accepts it, it will set the status to `SUBMITTED`.

Once `Smith` starts working on the task, he will update the status to `WORKING`.

At some point, `Smith` might realize he needs more information from `Neo` to complete the task. 
In that case it will update the status to 'INPUT REQUIRED' and send a message to `Neo` asking for more information. 

If `Neo` responds with the required information, `Smith` will update the status back to `WORKING` and continue working on the task.

This interaction is key in the protocol since it shows that the agents are not working just as a client-server solution but can really interact and collaborate with each other.

At some point, we can imagine that `Smith` will have completed his work, update the status to `COMPLETED` and send a message to `Neo` with the result of his work.
This result is what A2A calls an Artifact. An artifact is the result of a task it might contains multiple parts with different type of data (text,audio,image,...) giving us multimodal returns.

### What could go wrong?

We all know that things don't always go as planned, to handle that A2A has a couple of useful statuses that we'll go through in the following sections.

Let's imagine that `Smith` is working a task and for some reason fails at some point. In that case, the task status will be updated to `FAILED`. `Smith` will then send a message to `Neo` explaining what went wrong.

Another case could be that `Neo` gave a task to `Smith` but after all realizes it doesn't need `Smith` to complete his work. In that case, `Neo` send a message to `Smith` to cancel the task and `Smith` will update the status to `CANCELED`.

In the following scenario, `Neo` sends a task to `Smith`. But what `Neo` doesn't know is that `Smith` is already very busy and will not be able to work on it. 
In that case, `Smith` will update the status to `REJECTED` and send a message to `Neo` explaining that he will not be able to work on the task.

Finally let's imagine that `Smith` is working on a task for `Neo` but at some point realizes that `Neo` does not have the necessary privileges for `Smith` to complete the action. It's the classical case of the `sudo` on linux. In order to complete the action, `Smith` will update the status to `AUTH REQUIRED` and send a message to `Neo` asking for authorization. If then `Neo` provides the necessary authentication elements to `Smith`, then `Smith` will continue working on the task.

### Into the unknown

There's one last status supported by the protocol. This status is `unknown`. This status is used when the state of the task cannot be determined. For instance this could occur if a task has expired or the ID of the task is invalid.

## What about MCP ?


