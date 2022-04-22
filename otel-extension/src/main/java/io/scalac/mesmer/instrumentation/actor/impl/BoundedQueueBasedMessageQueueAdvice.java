package io.scalac.mesmer.instrumentation.actor.impl;


import akka.dispatch.BoundedQueueBasedMessageQueue;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import net.bytebuddy.asm.Advice;

import java.util.concurrent.BlockingQueue;

public class BoundedQueueBasedMessageQueueAdvice {

    @Advice.OnMethodExit
    public static void queue(@Advice.Return(readOnly = false) BlockingQueue<?> result, @Advice.This Object self) {
        result = VirtualField.find(BoundedQueueBasedMessageQueue.class, BlockingQueue.class)
                .get((BoundedQueueBasedMessageQueue) self);
    }

}
