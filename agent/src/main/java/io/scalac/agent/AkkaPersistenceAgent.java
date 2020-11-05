package io.scalac.agent;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class AkkaPersistenceAgent {
    public static void run() {

        Instrumentation instrumentation = ByteBuddyAgent.install();

        ElementMatcher.Junction<MethodDescription> onRecoveryCompleteDescription = isMethod().and(named("onRecoveryComplete")).and(not(isAbstract()));
        ElementMatcher.Junction<MethodDescription> onRecoveryStartDescription = isMethod().and(named("onRecoveryStart")).and(not(isAbstract()));


        new AgentBuilder.Default()
                .with(new ByteBuddy().with(TypeValidation.DISABLED))
                .with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly())
                .with(AgentBuilder.InstallationListener.StreamWriting.toSystemOut())
                .type(nameContains("ReplayingSnapshot"))
                .transform((builder, type, classLoader, module) ->
                        builder.method(onRecoveryStartDescription).intercept(Advice.to(RecoveryStartedInterceptor.class))
                )
                .installOn(instrumentation);

        new AgentBuilder.Default()
                .with(new ByteBuddy().with(TypeValidation.DISABLED))
                .with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly())
                .with(AgentBuilder.InstallationListener.StreamWriting.toSystemOut())
                .type(nameContains("ReplayingEvents"))
                .transform((builder, type, classLoader, module) ->
                        builder.method(onRecoveryCompleteDescription).intercept(Advice.to(RecoveryCompletedInteceptor.class))
                )
                .installOn(instrumentation);


    }

}
