package io.scalac.agent;

import akka.actor.typed.scaladsl.ActorContext;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

public class RecoveryStartedInterceptor {

	@Advice.OnMethodEnter
	public static void enter(@Advice.Origin Method method, @Advice.AllArguments Object[] parameters, @Advice.This Object thiz) {
		System.out.println("Recovery startup intercepted. Method: " + method + ", This: " + thiz);
		ActorContext <?> a = (ActorContext<?>) parameters[0];
		String p = a.self().path().toStringWithoutAddress();
		System.out.println("Recovery startup actor path: " + p);
		AkkaPersistenceAgentState.recoveryStarted.put(p, System.currentTimeMillis());
	}
}
