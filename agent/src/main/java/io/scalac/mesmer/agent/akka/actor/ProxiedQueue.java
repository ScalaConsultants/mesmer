package io.scalac.mesmer.agent.akka.actor;

import akka.dispatch.Envelope;

import java.util.concurrent.BlockingQueue;

import static net.bytebuddy.asm.Advice.*;

public class ProxiedQueue {
    public final static String queueFieldName = "_proxiedQueue";

    @OnMethodExit
    public static void constructor(
            @FieldValue(value = queueFieldName, readOnly = false) BlockingQueue<Envelope> queue,
            @This  BlockingQueue<Envelope> self
    ) {
        queue = new BoundedQueueProxy<>(self);
    }

}
