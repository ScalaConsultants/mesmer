//package io.scalac.mesmer.otelextension;
//
//import com.google.auto.service.AutoService;
//import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
//import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
//import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent;
//import java.util.List;
//
//@AutoService(InstrumentationModule.class)
//public class MesmerAkkaPersistenceInstrumentationModule extends InstrumentationModule {
//  public MesmerAkkaPersistenceInstrumentationModule() {
//    super("mesmer-akka-persistence");
//  }
//
//  @Override
//  public List<TypeInstrumentation> typeInstrumentations() {
//    return AkkaPersistenceAgent.agent().asOtelTypeInstrumentations();
//  }
//}
