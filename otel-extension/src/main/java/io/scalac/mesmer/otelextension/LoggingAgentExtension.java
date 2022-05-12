package io.scalac.mesmer.otelextension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.AgentExtension;
import java.io.File;
import java.io.IOException;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

@AutoService(AgentExtension.class)
public class LoggingAgentExtension implements AgentExtension {

  @Override
  public AgentBuilder extend(AgentBuilder agentBuilder) {
    return agentBuilder
        .with(AgentBuilder.Listener.StreamWriting.toSystemOut().withErrorsOnly())
        .with(new FileSavingListener());
  }

  @Override
  public String extensionName() {
    return "error-printing";
  }

  public static class FileSavingListener implements AgentBuilder.Listener {
    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}

    @Override
    public void onTransformation(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        DynamicType dynamicType) {
      try {
        dynamicType.saveIn(new File("/Users/piotrjosiak/scalac/mesmer-akka-agent/agentclasses"));
      } catch (IOException ignored) {

      }
    }

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded) {}

    @Override
    public void onError(
        String typeName,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        Throwable throwable) {}

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
  }
}
