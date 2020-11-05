package io.scalac.agent;

import akka.actor.typed.scaladsl.ActorContext;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

public class RecoveryCompletedInteceptor {

	@Advice.OnMethodEnter
	public static void enter(@Advice.Origin Method method, @Advice.AllArguments Object[] parameters, @Advice.This Object thiz) {
		System.out.println("Recovery completion intercepted. Method: " + method + ", This: " + thiz);
		ActorContext <?> a = (ActorContext<?>) parameters[0];
		String p = a.self().path().toStringWithoutAddress();
		long measured = System.currentTimeMillis() - AkkaPersistenceAgentState.recoveryStarted.get(p);
		System.out.println("Recovery took " + measured + "ms for actor " + p);
		AkkaPersistenceAgentState.recoveryMeasurements.put(p, measured);
	}
}
