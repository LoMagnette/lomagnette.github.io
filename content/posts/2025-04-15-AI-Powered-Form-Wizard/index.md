---
title: "Bridging the Gap: Full-Stack Development Without the Headaches"
description: Bridging the Gap Full-Stack Development Without the Headaches
tags: [ai, chatbot, form, java, quarkus, langchain4j]
author: Loïc
image: form-wizard-cover.png
---
##### Hand-Picked Top-Read Stories

# AI-Powered Form Wizards: Chat, Click, Done

## Transforming Form Filling into a Conversational Experience

Forms are everywhere-tax declarations, job applications, or even signing up for a new service. Although some forms are simple, many include ambiguous fields, complicated logic, or subpar design. This may frustrate users and make them more likely to make mistakes. Completing paperwork shouldn't be like solving a puzzle

Traditional forms, with their rigid structures and often confusing layouts, present a significant hurdle for users. Our objective was to dismantle this static paradigm and replace it with a dynamic, conversational interface. Instead of forcing users to navigate a pre-defined maze of fields, we envisioned an interactive experience where an AI assistant adapts in real-time. This approach fundamentally shifts the burden of data entry and validation. A user chatting with an AI can dramatically reduce errors and streamline the overall process. Imagine a conversation, not a questionnaire, where the AI guides you through each step.

Through this article we'll take as an example a website that allows you to adopt puppies.


## Back to basics

Before we try to replace forms with a chatbot, we need to take a minute to just quickly review a few elements. What tools, library, framework we're using but also a few key AI concepts.

### Tooling

Java has significantly improved its ability to integrate and communicate with AI over the last two years. These days, we have tools like Spring AI and [LangChain4j](https://docs.langchain4j.dev/) that offer strong integration. LangChain4j will be used in this article. Because [Quarkus](https://quarkus.io/) offers a fantastic integration with LangChain4j, we will also use it as a result. Quarkus is well known for its cloud-ready, lightweight solution. However, the developer experience is a significant benefit in this instance. It's the ideal framework for working with AI, where you need to make frequent adjustments to your prompt. It offers hot reload while in development mode. There's no need to reload; you can simply make changes to your code and see the outcome right away.

### AI Concepts

Before we go further, we're just going to take the time to recap some key concepts when using AI. Those concepts will be useful to build our chat-oriented approach, but if you are already familiar with AI and LangChain4j, you can directly jump to the [next step](#how-to-get-started) .

#### Prompt and AI service:

LangChain4j provides a rich API to interact with LLMs, allowing you to configure the integration. On top of that, the LangChain4j extension of Quarkus makes everything even easier with enterprise-grade configuration. You, of course, need to first decide which model you want to use and add the corresponding extension. Then you just need to configure a couple of properties, such as the API key.

Then you can define your first AI-powered service. To do so, you only need an interface annotated `@RegisterAIService` . Finally, define a method and use the `@SystemMessage` and `@UserMessage` to provide your prompt to interact with the LLM.

```java
@RegisterAiService
public interface Bot {

    @SystemMessage("""
            You are an AI named Pawtrick answering questions about puppy adoption.
            Your response must be polite, use the same language as the question, and be relevant to the question.
            When you don't know, respond that you don't know the answer and the bank will contact the customer directly.
            """)
    String chat(@UserMessage String question);
}
```

*Example of AI service with @SystemMessage and @UserMessage*

The @SystemMessage annotation is the first message delivered to the LLM. It gives the scope and preliminary instructions. It outlines the function of the AI service in the exchange. The primary instructions sent to the LLM are defined in the @UserMessage annotation. Usually, it includes requests along with the structure of the anticipated response.

```java
@RegisterAiService
public interface Bot {

    @SystemMessage("""
            You are an AI named Pawtrick answering questions about puppy adoption.
            Your response must be polite, use the same language as the question, and be relevant to the question.
            When you don't know, respond that you don't know the answer and the bank will contact the customer directly.
            """)
    @UserMessage("""
            You should try to answer the user questions about puppy adoption.
            \{question}
            """)
    String chat(String question);
}
```

*Example of AI service with @SystemMessage and @UserMessage*

You can find more info about all of this [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/ai-services.html) .

#### Memory:

An LLM is stateless by definition, which means it will completely forget everything from one exchange to the next. We must give it a means of recalling the previous exchange if we hope to engage in meaningful dialogue with it. We refer to that as memory.

Using Quarkus, the chat memory is on by default. If you want different memory for each user, then you need to add a parameter to your AI Service method and annotate it with `@MemoryId.`

```java
@RegisterAiService
public interface Bot {

    @SystemMessage("""
            You are an AI named Pawtrick answering questions about puppy adoption.
            Your response must be polite, use the same language as the question, and be relevant to the question.
            When you don't know, respond that you don't know the answer and the bank will contact the customer directly.
            """)
    @UserMessage("""
            You should try to answer the user questions about puppy adoption.
            \{question}
            """)
    String chat(@MemoryId long id, String question);
}
```

*Example of AI service using @MemoryId*

#### RAG:

[RAG](https://docs.langchain4j.dev/tutorials/rag) , often referred to as Retrieval Augmented Generation, is a technique for giving your model personalized knowledge, basically, information that it most likely lacks, to enable it to deliver insightful responses in your situation. To accomplish this, documents must be ingested, stored in a vector database, and made retrievable. [Quarkus Easy RAG](https://docs.quarkiverse.io/quarkus-langchain4j/dev/easy-rag.html) makes it simple to set up. Of course, you can - and probably should - go beyond this implementation. You can find more information on more complex RAG approaches at:

- [https://glaforge.dev/talks/2024/10/14/advanced-rag-techniques/](https://glaforge.dev/talks/2024/10/14/advanced-rag-techniques/)
- [https://docs.langchain4j.dev/tutorials/rag/#advanced-rag.](https://docs.langchain4j.dev/tutorials/rag/#advanced-rag.)

#### Tools/function calling:

Certain LLMs have the ability to invoke functions from your code. This gives you the chance to provide them with a wide range of capabilities. This is referred to as a tool or function calling. This provides you with the chance to further expand your capabilities. Basically, everything you can program, you can give them access to. For instance, you give access to your database or call a web service. Naturally, you must exercise caution because doing so could give the LLM permission to do risky actions. We don't want our LLM to be able to erase our database, for instance. Using Quarkus and LangChain4j makes it very [simple](https://docs.quarkiverse.io/quarkus-langchain4j/dev/agent-and-tools.html#_declaring_a_tool) .

```java
@ApplicationScoped
public class PuppiesService {

    @Tool("Get all the available puppies")
    public List<Puppy>  getPuppies() {
        return Puppy.listAll();
    }
}
```

```java
@RegisterAiService(
        tools = PuppiesService.class
)
public interface Bot {

    @SystemMessage("""
            You are an AI named Pawtrick answering questions about puppy adoption.
            Your response must be polite, use the same language as the question, and be relevant to the question.
            When you don't know, respond that you don't know the answer and the bank will contact the customer directly.
            """)
    @UserMessage("""
            You should try to answer the user questions about puppy adoption.
            \{question}
            """)
    String chat(@MemoryId long id, String question);
}
```

### Ready?

Now that we have everything organized, we can attempt to use a chatbot to replace those forms.

## How to get started

How do we go from messages from a user to fill in an object, validate the data, and give feedback to the user?

Saying something like "fill the object, validate the data, and write feedback for the user" was a naïve way to go about this. Using a large model, like the most recent OpenAI model, may yield some results, but this is not a given.

Rather, we ought to tackle the issue as we typically do when programming a complicated feature. In essence, break the issue down into smaller issues before assembling everything.

## Fill in the object

Most likely, the first step is to attempt to gather whatever information the user submits and to fill out the form or object.

### Structured output

To fill in the form, you are probably trying to fill in some POJO with the information. LLMs are capable of easily outputting data as JSON, but you need to give them a format. Then, with the help of [LangChain4j](https://docs.langchain4j.dev/tutorials/structured-outputs/) , you can automatically parse it to a POJO.

Quarkus provides a placeholder ( `\{response_schema\}` ) that can be included in your prompt. It will dynamically be replaced by the defined schema of the method's return object, making the whole process easy. If you don't include it in your system and user messages, Quarkus automatically appends it after your prompt to enforce the format. Learn more [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/ai-services.html#_ai_method_return_type) [.](https://docs.langchain4j.dev/tutorials/structured-outputs/)

```java
@RegisterAiService
public interface FormHelper {

    @SystemMessage(" You're an helpful bot that should fill an object based on the user message")
    @UserMessage("""
            Fill the the provided object based on the information given by the user.
            You should only update the field for which you have information.
            A field that is null must be filled by the user.
            
            \{userMessage}
            \{adoptionForm}
            """)
    AdoptionForm fillAdoptionForm(@MemoryId long id, String userMessage, AdoptionForm adoptionForm);
}
```

You can use the `@Description` annotation to give a description of each field in your POJO to aid in the data mapping even further.

```java
@Entity
public class AdoptionForm extends PanacheEntity {

    @NotNull
    @NotEmpty
    @Description("the firstname of the person willing to adopt")
    public String firstName;
    //...
}
```

One key issue you might encounter, depending on the model you're using is that not all the LLMs are the same when it comes to structured output. The LLM may not follow your instructions even with Quarkus' assistance; in this scenario, you may need to improve your prompt. Because of this, I have found that using few-shot prompting, which involves displaying both positive and negative output, is a very successful strategy.

### Memory

We should not only use the current user message and the current form state, but also the last few messages using the memory. If you are wondering, imagine the following exchange:

Bot: "When would you like to go pick up your puppy?"

User: "I would probably come the 4th of July."

Bot: "Sorry, but we're closed on this date. Could you provide another date?"

User: "Oh, of course, let's say the 6 then."

The LLM would not be able to understand that when the user says the 6, he means the 6th of July if we don't use the memory.

## Validation

You should now verify the content of your POJO after enriching it. One crucial factor to consider is that the validation's output should be readable by an LLM, which should then be able to provide feedback to the user. In my situation, I decided to verify the information in my POJO using [bean validation](https://quarkus.io/guides/validation) . Writing validation and producing a uniform, structured validation output was a simple solution. If you wish to be even more helpful, you can even include a detailed error message.

```java
@Entity
public class AdoptionForm extends PanacheEntity {

    @NotNull
    @NotEmpty
    @Description("the firstname of the person willing to adopt")
    public String firstName;
    @NotNull
    @NotEmpty
    public String lastName;
    @NotNull
    @NotEmpty
    @Email
    public String email;
    @NotNull
    @NotEmpty
    @Pattern(regexp = "^\\+(?:[0-9] ?)\{6,14}[0-9]$")
    public String phone;
    //...
}
```



```java
@Inject
Validator validator;

public Set<ConstraintViolation<AdoptionForm>> validateForm(AdoptionForm adoptionForm) {
        return validator.validate(adoptionForm);
}
```

Based on this output, you can ask the LLM to provide some feedback to the user on what data is missing or invalid. You can even enhance this feedback with your own knowledge using RAG.

```java
@SystemMessage("""
            You're an helpful and polite bot who try to help user fill a form.
            Your response must be polite, use the same language as the question.
            """)
@UserMessage("""
            You are to assist the user with fixing validation issues in the adoption form for a puppy.
            Address only one issue at a time.
            Respond directly to the user's queries or comments.
            
            ---
            Validation Issues: \{validationIssues}
            User Message: \{userMessage}
            """)
String helpSolveIssues(@MemoryId long id, Set<ConstraintViolation<AdoptionForm>> validationIssues, String userMessage);
```

*Example of AI service generating feedback for the user based on the validation errors*

## Orchestration

All you need to do now is put everything together so you can help a user fill out the appropriate information. You can achieve this in several ways. Probably the easiest method is to use some basic code to orchestrate everything and imperatively chain all the stages. You have a lot of control, but you must handle every situation by hand, and if your form is big and complex, it can get complicated.

```java
@Inject
 FormHelper formHelper;

public ChatMessage<AdoptionForm> helpAdoptAPuppy(ChatMessage<AdoptionForm> chatMessage) {
        var userMessage = chatMessage.message();
        var filledAdoptionForm = formHelper.fillAdoptionForm(userId, userMessage, chatMessage.form());
        var validationIssues = validateForm(filledAdoptionForm);
        if (validationIssues.isEmpty()) {
            AdoptionForm.persist(filledAdoptionForm);
            var completionMessage = formHelper.confirmValidForm(userId, userMessage);
            return new ChatMessage<>(completionMessage, filledAdoptionForm);
        }
        var guidanceMessage = formHelper.helpSolveIssues(userId, validationIssues, userMessage);
        return new ChatMessage<>(guidanceMessage, filledAdoptionForm);
}
```

*Example of simple orchestration using imperative code*

To handle every scenario and give the user a more granular experience, you might also employ a workflow or rule engine. You can have a great deal of flexibility with this option.

The final choice is a more agentic approach; you could just design a new AI service and specify the fundamental steps (fill the POJO, validate the data, and provide the user with feedback). Here, you give the LLM the tools - the filling and validation mechanisms - and let it handle the rest.

## And what about RAG?

We haven't really discussed RAG up to this point, but it may be helpful when filling out forms. You may request certain information, but the user is unsure of where to look for it. By incorporating RAG into your chat, the LLM may now give the user useful information to fill in specific information. Allowing the user to not only fill in the form but also give them some confidence in what they are filling in.

If you're using the `quarkus-langchain4j-easy-rag` extension, integrating the RAG is effortless. By default, Quarkus generates, discovers, and provides the retrieval augmentor to your AI service. If you don't use this extension, the process is quite easy. You need to define a retrieval augmentor like the one shown below.

```java
@ApplicationScoped
public class RetrievalAugmentorExample implements Supplier<RetrievalAugmentor> {

    private final RetrievalAugmentor augmentor;

    RetrievalAugmentorExample(PgVectorEmbeddingStore store, EmbeddingModel model) {
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(model)
                .embeddingStore(store)
                .maxResults(3)
                .build();
        augmentor = DefaultRetrievalAugmentor
                .builder()
                .contentRetriever(contentRetriever)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {
        return augmentor;
    }

}
```

Then provide it to your AI service so it can use it when exchanging with the LLM.

```java
@RegisterAiService(
        tools = PuppiesService.class,
        retrievalAugmentor = RetrievalAugmentorExample.class
)
public interface Bot {
    //...
}
```

You could even go one step further and provide users access to a chat feature throughout your app, which would make it easier for them to navigate your website. We've all been in the position where we can't find the form or page we're looking for on a website. How helpful would it be to receive guidance from an assistant?

## Going even further?

Having an assistant who can help me navigate the process of filling out a form has already made the user experience much better. However, is there any way we could make a user's job even easier? We could, of course, why don't we allow the user to upload files? The user experience might be improved even further. We could allow the user to provide us information in the form of a word, PDF, image, or even audio that we might employ. To do so, we need a [multimodal model](https://docs.langchain4j.dev/integrations/language-models/) . After that, we have two choices.

- The content of the attachment can be extracted in the proper format by calling an LLM in the case of sound and images, or by using a library like Apache Tika for documents. After which, provide the user message together with the content for the remaining steps.

- Another option is to just provide the LLM the file containing our call and let it take care of it.

By doing so, we can automatically fill the form without the user having to do anything.

When using this approach, we still need to be careful about the potential risk of using an unknown file. We also need to pay attention to the context window size of our model, since the user could provide us with a very large file, and it would overflow the size of the context window.

# Wrap up

Transforming traditional forms into a conversational experience revolutionizes user interfaces. Using AI tools like LangChain4j and Quarkus, developers can build dynamic assistants that simplify data entry. These assistants guide users step-by-step and validate input in real time. They can also provide more information on how to fill a form and where to find the information by harnessing the strength of RAG. Multimodal capabilities further enhance user confidence and ease by allowing them to let an LLM find the right information simply. This approach makes the user experience richer and helps improve the data quality by providing 24/7 support to the user. The AI-powered form represents a shift towards more intuitive digital experiences. It paves the way for advanced AI integration in everyday applications.
