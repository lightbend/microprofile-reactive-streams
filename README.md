# Lightbend implementations of MicroProfile Reactive Streams

This repo contains implementations by Lightbend of [MicroProfile Reactive Streams Operators Support](https://github.com/eclipse/microprofile-reactive-streams-operators).

Two implementations are provided, one based on Akka, and one zero dependency implementation.

To use the Akka implementation:

```xml
<dependency>
    <groupId>com.lightbend.microprofile.reactive.streams</groupId>
    <artifactId>lightbend-microprofile-reactive-streams-akka</artifactId>
    <version>1.0.0</version>
</dependency>
```

To use the zero dependency implementation:

```xml
<dependency>
    <groupId>com.lightbend.microprofile.reactive.streams</groupId>
    <artifactId>lightbend-microprofile-reactive-streams-zerodep</artifactId>
    <version>1.0.0</version>
</dependency>
```
