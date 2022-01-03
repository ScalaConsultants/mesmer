package io.scalac.mesmer.agent.akka.http;

import akka.actor.ExtendedActorSystem;
import akka.http.scaladsl.HttpExt;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.scaladsl.Flow;
import com.typesafe.config.Config;
import io.scalac.mesmer.core.model.Version;
import io.scalac.mesmer.core.util.LibraryInfo;
import net.bytebuddy.asm.Advice;
import scala.collection.immutable.Map;

public class HttpExtRequestsAdvice {
    @Advice.OnMethodEnter
    public static void bindAndHandle(@Advice.Argument(value = 0, readOnly = false) Flow<HttpRequest, HttpResponse, Object> handler,
                                     @Advice.This Object self) {


//        val system: ActorSystem[Nothing] = self.asInstanceOf[HttpExt].system.toTyped;

        HttpExt ext = (HttpExt) self;

        ExtendedActorSystem system = ext.system();


        // TODO (LEARNING): We technically could read the jars info here too but this seems expensive.
        Map<String, Version> libraryInfo = LibraryInfo.extractModulesInformation(Thread.currentThread().getContextClassLoader());

        // TODO (LEARNING): we also have access to config here in the Advice method. So we could do all the configuration here.
        //  (also seems expensive).
        Config config = system.settings().config();


        handler = HttpInstrumentation.bindAndHandleRequestImpl(handler, (HttpExt) self);
    }
}
